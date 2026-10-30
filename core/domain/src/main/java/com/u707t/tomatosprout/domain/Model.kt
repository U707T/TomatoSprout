package com.u707t.tomatosprout.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TaskType { HABIT, POMODORO, GOAL, TODO }

/** 中断原因：与番茄ToDo 的「打断原因」统计对齐；null 表示旧数据未记录。 */
enum class InterruptReason(val label: String) {
    DISTRACTED("分心了"),
    INTERRUPTED("被外界打断"),
    GAVE_UP("主动放弃"),
    OTHER("其它"),
}

/**
 * v1.6 起不再有 ARCHIVED：任务只有「进行中 / 已完成」两种状态。
 * 删除是独立维度（Task.deletedAt 软删除），历史打卡与专注记录继续保留。
 * 旧数据库/旧备份中的 "ARCHIVED" 会在 Migration 与 Mapper 中转换为删除或 TODO。
 */
enum class TaskStatus { TODO, DONE }
enum class Frequency { DAILY, WEEKDAYS, WEEKLY, CUSTOM }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Task(
    val id: String,
    val type: TaskType,
    val title: String,
    val note: String? = null,
    val color: Long = 0xFFFF6B6B,
    val icon: String? = null,
    val priority: Int = 1,
    val dueAt: Long? = null,
    val repeatRule: String? = null,
    val estimatedPomos: Int = 0,
    val completedPomos: Int = 0,
    val status: TaskStatus = TaskStatus.TODO,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val parentId: String? = null,
    /** 任务卡片流中的手动排序；越小越靠前。 */
    val sortOrder: Int = 0,
    /** countdown / countup / none */
    val timerMode: String = "countdown",
    val focusMinutes: Int = 25,
)

/** 独立子任务表（v2）。父任务状态由全部子任务自动汇总。 */
data class SubTask(
    val id: String,
    val parentTaskId: String,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    /** v6：跨设备合并用的记录级时间戳。旧数据回填为 createdAt。 */
    val updatedAt: Long = 0L,
    /** v7：软删除墓碑；null = 未删除。 */
    val deletedAt: Long? = null,
)

data class HabitRule(
    val taskId: String,
    val frequency: Frequency = Frequency.DAILY,
    val targetCountPerPeriod: Int = 1,
    val targetDaysPerWeek: Int? = null,
    val weekdaysMask: Int = 0b1111111,
    val intervalDays: Int = 1,
    val startDate: LocalDate,
    /** v8：规则记录级时间戳，支持独立于任务合并。 */
    val updatedAt: Long = 0L,
)

data class GoalRule(
    val taskId: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val targetTotalCount: Int? = null,
    val targetTotalSeconds: Long? = null,
    /** v8：规则记录级时间戳，支持独立于任务合并。 */
    val updatedAt: Long = 0L,
)

data class CheckIn(
    val id: String,
    val taskId: String,
    val date: LocalDate,
    val count: Int = 1,
    val note: String? = null,
    val mood: Int? = null,
    val source: String = "manual",
    val isMakeup: Boolean = false,
    val createdAt: Long,
    val deletedAt: Long? = null,
    /** v1.8：跨设备合并用的记录级时间戳。旧数据回填为 createdAt。 */
    val updatedAt: Long = 0L,
)

data class FocusSession(
    val id: String,
    val taskId: String?,
    val mode: String = "pomodoro",
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val startedAt: Long,
    val endedAt: Long,
    val isMidnightShifted: Boolean = false,
    val interruptionCount: Int = 0,
    val note: String? = null,
    val deletedAt: Long? = null,
    /** completed / interrupted；v1.6 新增，旧数据为空按 completed 处理。 */
    val outcome: String? = null,
    /** 归属日期（跨午夜时按设置取开始日或结束日），用于统计与时间轴。 */
    val attributedDate: LocalDate? = null,
    /** 由本次专注自动写入的打卡 id，便于回溯。 */
    val checkInId: String? = null,
    /** v1.8：中断原因（InterruptReason.name），仅 outcome=interrupted 时有值。 */
    val interruptReason: String? = null,
    /** v1.8：跨设备合并用的记录级时间戳。旧数据回填为 endedAt。 */
    val updatedAt: Long = 0L,
) {
    val completed: Boolean get() = outcome != "interrupted" && actualSeconds > 0
}

data class ReminderRule(
    val id: String,
    val taskId: String,
    val timeOfDay: String,
    val weekdaysMask: Int = 0b1111111,
    val enabled: Boolean = true,
    /** v7：跨设备合并用的记录级时间戳。 */
    val updatedAt: Long = 0L,
    /** v8：软删除墓碑；null = 未删除。 */
    val deletedAt: Long? = null,
)

data class Badge(
    val id: String,
    val key: String,
    val name: String,
    val conditionJson: String,
    val unlockedAt: Long? = null,
    val description: String = "",
)

data class WishItem(
    val id: String,
    val content: String,
    val status: String = "pending",
    val createdAt: Long,
    val deletedAt: Long? = null,
    /** v7：跨设备合并用的记录级时间戳。 */
    val updatedAt: Long = 0L,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val simpleMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    /** 专注时是否使用系统实时活动 / 澎湃 OS 超级岛；仅本机生效，不参与同步与备份。 */
    val liveActivityEnabled: Boolean = true,
    /**
     * 强制发送澎湃 OS 焦点通知，无视机型与焦点开关探测。
     * 给装了白名单解锁模块 / 系统把开关读成关闭的用户用；仅本机生效。
     */
    val forceFocusNotification: Boolean = false,
    val dndEnabled: Boolean = false,
    val dndStart: String = "22:00",
    val dndEnd: String = "07:00",
    val defaultFocusMinutes: Int = 25,
    val defaultBreakMinutes: Int = 5,
    /** 每 4 个番茄后的长休息时长。 */
    val longBreakMinutes: Int = 15,
    /** 正计时 / 倒计时不足 30 秒时直接丢弃，不写中断记录。 */
    val ignoreShortFocus: Boolean = true,
    val strictMode: Boolean = false,
    val countUpMode: Boolean = false,
    val midnightShift: Boolean = true,
    val wifiOnly: Boolean = false,
    val webDavServer: String = "",
    val webDavUsername: String = "",
    val webDavPath: String = "/tomatosprout",
    val deviceId: String = "",
    val lastSyncedAt: Long? = null,
)

/** 可在 JVM 测试中替换的时钟；Domain 层不依赖 Android。 */
interface AppClock {
    fun nowMillis(): Long
    fun zone(): ZoneId
}

object SystemAppClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

fun AppClock.today(): LocalDate =
    Instant.ofEpochMilli(nowMillis()).atZone(zone()).toLocalDate()

/** 同步/导入结果里只需要一个轻量冲突信息。 */
data class MergeReport(
    val inserted: Int = 0,
    val updated: Int = 0,
    val conflicts: Int = 0,
    val skipped: Int = 0,
) {
    operator fun plus(other: MergeReport) = MergeReport(
        inserted + other.inserted,
        updated + other.updated,
        conflicts + other.conflicts,
        skipped + other.skipped,
    )
}
