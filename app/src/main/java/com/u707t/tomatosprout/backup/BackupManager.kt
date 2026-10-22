package com.u707t.tomatosprout.backup

import com.u707t.tomatosprout.data.db.AppDatabase
import com.u707t.tomatosprout.data.db.toDomain
import com.u707t.tomatosprout.data.db.toEntity
import com.u707t.tomatosprout.domain.AppClock
import com.u707t.tomatosprout.domain.BadgeRefresher
import com.u707t.tomatosprout.domain.BadgeRepository
import com.u707t.tomatosprout.domain.CheckInRepository
import com.u707t.tomatosprout.domain.FocusRepository
import com.u707t.tomatosprout.domain.ReminderRepository
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.domain.SubTaskRepository
import com.u707t.tomatosprout.domain.TaskBundle
import com.u707t.tomatosprout.domain.TaskRepository
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.domain.ThemeMode
import com.u707t.tomatosprout.domain.WishRepository
import com.u707t.tomatosprout.sync.SnapshotFactory
import com.u707t.tomatosprout.sync.SyncSnapshot
import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(
    val ok: Boolean,
    val imported: Int = 0,
    val conflicts: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
)

@Singleton
class BackupManager @Inject constructor(
    private val snapshots: SnapshotFactory,
    private val tasks: TaskRepository,
    private val subTasks: SubTaskRepository,
    private val checkIns: CheckInRepository,
    private val focus: FocusRepository,
    private val badges: BadgeRepository,
    private val badgeRefresher: BadgeRefresher,
    private val reminders: ReminderRepository,
    private val wishes: WishRepository,
    private val settings: SettingsRepository,
    private val db: AppDatabase,
    private val clock: AppClock,
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun exportJson(): String = json.encodeToString(SyncSnapshot.serializer(), snapshots.create())

    suspend fun exportCsv(): String {
        val titles = tasks.captureAll().tasks.associate { it.id to it.title }
        val sb = StringBuilder()
        sb.append("date,task,count,makeup,source,note,created_at\n")
        checkIns.all().filter { it.deletedAt == null }.sortedBy { it.date }.forEach { c ->
            sb.append(csv(c.date.toString())).append(',')
                .append(csv(titles[c.taskId] ?: "已删除任务")).append(',')
                .append(c.count).append(',')
                .append(c.isMakeup).append(',')
                .append(csv(c.source)).append(',')
                .append(csv(c.note.orEmpty())).append(',')
                .append(c.createdAt).append('\n')
        }
        return sb.toString()
    }

    suspend fun importJson(text: String): ImportResult {
        val snapshot = runCatching {
            json.decodeFromString<SyncSnapshot>(text)
        }.getOrElse { return ImportResult(false, error = "文件不是有效的番茄芽备份：${it.message?.take(80)}") }

        if (snapshot.schemaVersion > 6) return ImportResult(false, error = "备份版本较新，请升级 App 后再导入")
        val invalid = validate(snapshot)
        if (invalid != null) return ImportResult(false, error = invalid)

        var imported = 0
        var conflicts = 0
        var skipped = 0

        // 导入是一个整体：任务、规则、打卡、专注、徽章要么全部写入，要么全部回滚。
        db.withTransaction {
            val localTasks = tasks.captureAll().tasks.associateBy { it.id }
            // 新任务 + 与本地完全一致的任务：它们的规则/子任务可以安全补入；冲突任务保留本地
            val acceptedTaskIds = mutableSetOf<String>()

            snapshot.tasks.forEach { remote ->
                val local = localTasks[remote.id]
                when {
                    local == null -> {
                        tasks.restoreAll(TaskBundle(tasks = listOf(remote.toDomain())), overwrite = false)
                        acceptedTaskIds += remote.id
                        imported++
                    }
                    local.toEntity() == remote -> {
                        acceptedTaskIds += remote.id
                        skipped++
                    }
                    else -> conflicts++ // 保留本地，冲突只提示不覆盖
                }
            }
            val subReport = subTasks.mergeAll(
                snapshot.subTasks.filter { it.parentTaskId in acceptedTaskIds }.map { it.toDomain() },
            )
            imported += subReport.inserted + subReport.updated
            skipped += subReport.skipped
            val remoteTaskById = snapshot.tasks.associateBy { it.id }
            val habitReport = tasks.mergeHabitRules(
                snapshot.habitRules
                    .filter { it.taskId in acceptedTaskIds && remoteTaskById[it.taskId]?.type == TaskType.HABIT.name }
                    .map { it.toDomain() },
            )
            val goalReport = tasks.mergeGoalRules(
                snapshot.goalRules
                    .filter { it.taskId in acceptedTaskIds && remoteTaskById[it.taskId]?.type == TaskType.GOAL.name }
                    .map { it.toDomain() },
            )
            imported += habitReport.inserted + habitReport.updated + goalReport.inserted + goalReport.updated
            skipped += habitReport.skipped + goalReport.skipped
            val checkReport = checkIns.mergeAll(snapshot.checkIns.map { it.toDomain() })
            val focusReport = focus.mergeAll(snapshot.focusSessions.map { it.toDomain() })
            // 导入的历史专注会改变番茄账本，按记录重算一次
            tasks.recountCompletedPomos()
            val badgeReport = badges.restoreAll(snapshot.badges.map { it.toDomain() })
            val wishReport = wishes.mergeAll(snapshot.wishes.map { it.toDomain() })
            val reminderReport = reminders.mergeAll(snapshot.reminders.map { it.toDomain() })
            imported += checkReport.inserted + checkReport.updated +
                focusReport.inserted + focusReport.updated +
                badgeReport.inserted +
                wishReport.inserted + wishReport.updated +
                reminderReport.inserted + reminderReport.updated
            skipped += checkReport.skipped + focusReport.skipped +
                badgeReport.skipped + wishReport.skipped + reminderReport.skipped
        }

        // 导入的远端数据也可能解锁徽章；DataStore 设置不在 Room 事务里
        runCatching { badgeRefresher.refresh() }

        snapshot.settings?.let { s ->
            settings.update {
                it.copy(
                    themeMode = runCatching { ThemeMode.valueOf(s.themeMode) }.getOrDefault(it.themeMode),
                    simpleMode = s.simpleMode,
                    notificationsEnabled = s.notificationsEnabled,
                    dndEnabled = s.dndEnabled,
                    dndStart = s.dndStart,
                    dndEnd = s.dndEnd,
                    defaultFocusMinutes = s.defaultFocusMinutes.coerceIn(5, 180),
                    defaultBreakMinutes = s.defaultBreakMinutes.coerceIn(5, 30),
                    longBreakMinutes = s.longBreakMinutes.coerceIn(10, 60),
                    ignoreShortFocus = s.ignoreShortFocus,
                    strictMode = s.strictMode,
                    countUpMode = s.countUpMode,
                    midnightShift = s.midnightShift,
                )
            }
        }
        return ImportResult(true, imported, conflicts, skipped)
    }

    private fun validate(snapshot: SyncSnapshot): String? {
        if (snapshot.tasks.any { it.id.isBlank() || it.title.isBlank() }) return "存在缺少 id 或标题的任务"
        if (snapshot.tasks.map { it.id }.distinct().size != snapshot.tasks.size) return "任务 id 重复"
        runCatching { snapshot.habitRules.forEach { LocalDate.parse(it.startDate) } }
            .onFailure { return "习惯规则的日期格式错误" }
        runCatching {
            snapshot.goalRules.forEach {
                LocalDate.parse(it.startDate)
                LocalDate.parse(it.endDate)
            }
        }.onFailure { return "目标规则的日期格式错误" }
        runCatching { snapshot.reminders.forEach { LocalTime.parse(it.timeOfDay) } }
            .onFailure { return "提醒时间格式错误" }
        runCatching { snapshot.checkIns.forEach { LocalDate.parse(it.checkDate) } }
            .onFailure { return "打卡记录的日期格式错误" }
        if (snapshot.checkIns.any { it.id.isBlank() || it.taskId.isBlank() }) return "打卡记录缺少 id"
        if (snapshot.checkIns.any { it.count < 0 }) return "打卡次数不合法"
        if (snapshot.checkIns.groupBy { Triple(it.taskId, it.checkDate, it.isMakeup) }.any { it.value.size > 1 }) {
            return "打卡记录存在重复的任务日期"
        }
        if (snapshot.habitRules.any { it.targetCountPerPeriod < 0 }) return "习惯目标次数不合法"
        if (snapshot.goalRules.any { (it.targetTotalCount ?: 0) < 0 || (it.targetTotalSeconds ?: 0) < 0 }) {
            return "目标数值不合法"
        }
        if (snapshot.focusSessions.any { it.actualSeconds < 0 || it.plannedSeconds < 0 }) return "专注时长不合法"
        if (snapshot.subTasks.any { it.id.isBlank() || it.parentTaskId.isBlank() || it.title.isBlank() }) {
            return "子任务缺少 id、父任务或标题"
        }
        return null
    }

    private fun csv(value: String): String {
        // 防止 Excel 公式注入：用户数据以 = + - @ 开头时前置单引号
        val safe = if (value.isNotEmpty() && value[0] in "=+-@") "'$value" else value
        return if (safe.contains(',') || safe.contains('"') || safe.contains('\n')) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else safe
    }
}
