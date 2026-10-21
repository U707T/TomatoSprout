package com.u707t.tomatosprout.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 数据库版本 1（首次发布）。
 *
 * 版本演进规则：
 * - 每次 Entity 变更必须同时提升 version、编写 Migration 与迁移测试；
 * - 正式发布后禁止 DROP TABLE；
 * - check_ins / focus_sessions 只允许 INSERT 与软删除（deletedAt）。
 */
@Database(
    entities = [
        TaskEntity::class,
        HabitRuleEntity::class,
        GoalRuleEntity::class,
        CheckInEntity::class,
        FocusSessionEntity::class,
        ReminderRuleEntity::class,
        BadgeEntity::class,
        WishItemEntity::class,
        SyncManifestEntity::class,
        SyncLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun habitRuleDao(): HabitRuleDao
    abstract fun goalRuleDao(): GoalRuleDao
    abstract fun checkInDao(): CheckInDao
    abstract fun focusDao(): FocusDao
    abstract fun badgeDao(): BadgeDao
    abstract fun reminderDao(): ReminderDao
    abstract fun wishDao(): WishDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "tomatosprout.db"
    }
}
