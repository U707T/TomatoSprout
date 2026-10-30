package com.u707t.tomatosprout

import com.u707t.tomatosprout.domain.GoalProgress
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.ui.tasks.TaskFilter
import com.u707t.tomatosprout.ui.tasks.TodoCardUi
import com.u707t.tomatosprout.ui.tasks.dueInfo
import com.u707t.tomatosprout.ui.tasks.filterCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TodoDerivedTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 3, 10)

    private fun dueAt(date: LocalDate): Long =
        date.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

    private fun card(
        id: String,
        type: TaskType = TaskType.TODO,
        done: Boolean = false,
    ) = TodoCardUi(
        task = Task(
            id = id,
            type = type,
            title = id,
            status = if (done) TaskStatus.DONE else TaskStatus.TODO,
            createdAt = 0L,
            updatedAt = 0L,
        ),
        timerLabel = "按次打卡",
        todayFocusCount = 0,
        todayFocusMinutes = 0,
        totalFocusCount = 0,
        totalFocusSeconds = 0L,
        weekMinutes = List(7) { 0 },
        subtaskDone = 0,
        subtaskTotal = 0,
        goal = null as GoalProgress?,
    )

    @Test
    fun `no due date means no label`() {
        assertNull(dueInfo(null, today, zone))
    }

    @Test
    fun `overdue says how many days`() {
        val info = dueInfo(dueAt(today.minusDays(2)), today, zone)!!
        assertEquals("逾期 2 天", info.text)
        assertTrue(info.overdue)
    }

    @Test
    fun `today and tomorrow keep it human`() {
        assertEquals("今天到期", dueInfo(dueAt(today), today, zone)!!.text)
        assertEquals("明天到期", dueInfo(dueAt(today.plusDays(1)), today, zone)!!.text)
        assertTrue(!dueInfo(dueAt(today), today, zone)!!.overdue)
    }

    @Test
    fun `within a week shows weekday and far dates show month day`() {
        // 2026-03-10 是周二
        val weekday = dueInfo(dueAt(today.plusDays(3)), today, zone)!!.text
        assertTrue("实际：$weekday", weekday.endsWith("到期") && weekday.length <= 4)
        assertEquals("3 月 30 日", dueInfo(dueAt(today.plusDays(20)), today, zone)!!.text)
    }

    @Test
    fun `filter by type and hide done`() {
        val cards = listOf(
            card("a", TaskType.HABIT),
            card("b", TaskType.TODO),
            card("c", TaskType.TODO, done = true),
        )
        assertEquals(listOf("a"), filterCards(cards, TaskFilter.HABIT, false).map { it.task.id })
        assertEquals(listOf("b", "c"), filterCards(cards, TaskFilter.TODO, false).map { it.task.id })
        assertEquals(listOf("b"), filterCards(cards, TaskFilter.TODO, true).map { it.task.id })
        assertEquals(listOf("a", "b"), filterCards(cards, TaskFilter.ALL, true).map { it.task.id })
    }
}
