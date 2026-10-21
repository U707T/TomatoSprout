package com.u707t.tomatosprout.sync

import com.u707t.tomatosprout.data.db.BadgeEntity
import com.u707t.tomatosprout.data.db.CheckInEntity
import com.u707t.tomatosprout.data.db.FocusSessionEntity
import com.u707t.tomatosprout.data.db.GoalRuleEntity
import com.u707t.tomatosprout.data.db.HabitRuleEntity
import com.u707t.tomatosprout.data.db.ReminderRuleEntity
import com.u707t.tomatosprout.data.db.TaskEntity
import com.u707t.tomatosprout.data.db.WishItemEntity
import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class SettingsDto(
    val themeMode: String = "SYSTEM",
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
)

/** 同步与备份共用同一份快照格式；schemaVersion 用于导入校验。 */
@Serializable
data class SyncSnapshot(
    val schemaVersion: Int = 1,
    val deviceId: String = "",
    val exportedAt: Long = 0L,
    val tasks: List<TaskEntity> = emptyList(),
    val habitRules: List<HabitRuleEntity> = emptyList(),
    val goalRules: List<GoalRuleEntity> = emptyList(),
    val checkIns: List<CheckInEntity> = emptyList(),
    val focusSessions: List<FocusSessionEntity> = emptyList(),
    val reminders: List<ReminderRuleEntity> = emptyList(),
    val badges: List<BadgeEntity> = emptyList(),
    val wishes: List<WishItemEntity> = emptyList(),
    val settings: SettingsDto? = null,
)

data class SyncResult(
    val success: Boolean,
    val message: String,
    val items: Int = 0,
    val conflicts: Int = 0,
    val retryable: Boolean = false,
)

object Hashing {
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
