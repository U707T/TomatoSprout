package com.u707t.tomatosprout.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 所有时间戳统一 epoch millis；日期字段存 yyyy-MM-dd（用户本地时区的逻辑日期）。
 * 枚举以字符串存储，便于同步文件跨版本读取。
 */
@Serializable
@Entity(
    tableName = "tasks",
    indices = [Index("status"), Index("parentId"), Index("deletedAt")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val note: String? = null,
    val color: Long = 0xFFFF6B6B,
    val icon: String? = null,
    val priority: Int = 1,
    val dueAt: Long? = null,
    val repeatRule: String? = null,
    val estimatedPomos: Int = 0,
    val completedPomos: Int = 0,
    val status: String = "TODO",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val parentId: String? = null,
)

@Serializable
@Entity(tableName = "habit_rules")
data class HabitRuleEntity(
    @PrimaryKey val taskId: String,
    val frequency: String = "DAILY",
    val targetCountPerPeriod: Int = 1,
    val targetDaysPerWeek: Int? = null,
    val weekdaysMask: Int = 0b1111111,
    val intervalDays: Int = 1,
    val startDate: String,
)

@Serializable
@Entity(tableName = "goal_rules")
data class GoalRuleEntity(
    @PrimaryKey val taskId: String,
    val startDate: String,
    val endDate: String,
    val targetTotalCount: Int? = null,
    val targetTotalSeconds: Long? = null,
)

@Serializable
@Entity(
    tableName = "check_ins",
    indices = [
        Index("taskId"),
        Index("checkDate"),
        Index(value = ["taskId", "checkDate", "isMakeup"], unique = true),
    ],
)
data class CheckInEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val checkDate: String,
    val count: Int = 1,
    val note: String? = null,
    val mood: Int? = null,
    val source: String = "manual",
    val isMakeup: Boolean = false,
    val createdAt: Long = 0L,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "focus_sessions",
    indices = [Index("taskId"), Index("endedAt")],
)
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val taskId: String? = null,
    val mode: String = "pomodoro",
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val startedAt: Long,
    val endedAt: Long,
    val isMidnightShifted: Boolean = false,
    val interruptionCount: Int = 0,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(tableName = "reminder_rules", indices = [Index(value = ["taskId"], unique = true)])
data class ReminderRuleEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val timeOfDay: String = "20:00",
    val weekdaysMask: Int = 0b1111111,
    val enabled: Boolean = true,
)

@Serializable
@Entity(tableName = "badges", indices = [Index(value = ["badgeKey"], unique = true)])
data class BadgeEntity(
    @PrimaryKey val id: String,
    val badgeKey: String,
    val name: String,
    val conditionJson: String = "{}",
    val unlockedAt: Long? = null,
    val description: String = "",
)

@Serializable
@Entity(tableName = "wish_items", indices = [Index("status")])
data class WishItemEntity(
    @PrimaryKey val id: String,
    val content: String,
    val status: String = "pending",
    val createdAt: Long = 0L,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(tableName = "sync_manifest", primaryKeys = ["deviceId", "fileName"])
data class SyncManifestEntity(
    val deviceId: String,
    val fileName: String,
    val contentHash: String,
    val remoteEtag: String? = null,
    val lastSyncedAt: Long = 0L,
)

@Serializable
@Entity(tableName = "sync_logs", indices = [Index("at")])
data class SyncLogEntity(
    @PrimaryKey val id: String,
    val at: Long,
    val direction: String,
    val items: Int = 0,
    val conflicts: Int = 0,
    val error: Boolean = false,
    val message: String = "",
)
