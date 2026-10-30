package com.u707t.tomatosprout

import com.u707t.tomatosprout.domain.GoalProgress
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.ui.tasks.TaskSort
import com.u707t.tomatosprout.ui.tasks.TodoCardUi
import com.u707t.tomatosprout.ui.tasks.sortCards
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSortTest {

    private fun card(
        id: String,
        priority: Int = 1,
        dueAt: Long? = null,
        sortOrder: Int = 0,
        done: Boolean = false,
        todayMinutes: Int = 0,
        todayChecks: Int = 0,
    ) = TodoCardUi(
        task = Task(
            id = id,
            type = TaskType.TODO,
            title = id,
            priority = priority,
            dueAt = dueAt,
            status = if (done) TaskStatus.DONE else TaskStatus.TODO,
            createdAt = 0L,
            updatedAt = 0L,
            sortOrder = sortOrder,
        ),
        timerLabel = "按次打卡",
        todayFocusCount = 0,
        todayFocusMinutes = todayMinutes,
        totalFocusCount = 0,
        totalFocusSeconds = 0L,
        weekMinutes = List(7) { 0 },
        subtaskDone = 0,
        subtaskTotal = 0,
        goal = null as GoalProgress?,
        todayCheckInCount = todayChecks,
    )

    @Test
    fun `manual keeps repository order`() {
        val cards = listOf(card("a", sortOrder = 2), card("b", sortOrder = 1))
        assertEquals(listOf("a", "b"), sortCards(cards, TaskSort.MANUAL).map { it.task.id })
    }

    @Test
    fun `priority puts urgent important first and keeps done at bottom`() {
        val cards = listOf(
            card("低", priority = 3, sortOrder = 1),
            card("高", priority = 0, sortOrder = 2),
            card("已完成", priority = 0, done = true),
            card("中", priority = 1, sortOrder = 3),
        )
        assertEquals(
            listOf("高", "中", "低", "已完成"),
            sortCards(cards, TaskSort.PRIORITY).map { it.task.id },
        )
    }

    @Test
    fun `due puts nearest deadline first and undated last`() {
        val cards = listOf(
            card("没截止", dueAt = null),
            card("晚", dueAt = 2_000L),
            card("早", dueAt = 1_000L),
        )
        assertEquals(
            listOf("早", "晚", "没截止"),
            sortCards(cards, TaskSort.DUE).map { it.task.id },
        )
    }

    @Test
    fun `focus ranks by today minutes then check count`() {
        val cards = listOf(
            card("零", todayMinutes = 0, todayChecks = 0),
            card("多", todayMinutes = 50, todayChecks = 1),
            card("次多", todayMinutes = 25, todayChecks = 3),
        )
        assertEquals(
            listOf("多", "次多", "零"),
            sortCards(cards, TaskSort.FOCUS).map { it.task.id },
        )
    }

    @Test
    fun `sort does not mutate the input list`() {
        val cards = listOf(card("b", sortOrder = 2), card("a", sortOrder = 1))
        sortCards(cards, TaskSort.PRIORITY)
        assertEquals(listOf("b", "a"), cards.map { it.task.id })
    }

    @Test
    fun `sort key round trips`() {
        TaskSort.entries.forEach { mode ->
            assertEquals(mode, TaskSort.of(mode.key))
        }
        assertEquals(TaskSort.DEFAULT, TaskSort.of("garbage"))
    }
}
