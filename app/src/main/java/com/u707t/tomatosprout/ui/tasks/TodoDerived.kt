package com.u707t.tomatosprout.ui.tasks

import com.u707t.tomatosprout.domain.TaskType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 首页分类筛选。 */
enum class TaskFilter(val label: String) {
    ALL("全部"),
    HABIT("习惯"),
    TODO("待办"),
    GOAL("目标"),
    POMODORO("番茄"),
}

/**
 * 截止时间的展示文案。
 *
 * 截止时间由编辑页统一写成「当天 23:59」，所以这里只显示日期粒度：
 * 逾期说天数、今明说今明、一周内说星期，更远才写月日。
 */
data class DueInfo(val text: String, val overdue: Boolean)

fun dueInfo(
    dueAt: Long?,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): DueInfo? {
    if (dueAt == null) return null
    val date = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0 -> DueInfo("逾期 ${-days} 天", overdue = true)
        days == 0L -> DueInfo("今天到期", overdue = false)
        days == 1L -> DueInfo("明天到期", overdue = false)
        days < 7 -> DueInfo("${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)}到期", overdue = false)
        else -> DueInfo("${date.monthValue} 月 ${date.dayOfMonth} 日", overdue = false)
    }
}

/** 分类 + 「隐藏已完成」的纯函数筛选，方便单测。 */
fun filterCards(
    cards: List<TodoCardUi>,
    filter: TaskFilter,
    hideDone: Boolean,
): List<TodoCardUi> {
    val typed = when (filter) {
        TaskFilter.ALL -> cards
        TaskFilter.HABIT -> cards.filter { it.task.type == TaskType.HABIT }
        TaskFilter.TODO -> cards.filter { it.task.type == TaskType.TODO }
        TaskFilter.GOAL -> cards.filter { it.task.type == TaskType.GOAL }
        TaskFilter.POMODORO -> cards.filter { it.task.type == TaskType.POMODORO }
    }
    return if (hideDone) typed.filterNot { it.isDone } else typed
}
