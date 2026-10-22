package com.u707t.tomatosprout.domain

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class TaskBundle(
    val tasks: List<Task> = emptyList(),
    val habitRules: List<HabitRule> = emptyList(),
    val goalRules: List<GoalRule> = emptyList(),
    val subTasks: List<SubTask> = emptyList(),
)

interface SubTaskRepository {
    fun observeAll(): Flow<List<SubTask>>
    fun observeForParent(parentId: String): Flow<List<SubTask>>
    suspend fun forParent(parentId: String): List<SubTask>
    suspend fun all(): List<SubTask>
    suspend fun upsert(subTask: SubTask)
    suspend fun delete(id: String, at: Long)
    suspend fun deleteForParent(parentId: String, at: Long)
    suspend fun insertAllIgnore(items: List<SubTask>): MergeReport
    suspend fun insertAll(items: List<SubTask>)

    /** 记录级合并：远端 updatedAt 更新才覆盖本地标题 / 完成状态 / 排序 / 删除。 */
    suspend fun mergeAll(items: List<SubTask>): MergeReport
}

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
    suspend fun recountCompletedPomos()
    suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    suspend fun habitRule(taskId: String): HabitRule?
    suspend fun goalRule(taskId: String): GoalRule?
    suspend fun upsertHabitRule(rule: HabitRule)
    suspend fun upsertGoalRule(rule: GoalRule)
    suspend fun deleteHabitRule(taskId: String)
    suspend fun deleteGoalRule(taskId: String)

    /** 规则记录级合并：远端 updatedAt 更新才覆盖本地。 */
    suspend fun mergeHabitRules(items: List<HabitRule>): MergeReport
    suspend fun mergeGoalRules(items: List<GoalRule>): MergeReport

    suspend fun captureAll(): TaskBundle
    suspend fun restoreAll(bundle: TaskBundle, overwrite: Boolean): MergeReport
}

interface CheckInRepository {
    fun observeAll(): Flow<List<CheckIn>>
    suspend fun all(): List<CheckIn>
    suspend fun forTask(taskId: String): List<CheckIn>
    suspend fun insert(checkIn: CheckIn): Boolean
    suspend fun updateCount(id: String, count: Int, at: Long)
    suspend fun updateMeta(id: String, note: String?, mood: Int?, at: Long)
    suspend fun softDelete(id: String, at: Long)

    /** 记录级合并：远端 updatedAt 更新才覆盖本地；同一天多次打卡取较大 count。 */
    suspend fun mergeAll(items: List<CheckIn>): MergeReport
}

interface FocusRepository {
    fun observeAll(): Flow<List<FocusSession>>
    suspend fun all(): List<FocusSession>
    suspend fun insert(session: FocusSession)
    suspend fun updateNote(id: String, note: String?, at: Long)
    suspend fun softDelete(id: String, at: Long)

    /** 记录级合并：远端 updatedAt 更新才覆盖本地。 */
    suspend fun mergeAll(items: List<FocusSession>): MergeReport

    /** 某归属日完成的番茄数（只算 mode=pomodoro 且 completed），用于长休息节奏。 */
    suspend fun completedPomodorosOn(date: LocalDate): Int
}

interface BadgeRepository {
    fun observeAll(): Flow<List<Badge>>
    suspend fun all(): List<Badge>
    suspend fun seed(defs: List<Badge>)
    suspend fun unlock(keys: Set<String>, at: Long)
    suspend fun restoreAll(items: List<Badge>): MergeReport
}

/**
 * 只暴露「刷新徽章」这一件事，避免 UseCase 之间直接依赖具体实现，
 * 也便于在 JVM 单测里替换成空实现。
 */
fun interface BadgeRefresher {
    suspend fun refresh(): List<Badge>
}

interface ReminderRepository {
    fun observeAll(): Flow<List<ReminderRule>>
    suspend fun all(): List<ReminderRule>
    suspend fun forTask(taskId: String): ReminderRule?
    suspend fun upsert(rule: ReminderRule)
    /** 保存提醒：若同 taskId 已有墓碑，复用其 id 并复活，避免撞唯一索引。 */
    suspend fun saveForTask(rule: ReminderRule): ReminderRule
    suspend fun deleteForTask(taskId: String, at: Long)

    /** 记录级合并；任务唯一键相同但 id 不同时保留本地 id。 */
    suspend fun mergeAll(items: List<ReminderRule>): MergeReport
}

interface WishRepository {
    fun observeAll(): Flow<List<WishItem>>
    suspend fun all(): List<WishItem>
    suspend fun upsert(item: WishItem)
    suspend fun softDelete(id: String, at: Long)

    /** 记录级合并：远端 updatedAt 更新才覆盖本地。 */
    suspend fun mergeAll(items: List<WishItem>): MergeReport
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)

    suspend fun saveWebDavPassword(password: String)
    suspend fun webDavPassword(): String?
    suspend fun hasWebDavPassword(): Boolean
}
