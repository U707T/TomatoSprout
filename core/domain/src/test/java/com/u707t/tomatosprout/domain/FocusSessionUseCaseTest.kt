package com.u707t.tomatosprout.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FocusSessionUseCaseTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 3, 2)

    private fun at(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun task(type: TaskType = TaskType.HABIT, id: String = "h1") = Task(
        id = id,
        type = type,
        title = "阅读",
        createdAt = 1L,
        updatedAt = 1L,
    )

    private inner class Fixture(
        val task: Task = task(),
        target: Int = 1,
    ) {
        val clock = FixedClock(at(day, 10, 0))
        val tasks = FakeTaskRepository()
        val checkIns = FakeCheckInRepository()
        val focus = FakeFocusRepository()
        var badgeRefreshes = 0
        val badges = BadgeRefresher {
            badgeRefreshes++
            emptyList()
        }
        val checkInUseCase: CheckInUseCase
        val useCase: FocusSessionUseCase

        init {
            tasks.items += task
            if (task.type == TaskType.HABIT) {
                tasks.habits[task.id] = HabitRule(
                    taskId = task.id,
                    frequency = Frequency.DAILY,
                    targetCountPerPeriod = target,
                    startDate = day.minusDays(30),
                )
            }
            checkInUseCase = CheckInUseCase(tasks, checkIns, badges, clock)
            useCase = FocusSessionUseCase(focus, tasks, checkInUseCase, badges, clock)
        }
    }

    private fun input(
        task: Task,
        start: Long,
        end: Long,
        actualSeconds: Int = 1500,
        reason: FocusEndReason = FocusEndReason.COUNTDOWN_FINISHED,
        strict: Boolean = false,
        midnightMode: Boolean = false,
        ignoreShort: Boolean = true,
        mode: String = "pomodoro",
    ) = FocusSettlementInput(
        task = task,
        mode = mode,
        plannedSeconds = 1500,
        actualSeconds = actualSeconds,
        startedAt = start,
        endedAt = end,
        interruptionCount = 0,
        strict = strict,
        midnightMode = midnightMode,
        ignoreShortFocus = ignoreShort,
        reason = reason,
    )

    @Test
    fun `countdown completion writes session and check-in with streak`() = runBlocking {
        val f = Fixture()
        val result = f.useCase.settle(input(f.task, at(day, 9, 0), at(day, 9, 25)))

        assertFalse(result.discarded)
        assertTrue(result.checkInWritten)
        assertEquals(1, result.streak)
        assertEquals(1, f.focus.items.size)
        assertEquals("completed", f.focus.items.single().outcome)
        assertEquals(day, f.focus.items.single().attributedDate)
        assertEquals(1, f.checkIns.items.size)
        assertEquals("timer", f.checkIns.items.single().source)
        assertEquals(day, f.checkIns.items.single().date)
        assertEquals(f.checkIns.items.single().id, f.focus.items.single().checkInId)
    }

    @Test
    fun `countup under 30 seconds is discarded when ignore is on`() = runBlocking {
        val f = Fixture()
        val result = f.useCase.settle(
            input(
                f.task,
                at(day, 9, 0),
                at(day, 9, 1),
                actualSeconds = 20,
                reason = FocusEndReason.COUNTUP_FINISHED,
            ),
        )
        assertTrue(result.discarded)
        assertTrue(f.focus.items.isEmpty())
        assertTrue(f.checkIns.items.isEmpty())
    }

    @Test
    fun `countup under 30 seconds is recorded when ignore is off`() = runBlocking {
        val f = Fixture()
        val result = f.useCase.settle(
            input(
                f.task,
                at(day, 9, 0),
                at(day, 9, 1),
                actualSeconds = 20,
                reason = FocusEndReason.COUNTUP_FINISHED,
                ignoreShort = false,
            ),
        )
        assertFalse(result.discarded)
        assertEquals(1, f.focus.items.size)
        assertEquals(1, f.checkIns.items.size)
    }

    @Test
    fun `manual stop records interrupted session without check-in`() = runBlocking {
        val f = Fixture()
        val result = f.useCase.settle(
            input(
                f.task,
                at(day, 9, 0),
                at(day, 9, 5),
                actualSeconds = 300,
                reason = FocusEndReason.MANUAL_STOP,
            ),
        )
        assertFalse(result.discarded)
        assertTrue(result.interrupted)
        assertFalse(result.checkInWritten)
        assertEquals("interrupted", f.focus.items.single().outcome)
        assertTrue(f.checkIns.items.isEmpty())
    }

    @Test
    fun `strict manual stop discards everything`() = runBlocking {
        val f = Fixture()
        val result = f.useCase.settle(
            input(
                f.task,
                at(day, 9, 0),
                at(day, 9, 5),
                actualSeconds = 300,
                reason = FocusEndReason.MANUAL_STOP,
                strict = true,
            ),
        )
        assertTrue(result.discarded)
        assertTrue(f.focus.items.isEmpty())
        assertTrue(f.checkIns.items.isEmpty())
    }

    @Test
    fun `cross midnight defaults to end date`() = runBlocking {
        val f = Fixture()
        val start = at(day, 23, 50)
        val end = at(day.plusDays(1), 0, 20)
        val result = f.useCase.settle(
            input(f.task, start, end, actualSeconds = 1800),
        )
        assertEquals(day.plusDays(1), result.attributedDate)
        assertEquals(day.plusDays(1), f.focus.items.single().attributedDate)
        assertEquals(day.plusDays(1), f.checkIns.items.single().date)
        assertTrue(f.focus.items.single().isMidnightShifted)
    }

    @Test
    fun `cross midnight with midnight mode uses start date`() = runBlocking {
        val f = Fixture()
        val start = at(day, 23, 50)
        val end = at(day.plusDays(1), 0, 20)
        val result = f.useCase.settle(
            input(f.task, start, end, actualSeconds = 1800, midnightMode = true),
        )
        assertEquals(day, result.attributedDate)
        assertEquals(day, f.focus.items.single().attributedDate)
        assertEquals(day, f.checkIns.items.single().date)
    }

    @Test
    fun `habit target accumulates count but keeps one record`() = runBlocking {
        val f = Fixture(target = 2)
        val first = f.useCase.settle(input(f.task, at(day, 9, 0), at(day, 9, 25)))
        val second = f.useCase.settle(input(f.task, at(day, 10, 0), at(day, 10, 25)))
        val third = f.useCase.settle(input(f.task, at(day, 11, 0), at(day, 11, 25)))

        assertTrue(first.checkInWritten)
        assertTrue(second.checkInWritten)
        assertFalse(third.checkInWritten)
        assertEquals(1, f.checkIns.items.size)
        assertEquals(2, f.checkIns.items.single().count)
        assertEquals(3, f.focus.items.size)
        assertEquals(1, second.streak)
    }

    @Test
    fun `countdown completion increments pomos for any task type`() = runBlocking {
        val pomodoro = Fixture(task = task(TaskType.POMODORO, id = "p1"))
        pomodoro.useCase.settle(input(pomodoro.task, at(day, 9, 0), at(day, 9, 25)))
        assertEquals(1, pomodoro.tasks.items.single().completedPomos)

        val todo = Fixture(task = task(TaskType.TODO, id = "t1"))
        todo.useCase.settle(input(todo.task, at(day, 9, 0), at(day, 9, 25)))
        assertEquals(1, todo.tasks.items.single().completedPomos)
        assertEquals(1, todo.checkIns.items.size)
    }

    @Test
    fun `countup completion adds focus time but no pomo`() = runBlocking {
        val f = Fixture(task = task(TaskType.POMODORO))
        val result = f.useCase.settle(
            input(
                f.task,
                at(day, 9, 0),
                at(day, 9, 20),
                actualSeconds = 1200,
                reason = FocusEndReason.COUNTUP_FINISHED,
                mode = "countup",
            ),
        )

        assertFalse(result.interrupted)
        assertEquals(0, f.tasks.items.single().completedPomos)
        assertEquals(1200, f.focus.items.single().actualSeconds)
    }

    @Test
    fun `countdown completion refreshes badges exactly once`() = runBlocking {
        val f = Fixture()
        f.useCase.settle(input(f.task, at(day, 9, 0), at(day, 9, 25)))

        // 打卡内部不再刷新；结算在 FocusSession 落库后统一刷新一次
        assertEquals(1, f.badgeRefreshes)
    }

    @Test
    fun `countdown recovered hours later is capped to planned seconds`() = runBlocking {
        val f = Fixture()
        val start = at(day, 9, 0)
        // 进程被杀 6 小时后才回到 App：elapsedRealtime 会把后台时间一起算进来
        val result = f.useCase.settle(
            input(f.task, start, at(day, 15, 25), actualSeconds = 6 * 3600 + 1500),
        )

        assertEquals(1500, result.session?.actualSeconds)
        assertEquals(start + 1500_000L, result.session?.endedAt)
        assertEquals(1500, f.focus.items.single().actualSeconds)
        assertEquals(start + 1500_000L, f.checkIns.items.single().createdAt)
    }

    // ---- fakes ----

    private class FixedClock(private val now: Long) : AppClock {
        override fun nowMillis(): Long = now
        override fun zone(): ZoneId = ZoneId.of("Asia/Shanghai")
    }

    private class FakeTaskRepository : TaskRepository {
        val items = mutableListOf<Task>()
        val habits = mutableMapOf<String, HabitRule>()
        val goals = mutableMapOf<String, GoalRule>()

        override fun observeActiveTasks(): Flow<List<Task>> = flowOf(items.filter { it.deletedAt == null })
        override fun observeAllTasks(): Flow<List<Task>> = flowOf(items)
        override fun observeHabitRules(): Flow<List<HabitRule>> = flowOf(habits.values.toList())
        override fun observeGoalRules(): Flow<List<GoalRule>> = flowOf(goals.values.toList())
        override suspend fun task(id: String): Task? = items.firstOrNull { it.id == id }
        override suspend fun tasksByParent(parentId: String): List<Task> = items.filter { it.parentId == parentId }
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

        override suspend fun incrementCompletedPomos(id: String, delta: Int, updatedAt: Long) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) {
                items[index] = items[index].copy(completedPomos = items[index].completedPomos + delta)
            }
        }

        override suspend fun recountCompletedPomos() = Unit

        override suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long) = Unit
        override suspend fun habitRule(taskId: String): HabitRule? = habits[taskId]
        override suspend fun goalRule(taskId: String): GoalRule? = goals[taskId]
        override suspend fun upsertHabitRule(rule: HabitRule) { habits[rule.taskId] = rule }
        override suspend fun upsertGoalRule(rule: GoalRule) { goals[rule.taskId] = rule }
        override suspend fun deleteHabitRule(taskId: String) { habits.remove(taskId) }
        override suspend fun deleteGoalRule(taskId: String) { goals.remove(taskId) }
        override suspend fun captureAll(): TaskBundle = TaskBundle(items.toList(), habits.values.toList(), goals.values.toList())
        override suspend fun restoreAll(bundle: TaskBundle, overwrite: Boolean): MergeReport = MergeReport()
    }

    private class FakeCheckInRepository : CheckInRepository {
        val items = mutableListOf<CheckIn>()

        override fun observeAll(): Flow<List<CheckIn>> = flowOf(items.filter { it.deletedAt == null })
        override suspend fun all(): List<CheckIn> = items.toList()
        override suspend fun forTask(taskId: String): List<CheckIn> = items.filter { it.taskId == taskId }

        override suspend fun insert(checkIn: CheckIn): Boolean {
            val exists = items.any {
                it.taskId == checkIn.taskId && it.date == checkIn.date && it.isMakeup == checkIn.isMakeup
            }
            if (exists) return false
            items += checkIn
            return true
        }

        override suspend fun updateCount(id: String, count: Int) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(count = count)
        }

        override suspend fun updateMeta(id: String, note: String?, mood: Int?) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(note = note, mood = mood)
        }

        override suspend fun softDelete(id: String, at: Long) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(deletedAt = at)
        }

        override suspend fun restoreAll(items: List<CheckIn>): MergeReport = MergeReport()
    }

    private class FakeFocusRepository : FocusRepository {
        val items = mutableListOf<FocusSession>()

        override fun observeAll(): Flow<List<FocusSession>> = flowOf(items.filter { it.deletedAt == null })
        override suspend fun all(): List<FocusSession> = items.toList()
        override suspend fun insert(session: FocusSession) { items += session }
        override suspend fun updateNote(id: String, note: String?) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(note = note)
        }

        override suspend fun softDelete(id: String, at: Long) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(deletedAt = at)
        }

        override suspend fun restoreAll(items: List<FocusSession>): MergeReport = MergeReport()
    }
}
