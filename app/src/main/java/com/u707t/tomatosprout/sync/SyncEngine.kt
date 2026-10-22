package com.u707t.tomatosprout.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import com.u707t.tomatosprout.data.db.SyncLogEntity
import com.u707t.tomatosprout.data.db.SyncManifestEntity
import com.u707t.tomatosprout.data.db.SyncDao
import com.u707t.tomatosprout.data.db.toDomain
import com.u707t.tomatosprout.data.db.toEntity
import com.u707t.tomatosprout.domain.AppClock
import com.u707t.tomatosprout.domain.AppSettings
import com.u707t.tomatosprout.domain.BadgeRepository
import com.u707t.tomatosprout.domain.CheckInRepository
import com.u707t.tomatosprout.domain.FocusRepository
import com.u707t.tomatosprout.domain.MergeReport
import com.u707t.tomatosprout.domain.ReminderRepository
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.domain.TaskBundle
import com.u707t.tomatosprout.domain.TaskRepository
import com.u707t.tomatosprout.domain.WishRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnapshotFactory @Inject constructor(
    private val tasks: TaskRepository,
    private val checkIns: CheckInRepository,
    private val focus: FocusRepository,
    private val badges: BadgeRepository,
    private val reminders: ReminderRepository,
    private val wishes: WishRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    suspend fun create(): SyncSnapshot {
        val config = settings.current()
        val bundle = tasks.captureAll()
        return SyncSnapshot(
            schemaVersion = 2,
            deviceId = config.deviceId,
            exportedAt = clock.nowMillis(),
            tasks = bundle.tasks.map { it.toEntity() },
            subTasks = bundle.subTasks.map { it.toEntity() },
            habitRules = bundle.habitRules.map { it.toEntity() },
            goalRules = bundle.goalRules.map { it.toEntity() },
            checkIns = checkIns.all().map { it.toEntity() },
            focusSessions = focus.all().map { it.toEntity() },
            reminders = reminders.all().map { it.toEntity() },
            badges = badges.all().map { it.toEntity() },
            wishes = wishes.all().map { it.toEntity() },
            settings = config.toDto(),
        )
    }
}

fun AppSettings.toDto() = SettingsDto(
    themeMode = themeMode.name,
    simpleMode = simpleMode,
    notificationsEnabled = notificationsEnabled,
    dndEnabled = dndEnabled,
    dndStart = dndStart,
    dndEnd = dndEnd,
    defaultFocusMinutes = defaultFocusMinutes,
    defaultBreakMinutes = defaultBreakMinutes,
    ignoreShortFocus = ignoreShortFocus,
    strictMode = strictMode,
    countUpMode = countUpMode,
    midnightShift = midnightShift,
)

