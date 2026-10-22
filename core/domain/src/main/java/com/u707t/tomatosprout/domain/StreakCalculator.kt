package com.u707t.tomatosprout.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

object HabitSchedule {
    /** weekdaysMask 的 bit0 = 周一 … bit6 = 周日。 */
    fun isScheduled(rule: HabitRule, date: LocalDate): Boolean {
        if (date.isBefore(rule.startDate)) return false
        return when (rule.frequency) {
            Frequency.DAILY -> true
            Frequency.WEEKDAYS -> date.dayOfWeek.value <= 5
            Frequency.WEEKLY -> {
                val mask = rule.weekdaysMask
                if (mask == 0) true else (mask shr (date.dayOfWeek.value - 1)) and 1 == 1
            }
            Frequency.CUSTOM -> {
                val interval = max(1, rule.intervalDays)
                (ChronoUnit.DAYS.between(rule.startDate, date) % interval).toInt() == 0
            }
        }
    }

    fun datesBetween(rule: HabitRule, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        val start = maxOf(from, rule.startDate)
        if (to.isBefore(start)) return emptyList()
        val out = ArrayList<LocalDate>()
        var cursor = start
        while (!cursor.isAfter(to) && out.size < 20_000) {
            if (isScheduled(rule, cursor)) out += cursor
            cursor = cursor.plusDays(1)
        }
        return out
    }

    fun maskOf(vararg days: DayOfWeek): Int =
        days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

    fun maskLabels(mask: Int): String {
        val names = listOf("一", "二", "三", "四", "五", "六", "日")
        val on = names.filterIndexed { index, _ -> (mask shr index) and 1 == 1 }
        return when {
            mask == 0b1111111 -> "每天"
            mask == 0b0011111 -> "工作日"
            mask == 0 -> "未选择"
            else -> "周" + on.joinToString("、")
        }
    }
}

object StreakCalculator {
    fun countOn(checkIns: List<CheckIn>, taskId: String, date: LocalDate): Int =
        CheckInLedger(checkIns).countOn(taskId, date)

    fun isMet(rule: HabitRule, checkIns: List<CheckIn>, date: LocalDate): Boolean =
        CheckInLedger(checkIns).isMet(rule, date)

    fun isMet(rule: HabitRule, ledger: CheckInLedger, date: LocalDate): Boolean =
        ledger.isMet(rule, date)

    /**
     * 当前连续天数：
     * - 今天已排期但还没打卡时不算中断（今天还有时间）；
     * - 未排期的日子自动跳过，不打断连续；
     * - 补卡与正常打卡等价计数；
     * - 重复记录按日期合并计数，不重复累计天数。
     */
    fun currentStreak(rule: HabitRule, checkIns: List<CheckIn>, today: LocalDate): Int =
        currentStreak(rule, CheckInLedger(checkIns), today)

    fun currentStreak(rule: HabitRule, ledger: CheckInLedger, today: LocalDate): Int {
        if (today.isBefore(rule.startDate)) return 0
        // 跨时区旅行可能留下“未来日期”的记录；以最晚记录为基准，避免被误判为中断
        val effective = ledger.latestDate(rule.taskId)?.let { maxOf(today, it) } ?: today
        var cursor = effective
        if (HabitSchedule.isScheduled(rule, effective) && !ledger.isMet(rule, effective)) {
            cursor = effective.minusDays(1)
        }
        var streak = 0
        var guard = 0
        while (!cursor.isBefore(rule.startDate) && guard < 20_000) {
            if (HabitSchedule.isScheduled(rule, cursor)) {
                if (ledger.isMet(rule, cursor)) streak++ else break
            }
            cursor = cursor.minusDays(1)
            guard++
        }
        return streak
    }

    fun longestStreak(rule: HabitRule, checkIns: List<CheckIn>, today: LocalDate): Int =
        longestStreak(rule, CheckInLedger(checkIns), today)

    fun longestStreak(rule: HabitRule, ledger: CheckInLedger, today: LocalDate): Int {
        if (today.isBefore(rule.startDate)) return 0
        val effective = ledger.latestDate(rule.taskId)?.let { maxOf(today, it) } ?: today
        var best = 0
        var run = 0
        for (date in HabitSchedule.datesBetween(rule, rule.startDate, effective)) {
            if (ledger.isMet(rule, date)) {
                run++
                best = max(best, run)
            } else {
                run = 0
            }
        }
        return best
    }

    /** 完成率：排期日中达成目标的比例；无排期返回 0。 */
    fun completionRate(
        rule: HabitRule,
        checkIns: List<CheckIn>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
    ): Float = completionRate(rule, CheckInLedger(checkIns), from, to, today)

    fun completionRate(
        rule: HabitRule,
        ledger: CheckInLedger,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
    ): Float {
        val end = minOf(to, today)
        val dates = HabitSchedule.datesBetween(rule, from, end)
        if (dates.isEmpty()) return 0f
        val done = dates.count { ledger.isMet(rule, it) }
        return done.toFloat() / dates.size
    }
}
