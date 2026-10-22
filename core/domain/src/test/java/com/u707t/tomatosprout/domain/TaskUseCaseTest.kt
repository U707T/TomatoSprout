package com.u707t.tomatosprout.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskUseCaseTest {
    private fun task(id: String, sortOrder: Int = 0, createdAt: Long = 1L) = Task(
        id = id,
        type = TaskType.TODO,
        title = id,
        createdAt = createdAt,
        updatedAt = createdAt,
        sortOrder = sortOrder,
    )

    private fun orderOf(repo: FakeTaskRepository): List<String> =
        repo.items.sortedWith(compareBy({ it.sortOrder }, { it.priority }, { it.createdAt })).map { it.id }

    @Test
    fun `move up works when every sortOrder is zero`() = runBlocking {
        val repo = FakeTaskRepository(listOf(task("a"), task("b", createdAt = 2), task("c", createdAt = 3)))
        TaskUseCase(repo, FakeReminderRepository(), FakeSubTaskRepository())
            .moveTask("b", -1, 10L)

        assertEquals(listOf("b", "a", "c"), orderOf(repo))
    }

    @Test
    fun `move works when duplicate sortOrders exist`() = runBlocking {
        // 旧的“全部为 0 才归一化”逻辑在这里会静默失败：a/b 顺序相同，交换后仍是原值
        val repo = FakeTaskRepository(
            listOf(
                task("a", sortOrder = 0),
                task("b", sortOrder = 0, createdAt = 2),
                task("c", sortOrder = 5, createdAt = 3),
            ),
        )
        TaskUseCase(repo, FakeReminderRepository(), FakeSubTaskRepository())
            .moveTask("b", -1, 10L)

        assertEquals(listOf("b", "a", "c"), orderOf(repo))
        assertEquals(listOf(1, 2, 3), repo.items.sortedBy { it.sortOrder }.map { it.sortOrder })
    }

    @Test
    fun `move outside the list is a no-op`() = runBlocking {
        val repo = FakeTaskRepository(listOf(task("a"), task("b", createdAt = 2)))
        val before = orderOf(repo)
        TaskUseCase(repo, FakeReminderRepository(), FakeSubTaskRepository())
            .moveTask("a", -1, 10L)
        assertEquals(before, orderOf(repo))
    }

    @Test
    fun `subtask add stamps updatedAt and delete keeps a tombstone`() = runBlocking {
        val taskRepo = FakeTaskRepository(listOf(task("p1")))
        val subRepo = FakeSubTaskRepository()
        val useCase = TaskUseCase(taskRepo, FakeReminderRepository(), subRepo)

        val child = useCase.addSubtask("p1", "子项", at = 5L)
        assertEquals(5L, child.updatedAt)
        assertEquals(1, subRepo.forParent("p1").size)

        useCase.deleteSubtask(child)
        // 行还在（墓碑），只是不再出现在父任务的子任务列表里
        assertEquals(1, subRepo.items.size)
        assertNotNull(subRepo.items.single().deletedAt)
        assertTrue(subRepo.forParent("p1").isEmpty())
    }

    private class FakeTaskRepository(initial: List<Task>) : TaskRepository {
        val items = initial.toMutableList()

        override fun observeActiveTasks(): Flow<List<Task>> = flowOf(items.filter { it.deletedAt == null })
        override fun observeAllTasks(): Flow<List<Task>> = flowOf(items.toList())
        override fun observeHabitRules(): Flow<List<HabitRule>> = flowOf(emptyList())
        override fun observeGoalRules(): Flow<List<GoalRule>> = flowOf(emptyList())
        override suspend fun task(id: String): Task? = items.firstOrNull { it.id == id }
        override suspend fun tasksByParent(parentId: String): List<Task> = emptyList()
        override suspend fun upsert(task: Task) {
            items.removeAll { it.id == task.id }
            items += task
        }

        override suspend fun setStatus(id: String, status: TaskStatus, updatedAt: Long) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(status = status, updatedAt = updatedAt)
        }

        override suspend fun softDelete(id: String, updatedAt: Long) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(deletedAt = updatedAt)
        }

        override suspend fun incrementCompletedPomos(id: String, delta: Int, updatedAt: Long) = Unit
        override suspend fun recountCompletedPomos() = Unit

        override suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(sortOrder = sortOrder, updatedAt = updatedAt)
        }

        override suspend fun habitRule(taskId: String): HabitRule? = null
        override suspend fun goalRule(taskId: String): GoalRule? = null
        override suspend fun upsertHabitRule(rule: HabitRule) = Unit
        override suspend fun upsertGoalRule(rule: GoalRule) = Unit
        override suspend fun deleteHabitRule(taskId: String) = Unit
        override suspend fun deleteGoalRule(taskId: String) = Unit
        override suspend fun captureAll(): TaskBundle = TaskBundle(tasks = items.toList())
        override suspend fun restoreAll(bundle: TaskBundle, overwrite: Boolean): MergeReport = MergeReport()
    }

    private class FakeReminderRepository : ReminderRepository {
        override fun observeAll(): Flow<List<ReminderRule>> = flowOf(emptyList())
        override suspend fun all(): List<ReminderRule> = emptyList()
        override suspend fun forTask(taskId: String): ReminderRule? = null
        override suspend fun upsert(rule: ReminderRule) = Unit
        override suspend fun deleteForTask(taskId: String) = Unit
        override suspend fun mergeAll(items: List<ReminderRule>): MergeReport = MergeReport()
    }

    private class FakeSubTaskRepository : SubTaskRepository {
        val items = mutableListOf<SubTask>()
        private fun active() = items.filter { it.deletedAt == null }

        override fun observeAll(): Flow<List<SubTask>> = flowOf(active())
        override fun observeForParent(parentId: String): Flow<List<SubTask>> =
            flowOf(active().filter { it.parentTaskId == parentId })
        override suspend fun forParent(parentId: String): List<SubTask> =
            active().filter { it.parentTaskId == parentId }
        override suspend fun all(): List<SubTask> = items.toList()
        override suspend fun upsert(subTask: SubTask) {
            items.removeAll { it.id == subTask.id }
            items += subTask
        }

        override suspend fun delete(id: String, at: Long) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(deletedAt = at, updatedAt = at)
        }

        override suspend fun deleteForParent(parentId: String, at: Long) {
            items.replaceAll {
                if (it.parentTaskId == parentId && it.deletedAt == null) {
                    it.copy(deletedAt = at, updatedAt = at)
                } else {
                    it
                }
            }
        }

        override suspend fun insertAllIgnore(items: List<SubTask>): MergeReport {
            items.filter { draft -> this.items.none { it.id == draft.id } }.forEach { this.items += it }
            return MergeReport()
        }

        override suspend fun insertAll(items: List<SubTask>) {
            items.forEach { draft ->
                this.items.removeAll { it.id == draft.id }
                this.items += draft
            }
        }

        override suspend fun mergeAll(items: List<SubTask>): MergeReport {
            items.forEach { remote ->
                val index = this.items.indexOfFirst { it.id == remote.id }
                if (index < 0) {
                    this.items += remote
                } else if (remote.updatedAt > this.items[index].updatedAt) {
                    this.items[index] = remote
                }
            }
            return MergeReport()
        }
    }
}
