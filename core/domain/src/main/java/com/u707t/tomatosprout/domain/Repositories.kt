package com.u707t.tomatosprout.domain

import kotlinx.coroutines.flow.Flow

data class TaskBundle(
    val tasks: List<Task> = emptyList(),
    val habitRules: List<HabitRule> = emptyList(),
    val goalRules: List<GoalRule> = emptyList(),
)

interface TaskRepository {
    fun observeActiveTasks(): Flow<List<Task>>
    fun observeAllTasks(): Flow<List<Task>>
    fun observeHabitRules(): Flow<List<HabitRule>>
    fun observeGoalRules(): Flow<List<GoalRule>>

    suspend fun task(id: String): Task?
    suspend fun tasksByParent(parentId: String): List<Task>
    suspend fun upsert(task: Task)
    suspend fun setStatus(id: String, status: TaskStatus, updatedAt: Long)
    suspend fun softDelete(id: String, updatedAt: Long)
    suspend fun incrementCompletedPomos(id: String, delta: Int, updatedAt: Long)

    suspend fun habitRule(taskId: String): HabitRule?
    suspend fun goalRule(taskId: String): GoalRule?
    suspend fun upsertHabitRule(rule: HabitRule)
    suspend fun upsertGoalRule(rule: GoalRule)
    suspend fun deleteHabitRule(taskId: String)
    suspend fun deleteGoalRule(taskId: String)

    suspend fun captureAll(): TaskBundle
    suspend fun restoreAll(bundle: TaskBundle, overwrite: Boolean): MergeReport
}

interface CheckInRepository {
    fun observeAll(): Flow<List<CheckIn>>
    suspend fun all(): List<CheckIn>
    suspend fun forTask(taskId: String): List<CheckIn>
    suspend fun insert(checkIn: CheckIn): Boolean
    suspend fun updateCount(id: String, count: Int)
    suspend fun updateMeta(id: String, note: String?, mood: Int?)
    suspend fun softDelete(id: String, at: Long)
    suspend fun restoreAll(items: List<CheckIn>): MergeReport
}

interface FocusRepository {
    fun observeAll(): Flow<List<FocusSession>>
    suspend fun all(): List<FocusSession>
    suspend fun insert(session: FocusSession)
    suspend fun softDelete(id: String, at: Long)
    suspend fun restoreAll(items: List<FocusSession>): MergeReport
}

interface BadgeRepository {
    fun observeAll(): Flow<List<Badge>>
    suspend fun all(): List<Badge>
    suspend fun seed(defs: List<Badge>)
    suspend fun unlock(keys: Set<String>, at: Long)
    suspend fun restoreAll(items: List<Badge>): MergeReport
}

interface ReminderRepository {
    fun observeAll(): Flow<List<ReminderRule>>
    suspend fun all(): List<ReminderRule>
    suspend fun forTask(taskId: String): ReminderRule?
    suspend fun upsert(rule: ReminderRule)
    suspend fun deleteForTask(taskId: String)
    suspend fun restoreAll(items: List<ReminderRule>): MergeReport
}

interface WishRepository {
    fun observeAll(): Flow<List<WishItem>>
    suspend fun all(): List<WishItem>
    suspend fun upsert(item: WishItem)
    suspend fun softDelete(id: String, at: Long)
    suspend fun restoreAll(items: List<WishItem>): MergeReport
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)

    suspend fun saveWebDavPassword(password: String)
    suspend fun webDavPassword(): String?
    suspend fun hasWebDavPassword(): Boolean
}
