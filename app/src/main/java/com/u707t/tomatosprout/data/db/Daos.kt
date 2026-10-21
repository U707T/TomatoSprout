package com.u707t.tomatosprout.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND status != 'ARCHIVED' AND parentId IS NULL ORDER BY sortOrder ASC, priority ASC, createdAt ASC")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE parentId = :parentId AND deletedAt IS NULL ORDER BY createdAt ASC")
    suspend fun children(parentId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks ORDER BY createdAt ASC, id ASC")
    suspend fun all(): List<TaskEntity>

    @Upsert
    suspend fun upsert(entity: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entities: List<TaskEntity>): List<Long>

    @Upsert
    suspend fun upsertAll(entities: List<TaskEntity>)

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setStatus(id: String, status: String, updatedAt: Long)

    @Query("UPDATE tasks SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE tasks SET completedPomos = completedPomos + :delta, updatedAt = :at WHERE id = :id")
    suspend fun incrementCompletedPomos(id: String, delta: Int, at: Long)

    @Query("UPDATE tasks SET sortOrder = :sortOrder, updatedAt = :at WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, at: Long)

    @Query("UPDATE tasks SET deletedAt = :at, updatedAt = :at WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(at: Long)
}

@Dao
interface SubTaskDao {
    @Query("SELECT * FROM subtasks WHERE parentTaskId = :parentId ORDER BY sortOrder ASC, createdAt ASC")
    fun observeForParent(parentId: String): Flow<List<SubTaskEntity>>

    @Query("SELECT * FROM subtasks WHERE parentTaskId = :parentId ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun forParent(parentId: String): List<SubTaskEntity>

    @Query("SELECT * FROM subtasks ORDER BY parentTaskId ASC, sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<SubTaskEntity>>

    @Query("SELECT * FROM subtasks ORDER BY parentTaskId ASC, sortOrder ASC, createdAt ASC")
    suspend fun all(): List<SubTaskEntity>

    @Upsert
    suspend fun upsert(entity: SubTaskEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<SubTaskEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SubTaskEntity>)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM subtasks WHERE parentTaskId = :parentId")
    suspend fun deleteForParent(parentId: String)

    @Query("DELETE FROM subtasks")
    suspend fun deleteAll()
}

@Dao
interface HabitRuleDao {
    @Query("SELECT * FROM habit_rules ORDER BY taskId ASC")
    fun observeAll(): Flow<List<HabitRuleEntity>>

    @Query("SELECT * FROM habit_rules WHERE taskId = :taskId LIMIT 1")
    suspend fun get(taskId: String): HabitRuleEntity?

    @Query("SELECT * FROM habit_rules ORDER BY taskId ASC")
    suspend fun all(): List<HabitRuleEntity>

    @Upsert
    suspend fun upsert(entity: HabitRuleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entities: List<HabitRuleEntity>): List<Long>

    @Upsert
    suspend fun upsertAll(entities: List<HabitRuleEntity>)

    @Query("DELETE FROM habit_rules WHERE taskId = :taskId")
    suspend fun delete(taskId: String)
}

@Dao
interface GoalRuleDao {
    @Query("SELECT * FROM goal_rules ORDER BY taskId ASC")
    fun observeAll(): Flow<List<GoalRuleEntity>>

    @Query("SELECT * FROM goal_rules WHERE taskId = :taskId LIMIT 1")
    suspend fun get(taskId: String): GoalRuleEntity?

    @Query("SELECT * FROM goal_rules ORDER BY taskId ASC")
    suspend fun all(): List<GoalRuleEntity>

    @Upsert
    suspend fun upsert(entity: GoalRuleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entities: List<GoalRuleEntity>): List<Long>

    @Upsert
    suspend fun upsertAll(entities: List<GoalRuleEntity>)

    @Query("DELETE FROM goal_rules WHERE taskId = :taskId")
    suspend fun delete(taskId: String)
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins ORDER BY createdAt DESC, id ASC")
    fun observeAll(): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun allActive(): List<CheckInEntity>

    @Query("SELECT * FROM check_ins ORDER BY createdAt DESC, id ASC")
    suspend fun all(): List<CheckInEntity>

    @Query("SELECT * FROM check_ins WHERE taskId = :taskId AND deletedAt IS NULL ORDER BY checkDate ASC")
    suspend fun forTask(taskId: String): List<CheckInEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: CheckInEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<CheckInEntity>): List<Long>

    @Query("UPDATE check_ins SET deletedAt = NULL, createdAt = :at, source = :source WHERE taskId = :taskId AND checkDate = :date AND isMakeup = :makeup")
    suspend fun reactivate(taskId: String, date: String, makeup: Boolean, at: Long, source: String): Int

    @Query("UPDATE check_ins SET count = :count WHERE id = :id")
    suspend fun updateCount(id: String, count: Int)

    @Query("UPDATE check_ins SET note = :note, mood = :mood WHERE id = :id")
    suspend fun updateMeta(id: String, note: String?, mood: Int?)

    @Query("UPDATE check_ins SET deletedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE check_ins SET deletedAt = :at WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(at: Long)
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions WHERE deletedAt IS NULL ORDER BY endedAt DESC")
    fun observeActive(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY endedAt DESC, id ASC")
    fun observeAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE deletedAt IS NULL ORDER BY endedAt DESC")
    suspend fun allActive(): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions ORDER BY endedAt DESC, id ASC")
    suspend fun all(): List<FocusSessionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: FocusSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<FocusSessionEntity>): List<Long>

    @Query("UPDATE focus_sessions SET note = :note WHERE id = :id")
    suspend fun updateNote(id: String, note: String?)

    @Query("UPDATE focus_sessions SET deletedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE focus_sessions SET deletedAt = :at WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(at: Long)
}

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badges ORDER BY name ASC")
    fun observeAll(): Flow<List<BadgeEntity>>

    @Query("SELECT * FROM badges ORDER BY name ASC")
    suspend fun all(): List<BadgeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seed(entities: List<BadgeEntity>)

    @Query("UPDATE badges SET unlockedAt = :at WHERE badgeKey IN (:keys) AND unlockedAt IS NULL")
    suspend fun unlock(keys: List<String>, at: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<BadgeEntity>): List<Long>

    @Query("UPDATE badges SET unlockedAt = NULL")
    suspend fun resetUnlocks()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_rules ORDER BY taskId ASC")
    fun observeAll(): Flow<List<ReminderRuleEntity>>

    @Query("SELECT * FROM reminder_rules ORDER BY taskId ASC")
    suspend fun all(): List<ReminderRuleEntity>

    @Query("SELECT * FROM reminder_rules WHERE taskId = :taskId LIMIT 1")
    suspend fun forTask(taskId: String): ReminderRuleEntity?

    @Upsert
    suspend fun upsert(entity: ReminderRuleEntity)

    @Query("DELETE FROM reminder_rules WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)

    @Query("DELETE FROM reminder_rules")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<ReminderRuleEntity>): List<Long>
}

@Dao
interface WishDao {
    @Query("SELECT * FROM wish_items WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<WishItemEntity>>

    @Query("SELECT * FROM wish_items ORDER BY createdAt DESC, id ASC")
    suspend fun all(): List<WishItemEntity>

    @Upsert
    suspend fun upsert(entity: WishItemEntity)

    @Query("UPDATE wish_items SET deletedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE wish_items SET deletedAt = :at WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(at: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<WishItemEntity>): List<Long>
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_manifest")
    suspend fun manifests(): List<SyncManifestEntity>

    @Query("SELECT * FROM sync_manifest WHERE deviceId = :deviceId AND fileName = :fileName LIMIT 1")
    suspend fun manifest(deviceId: String, fileName: String): SyncManifestEntity?

    @Upsert
    suspend fun upsertManifest(entity: SyncManifestEntity)

    @Query("SELECT * FROM sync_logs ORDER BY at DESC LIMIT 100")
    fun observeLogs(): Flow<List<SyncLogEntity>>

    @Insert
    suspend fun log(entity: SyncLogEntity)

    @Query("DELETE FROM sync_logs")
    suspend fun clearLogs()
}
