package com.u707t.tomatosprout.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库版本 3（v1.6）：
 * - 取消归档：旧 ARCHIVED 任务转为软删除；
 * - focus_sessions 增加 outcome / attributedDate / checkInId；
 * - 打卡与专注仍然只允许 INSERT 与软删除，删除任务不会级联删除历史。
 *
 * 版本演进规则：
 * - 每次 Entity 变更必须同时提升 version、编写 Migration 与迁移测试；
 * - 正式发布后禁止 DROP TABLE；
 * - check_ins / focus_sessions 只允许 INSERT 与软删除（deletedAt）。
 */
@Database(
    entities = [
        TaskEntity::class,
        SubTaskEntity::class,
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
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun subTaskDao(): SubTaskDao
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN timerMode TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN focusMinutes INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS subtasks (
                        id TEXT NOT NULL PRIMARY KEY,
                        parentTaskId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        done INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(parentTaskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_subtasks_parentTaskId ON subtasks(parentTaskId)")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO subtasks (id, parentTaskId, title, done, sortOrder, createdAt)
                    SELECT id, parentId, title,
                           CASE WHEN status = 'DONE' THEN 1 ELSE 0 END, 0, createdAt
                    FROM tasks WHERE parentId IS NOT NULL
                    """.trimIndent(),
                )
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN note TEXT")
            }
        }

        /**
         * v2 → v3：
         * 归档语义取消。旧 ARCHIVED 任务按「用户原本想把它从列表移除」的意图转为软删除，
         * 这样 CheckIn / FocusSession 仍然保留，历史页显示「[已删除] 原任务名」。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE tasks
                    SET status = 'TODO',
                        deletedAt = CASE WHEN deletedAt IS NULL THEN MAX(updatedAt, 1) ELSE deletedAt END
                    WHERE status = 'ARCHIVED'
                    """.trimIndent(),
                )
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN outcome TEXT")
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN attributedDate TEXT")
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN checkInId TEXT")
                db.execSQL("UPDATE focus_sessions SET outcome = 'completed' WHERE outcome IS NULL")
                db.execSQL(
                    """
                    UPDATE focus_sessions
                    SET attributedDate = strftime('%Y-%m-%d', endedAt / 1000, 'unixepoch', 'localtime')
                    WHERE attributedDate IS NULL
                    """.trimIndent(),
                )
            }
        }
    }
}