@Singleton
class SyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: WebDavClient,
    private val tasks: TaskRepository,
    private val checkIns: CheckInRepository,
    private val focus: FocusRepository,
    private val badges: BadgeRepository,
    private val reminders: ReminderRepository,
    private val wishes: WishRepository,
    private val settings: SettingsRepository,
    private val syncDao: SyncDao,
    private val snapshots: SnapshotFactory,
    private val clock: AppClock,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    private suspend fun config(): WebDavConfig? {
        val s = settings.current()
        if (s.webDavServer.isBlank()) return null
        val password = settings.webDavPassword().orEmpty()
        return WebDavConfig(s.webDavServer, s.webDavUsername, password, s.webDavPath)
    }

    suspend fun testConnection(): Result<Unit> {
        val config = config() ?: return Result.failure(IllegalStateException("请先填写服务器地址"))
        return client.testConnection(config)
    }

    suspend fun sync(): SyncResult {
        val local = settings.current()
        val config = config() ?: return SyncResult(false, "尚未配置 WebDAV")
        if (local.wifiOnly && !onWifi()) {
            return SyncResult(false, "已开启仅 Wi-Fi 同步，请连接 Wi-Fi 后重试")
        }
        val started = clock.nowMillis()
        val lastSync = local.lastSyncedAt ?: 0L
        var totalItems = 0
        var totalConflicts = 0
        return try {
            client.ensureDirectory(config)
            val root = client.rootUrl(config)
            val files = client.list(config, root)
                .filter { !it.isDirectory && it.name.endsWith(".json") && it.name.startsWith("tomatosprout-") }
            val manifests = syncDao.manifests().associateBy { it.fileName }
            var latestSettings: SettingsDto? = null
            var latestExportedAt = 0L

            files.filter { it.name != ownFileName(local.deviceId) }.forEach { file ->
                val previous = manifests[file.name]
                // ETag 没变说明远端文件没动过：连下载都省掉（大文件同步的主要耗时在这里）
                if (previous?.remoteEtag != null && file.etag != null && previous.remoteEtag == file.etag) {
                    return@forEach
                }
                val bytes = client.get(config, client.fileUrl(config, file.name))
                val snapshot = runCatching {
                    json.decodeFromString<SyncSnapshot>(bytes.toString(Charsets.UTF_8))
                }.getOrElse { throw IllegalStateException("远端文件 ${file.name} 格式不正确，已停止以避免数据丢失") }
                if (snapshot.schemaVersion > 2) throw IllegalStateException("远端备份版本过新，请升级 App")
                // 语义哈希：忽略每次都会变化的 exportedAt，数据没变就不重复合并
                val hash = semanticHash(snapshot)
                if (previous?.contentHash == hash) return@forEach
                snapshot.settings?.let { remoteSettings ->
                    if (snapshot.exportedAt > latestExportedAt) {
                        latestExportedAt = snapshot.exportedAt
                        latestSettings = remoteSettings
                    }
                }
                val report = merge(snapshot, lastSync, started)
                totalItems += report.inserted + report.updated
                totalConflicts += report.conflicts
                syncDao.upsertManifest(
                    SyncManifestEntity(
                        deviceId = local.deviceId,
                        fileName = file.name,
                        contentHash = hash,
                        remoteEtag = file.etag,
                        lastSyncedAt = started,
                    ),
                )
            }

            // 设置也参与同步：仅采用下载文件中较新的一份，凭据类字段始终留在本机
            if (latestSettings != null && latestExportedAt > lastSync) {
                applySettings(latestSettings!!)
            }

            // 合并后按专注记录重算番茄账本，避免各设备 +1 后互相覆盖造成漂移
            tasks.recountCompletedPomos()

            // 合并后把本地快照推回自己的文件
            val snapshot = snapshots.create()
            val ownName = ownFileName(local.deviceId)
            val ownHash = semanticHash(snapshot)
            val previousOwn = syncDao.manifest(local.deviceId, ownName)
            val etag = if (previousOwn?.contentHash == ownHash && previousOwn.remoteEtag != null) {
                previousOwn.remoteEtag
            } else {
                val bytes = json.encodeToString(SyncSnapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8)
                client.put(config, client.fileUrl(config, ownName), bytes)
            }
            syncDao.upsertManifest(
                SyncManifestEntity(
                    deviceId = local.deviceId,
                    fileName = ownName,
                    contentHash = ownHash,
                    remoteEtag = etag,
                    lastSyncedAt = started,
                ),
            )
            settings.update { it.copy(lastSyncedAt = started) }
            syncDao.log(
                SyncLogEntity(
                    id = UUID.randomUUID().toString(),
                    at = started,
                    direction = "sync",
                    items = totalItems,
                    conflicts = totalConflicts,
                    error = false,
                    message = "同步完成：合并 $totalItems 条，冲突 $totalConflicts 条",
                ),
            )
            SyncResult(true, "同步完成：合并 $totalItems 条，冲突 $totalConflicts 条", totalItems, totalConflicts)
        } catch (e: WebDavException) {
            val message = e.message
            syncDao.log(errorLog(started, message))
            SyncResult(false, message, retryable = e.code == 0 || e.code >= 500)
        } catch (e: Exception) {
            val message = e.message ?: "同步失败"
            syncDao.log(errorLog(started, message))
            SyncResult(false, message, retryable = true)
        }
    }

    private suspend fun merge(remote: SyncSnapshot, lastSync: Long, now: Long): MergeReport {
        var report = MergeReport()
        val localTasks = tasks.captureAll().tasks.associateBy { it.id }

        remote.tasks.forEach { r ->
            val localTask = localTasks[r.id]
            if (localTask == null) {
                tasks.restoreAll(TaskBundle(tasks = listOf(r.toDomain())), overwrite = false)
                report += MergeReport(inserted = 1)
                return@forEach
            }
            if (localTask.toEntity() == r) return@forEach
            // 设备时钟可能有几分钟偏差。用容差把“刚同步完又被改”的记录判成变化，
            // 宁可多生成冲突副本，也不要因为时钟慢几秒就静默丢掉一端的数据。
            val changedSince = if (lastSync == 0L) 0L else (lastSync - SYNC_CLOCK_SKEW_MS).coerceAtLeast(0L)
            val remoteChanged = lastSync == 0L || r.updatedAt > changedSince
            val localChanged = lastSync == 0L || localTask.updatedAt > changedSince
            when {
                remoteChanged && !localChanged -> {
                    // 本地自上次同步后没动过 → 接受远端
                    tasks.upsert(r.toDomain())
                    report += MergeReport(updated = 1)
                }
                remoteChanged && localChanged -> {
                    // 双方都改过：保留本地版本，远端版本另存为冲突副本
                    if (r.deletedAt == null) {
                        val copyId = UUID.randomUUID().toString()
                        val copy = r.copy(
                            id = copyId,
                            title = r.title + "（冲突副本）",
                            parentId = null,
                            createdAt = now,
                            updatedAt = now,
                        )
                        tasks.upsert(copy.toDomain())
                        // 冲突副本必须带上自己的规则，否则会变成一条没有习惯/目标/提醒的裸任务
                        remote.habitRules.firstOrNull { it.taskId == r.id }?.let {
                            tasks.upsertHabitRule(it.copy(taskId = copyId).toDomain())
                        }
                        remote.goalRules.firstOrNull { it.taskId == r.id }?.let {
                            tasks.upsertGoalRule(it.copy(taskId = copyId).toDomain())
                        }
                        remote.reminders.firstOrNull { it.taskId == r.id }?.let {
                            reminders.upsert(
                                it.copy(id = UUID.randomUUID().toString(), taskId = copyId).toDomain(),
                            )
                        }
                        val childCopies = remote.subTasks
                            .filter { it.parentTaskId == r.id }
                            .map { it.copy(id = UUID.randomUUID().toString(), parentTaskId = copyId) }
                        if (childCopies.isNotEmpty()) {
                            tasks.restoreAll(
                                TaskBundle(subTasks = childCopies.map { it.toDomain() }),
                                overwrite = false,
                            )
                        }
                    }
                    report += MergeReport(conflicts = 1)
                }
                else -> report += MergeReport(skipped = 1)
            }
        }

        // 规则跟随任务。本地有未同步修改的任务时不覆盖其规则，避免一次同步把本地编辑吞掉。
        val localAfter = tasks.captureAll().tasks.associateBy { it.id }
        val protectedIds = localAfter.values.filter { lastSync > 0 && it.updatedAt > lastSync }.map { it.id }.toSet()
        val validTaskIds = localAfter.keys
        tasks.restoreAll(
            TaskBundle(subTasks = remote.subTasks.filter { it.parentTaskId in validTaskIds }.map { it.toDomain() }),
            overwrite = false,
        )
        remote.habitRules.filter { it.taskId in validTaskIds && it.taskId !in protectedIds }
            .forEach { tasks.upsertHabitRule(it.toDomain()) }
        remote.goalRules.filter { it.taskId in localAfter.keys && it.taskId !in protectedIds }
            .forEach { tasks.upsertGoalRule(it.toDomain()) }
        report += checkIns.restoreAll(remote.checkIns.map { it.toDomain() })
        report += focus.restoreAll(remote.focusSessions.map { it.toDomain() })
        report += reminders.restoreAll(remote.reminders.map { it.toDomain() })
        report += wishes.restoreAll(remote.wishes.map { it.toDomain() })
        report += badges.restoreAll(remote.badges.map { it.toDomain() })
        remote.badges.filter { it.unlockedAt != null }.forEach { badge ->
            badges.unlock(setOf(badge.badgeKey), badge.unlockedAt ?: now)
        }
        return report
    }

    private suspend fun applySettings(dto: SettingsDto) {
        settings.update { current ->
            current.copy(
                themeMode = runCatching { com.u707t.tomatosprout.domain.ThemeMode.valueOf(dto.themeMode) }
                    .getOrDefault(current.themeMode),
                simpleMode = dto.simpleMode,
                notificationsEnabled = dto.notificationsEnabled,
                dndEnabled = dto.dndEnabled,
                dndStart = dto.dndStart,
                dndEnd = dto.dndEnd,
                defaultFocusMinutes = dto.defaultFocusMinutes.coerceIn(5, 180),
                defaultBreakMinutes = dto.defaultBreakMinutes.coerceIn(5, 30),
                ignoreShortFocus = dto.ignoreShortFocus,
                strictMode = dto.strictMode,
                countUpMode = dto.countUpMode,
                midnightShift = dto.midnightShift,
            )
        }
    }

    private fun semanticHash(snapshot: SyncSnapshot): String {
        val canonical = json.encodeToString(SyncSnapshot.serializer(), snapshot.copy(exportedAt = 0L))
        return Hashing.sha256(canonical.toByteArray(Charsets.UTF_8))
    }

    private fun ownFileName(deviceId: String) = "tomatosprout-${deviceId.take(24)}.json"

    private fun onWifi(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun errorLog(at: Long, message: String) = SyncLogEntity(
        id = UUID.randomUUID().toString(),
        at = at,
        direction = "error",
        error = true,
        message = message.take(300),
    )

    private companion object {
        /** 允许的设备时钟偏差；超出这个窗口的修改仍然按 updatedAt 正常比较。 */
        const val SYNC_CLOCK_SKEW_MS = 5 * 60_000L
    }
}

@HiltWorker
class SyncRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (runAttemptCount >= 3) return Result.failure()
        val result = engine.sync()
        return when {
            result.success -> Result.success()
            result.retryable -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val NAME = "webdav-sync-retry"

        fun enqueue(context: Context, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncRetryWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
