package com.u707t.tomatosprout.backup

import com.u707t.tomatosprout.data.db.toDomain
import com.u707t.tomatosprout.data.db.toEntity
import com.u707t.tomatosprout.domain.AppClock
import com.u707t.tomatosprout.domain.BadgeRepository
import com.u707t.tomatosprout.domain.CheckInRepository
import com.u707t.tomatosprout.domain.FocusRepository
import com.u707t.tomatosprout.domain.ReminderRepository
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.domain.TaskBundle
import com.u707t.tomatosprout.domain.TaskRepository
import com.u707t.tomatosprout.domain.ThemeMode
import com.u707t.tomatosprout.domain.WishRepository
import com.u707t.tomatosprout.sync.SnapshotFactory
import com.u707t.tomatosprout.sync.SyncSnapshot
import kotlinx.serialization.json.Json
import java.time.LocalDate
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
    private val checkIns: CheckInRepository,
    private val focus: FocusRepository,
    private val badges: BadgeRepository,
    private val reminders: ReminderRepository,
    private val wishes: WishRepository,
    private val settings: SettingsRepository,
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

        if (snapshot.schemaVersion > 2) return ImportResult(false, error = "备份版本较新，请升级 App 后再导入")
        val invalid = validate(snapshot)
        if (invalid != null) return ImportResult(false, error = invalid)

        var imported = 0
        var conflicts = 0
        var skipped = 0
        val localTasks = tasks.captureAll().tasks.associateBy { it.id }
        val newTaskIds = mutableSetOf<String>()

        snapshot.tasks.forEach { remote ->
            val local = localTasks[remote.id]
            when {
                local == null -> {
                    tasks.restoreAll(TaskBundle(tasks = listOf(remote.toDomain())), overwrite = false)
                    newTaskIds += remote.id
                    imported++
                }
                local.toEntity() == remote -> skipped++
                else -> conflicts++ // 保留本地，冲突只提示不覆盖
            }
        }
        snapshot.subTasks.filter { it.parentTaskId in newTaskIds }.forEach {
            tasks.restoreAll(TaskBundle(subTasks = listOf(it.toDomain())), overwrite = false)
        }
        snapshot.habitRules.filter { it.taskId in newTaskIds }.forEach { tasks.upsertHabitRule(it.toDomain()) }
        snapshot.goalRules.filter { it.taskId in newTaskIds }.forEach { tasks.upsertGoalRule(it.toDomain()) }
        val checkReport = checkIns.restoreAll(snapshot.checkIns.map { it.toDomain() })
        val focusReport = focus.restoreAll(snapshot.focusSessions.map { it.toDomain() })
        val badgeReport = badges.restoreAll(snapshot.badges.map { it.toDomain() })
        val wishReport = wishes.restoreAll(snapshot.wishes.map { it.toDomain() })
        val reminderReport = reminders.restoreAll(snapshot.reminders.map { it.toDomain() })
        imported += checkReport.inserted + focusReport.inserted + badgeReport.inserted +
            wishReport.inserted + reminderReport.inserted
        skipped += checkReport.skipped + focusReport.skipped + badgeReport.skipped +
            wishReport.skipped + reminderReport.skipped

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
        runCatching { snapshot.checkIns.forEach { LocalDate.parse(it.checkDate) } }
            .onFailure { return "打卡记录的日期格式错误" }
        if (snapshot.checkIns.any { it.id.isBlank() || it.taskId.isBlank() }) return "打卡记录缺少 id"
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
