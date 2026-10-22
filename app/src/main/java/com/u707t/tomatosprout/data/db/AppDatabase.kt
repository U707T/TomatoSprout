package com.u707t.tomatosprout.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库版本 6（v1.8.1）：
 * - subtasks 增加 updatedAt，子任务标题 / 完成状态可以双向合并；
 * - check_ins / focus_sessions 的记录级 updatedAt 与 interruptReason 沿用 v5；
 * - 打卡与专注仍然只做软删除，删除任务不会级联删除历史。
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
    version = 6,
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
        /**
         * v3 → v4：把「实际番茄数」回填成已完成专注次数。
         *
         * v1.6 之前只有 TaskType.POMODORO 会累加 completedPomos，其它任务即使专注完成
         * 也一直是 0。这里按 focus_sessions 的真实记录重算一次，保证老用户升级后
         * 卡片上的「番茄 x/y」与历史数据一致。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE tasks
                    SET completedPomos = (
                        SELECT COUNT(*) FROM focus_sessions f
                        WHERE f.taskId = tasks.id
                          AND f.deletedAt IS NULL
                          AND f.mode = 'pomodoro'
                          AND COALESCE(f.outcome, 'completed') = 'completed'
                    )
                    """.trimIndent(),
                )
            }
        }
        /**
         * v4 → v5：记录级双向合并所需的时间戳 + 中断原因。
         *
         * updatedAt 用 NOT NULL DEFAULT 0 加列，再回填为原记录时间；
         * Entity 上对应 @ColumnInfo(defaultValue = "0")，保证 Room 校验一致。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE check_ins ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE check_ins SET updatedAt = createdAt WHERE updatedAt = 0")
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE focus_sessions SET updatedAt = endedAt WHERE updatedAt = 0")
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN interruptReason TEXT")
            }
        }
        /**
         * v5 → v6：子任务增加记录级 updatedAt，旧数据回填 createdAt。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subtasks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE subtasks SET updatedAt = createdAt WHERE updatedAt = 0")
            }
        }
    }
}
