package com.u707t.tomatosprout.data.repo

import com.u707t.tomatosprout.data.db.GoalRuleDao
import com.u707t.tomatosprout.data.db.HabitRuleDao
import com.u707t.tomatosprout.data.db.TaskDao
import com.u707t.tomatosprout.data.db.toDomain
import com.u707t.tomatosprout.data.db.toEntity
import com.u707t.tomatosprout.domain.GoalRule
import com.u707t.tomatosprout.domain.HabitRule
import com.u707t.tomatosprout.domain.MergeReport
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
) : TaskRepository {
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

    override suspend fun habitRule(taskId: String): HabitRule? = habits.get(taskId)?.toDomain()
    override suspend fun goalRule(taskId: String): GoalRule? = goals.get(taskId)?.toDomain()
    override suspend fun upsertHabitRule(rule: HabitRule) = habits.upsert(rule.toEntity())
    override suspend fun upsertGoalRule(rule: GoalRule) = goals.upsert(rule.toEntity())
    override suspend fun deleteHabitRule(taskId: String) = habits.delete(taskId)
    override suspend fun deleteGoalRule(taskId: String) = goals.delete(taskId)

    override suspend fun captureAll(): TaskBundle = TaskBundle(
        tasks = tasks.all().map { it.toDomain() },
        habitRules = habits.all().map { it.toDomain() },
        goalRules = goals.all().map { it.toDomain() },
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
        val total = taskRows + habitRows + goalRows
        val expected = bundle.tasks.size + bundle.habitRules.size + bundle.goalRules.size
        return MergeReport(inserted = total, skipped = expected - total)
    }
}
