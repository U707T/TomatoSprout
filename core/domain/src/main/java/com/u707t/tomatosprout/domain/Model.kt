package com.u707t.tomatosprout.domain

import java.time.LocalDate
import java.time.ZoneId

enum class TaskType { HABIT, POMODORO, GOAL, TODO }
enum class TaskStatus { TODO, DONE, ARCHIVED }
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
)

data class HabitRule(
    val taskId: String,
    val frequency: Frequency = Frequency.DAILY,
    val targetCountPerPeriod: Int = 1,
    val targetDaysPerWeek: Int? = null,
    val weekdaysMask: Int = 0b1111111,
    val intervalDays: Int = 1,
    val startDate: LocalDate,
)

data class GoalRule(
    val taskId: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val targetTotalCount: Int? = null,
    val targetTotalSeconds: Long? = null,
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
    val deletedAt: Long? = null,
)

data class ReminderRule(
    val id: String,
    val taskId: String,
    val timeOfDay: String,
    val weekdaysMask: Int = 0b1111111,
    val enabled: Boolean = true,
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
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val simpleMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val dndEnabled: Boolean = false,
    val dndStart: String = "22:00",
    val dndEnd: String = "07:00",
    val defaultFocusMinutes: Int = 25,
    val defaultBreakMinutes: Int = 5,
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

fun AppClock.today(): LocalDate = LocalDate.now(zone())

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
