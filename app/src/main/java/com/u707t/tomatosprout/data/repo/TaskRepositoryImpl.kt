package com.u707t.tomatosprout.data.repo

import com.u707t.tomatosprout.data.db.GoalRuleDao
import com.u707t.tomatosprout.data.db.HabitRuleDao
import com.u707t.tomatosprout.data.db.SubTaskDao
import com.u707t.tomatosprout.data.db.TaskDao
import com.u707t.tomatosprout.data.db.toDomain
import com.u707t.tomatosprout.data.db.toEntity
import com.u707t.tomatosprout.domain.GoalRule
import com.u707t.tomatosprout.domain.HabitRule
import com.u707t.tomatosprout.domain.MergeReport
import com.u707t.tomatosprout.domain.SubTask
import com.u707t.tomatosprout.domain.SubTaskRepository
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskBundle
import com.u707t.tomatosprout.domain.TaskRepository
import com.u707t.tomatosprout.domain.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val tasks: TaskDao,
    private val habits: HabitRuleDao,
    private val goals: GoalRuleDao,
    private val subTasks: SubTaskDao,
) : TaskRepository, SubTaskRepository {
    override fun observeActiveTasks(): Flow<List<Task>> = tasks.observeActive().map { list -> list.map { it.toDomain() } }
    override fun observeAllTasks(): Flow<List<Task>> = tasks.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observeHabitRules(): Flow<List<HabitRule>> = habits.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observeGoalRules(): Flow<List<GoalRule>> = goals.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun task(id: String): Task? = tasks.get(id)?.toDomain()
    override suspend fun tasksByParent(parentId: String): List<Task> = tasks.children(parentId).map { it.toDomain() }
    override suspend fun upsert(task: Task) = tasks.upsert(task.toEntity())
    override suspend fun setStatus(id: String, status: TaskStatus, updatedAt: Long) = tasks.setStatus(id, status.name, updatedAt)
    override suspend fun softDelete(id: String, updatedAt: Long) = tasks.softDelete(id, updatedAt)
    override suspend fun incrementCompletedPomos(id: String, delta: Int, updatedAt: Long) = tasks.incrementCompletedPomos(id, delta, updatedAt)
    override suspend fun recountCompletedPomos() = tasks.recountCompletedPomos()
    override suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long) = tasks.updateSortOrder(id, sortOrder, updatedAt)

    override suspend fun habitRule(taskId: String): HabitRule? = habits.get(taskId)?.toDomain()
    override suspend fun goalRule(taskId: String): GoalRule? = goals.get(taskId)?.toDomain()
    override suspend fun upsertHabitRule(rule: HabitRule) = habits.upsert(rule.toEntity())
    override suspend fun upsertGoalRule(rule: GoalRule) = goals.upsert(rule.toEntity())
    override suspend fun deleteHabitRule(taskId: String) = habits.delete(taskId)
    override suspend fun deleteGoalRule(taskId: String) = goals.delete(taskId)

    override fun observeAll(): Flow<List<SubTask>> = subTasks.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observeForParent(parentId: String): Flow<List<SubTask>> =
        subTasks.observeForParent(parentId).map { list -> list.map { it.toDomain() } }
    override suspend fun forParent(parentId: String): List<SubTask> = subTasks.forParent(parentId).map { it.toDomain() }
    override suspend fun all(): List<SubTask> = subTasks.all().map { it.toDomain() }
    override suspend fun upsert(subTask: SubTask) = subTasks.upsert(subTask.toEntity())
    override suspend fun delete(id: String) = subTasks.delete(id)
    override suspend fun deleteForParent(parentId: String) = subTasks.deleteForParent(parentId)
    override suspend fun insertAllIgnore(items: List<SubTask>): MergeReport {
        val rows = subTasks.insertAllIgnore(items.map { it.toEntity() })
        val inserted = rows.count { it >= 0 }
        return MergeReport(inserted = inserted, skipped = items.size - inserted)
    }
    override suspend fun insertAll(items: List<SubTask>) = subTasks.insertAll(items.map { it.toEntity() })

    override suspend fun captureAll(): TaskBundle = TaskBundle(
        tasks = tasks.all().map { it.toDomain() },
        habitRules = habits.all().map { it.toDomain() },
        goalRules = goals.all().map { it.toDomain() },
        subTasks = subTasks.all().map { it.toDomain() },
    )

    override suspend fun restoreAll(bundle: TaskBundle, overwrite: Boolean): MergeReport {
        val taskRows = if (overwrite) {
            tasks.upsertAll(bundle.tasks.map { it.toEntity() }); bundle.tasks.size
        } else {
            tasks.insertIgnore(bundle.tasks.map { it.toEntity() }).count { it >= 0 }
        }
        val habitRows = if (overwrite) {
            habits.upsertAll(bundle.habitRules.map { it.toEntity() }); bundle.habitRules.size
        } else {
            habits.insertIgnore(bundle.habitRules.map { it.toEntity() }).count { it >= 0 }
        }
        val goalRows = if (overwrite) {
            goals.upsertAll(bundle.goalRules.map { it.toEntity() }); bundle.goalRules.size
        } else {
            goals.insertIgnore(bundle.goalRules.map { it.toEntity() }).count { it >= 0 }
        }
        val subTaskRows = if (overwrite) {
            subTasks.insertAll(bundle.subTasks.map { it.toEntity() }); bundle.subTasks.size
        } else {
            subTasks.insertAllIgnore(bundle.subTasks.map { it.toEntity() }).count { it >= 0 }
        }
        val total = taskRows + habitRows + goalRows + subTaskRows
        val expected = bundle.tasks.size + bundle.habitRules.size + bundle.goalRules.size + bundle.subTasks.size
        return MergeReport(inserted = total, skipped = expected - total)
    }
}
