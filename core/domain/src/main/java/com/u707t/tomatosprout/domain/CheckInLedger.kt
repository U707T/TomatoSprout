package com.u707t.tomatosprout.domain

import java.time.LocalDate
import kotlin.math.max

/**
 * 打卡记录的只读索引：按 taskId + date 汇总次数。
 *
 * 旧实现里 `StreakCalculator.isMet` 每次都要 filter 一遍全量 CheckIn；
 * 统计一次会为每个习惯算 current / longest / 30 天完成率，再算 730 天圆满日，
 * 数据攒到几年后是 O(天数 × 习惯 × 记录) 的重复扫描。
 *
 * Ledger 只建一次索引，之后的达标判断都是 O(1)。
 * 软删除记录在这里统一忽略；同一任务同一天的多条记录按次数累加。
 */
class CheckInLedger(checkIns: List<CheckIn>) {
    private val counts: Map<String, Map<LocalDate, Int>>

    init {
        val byTask = HashMap<String, HashMap<LocalDate, Int>>()
        checkIns.forEach { check ->
            if (check.deletedAt != null) return@forEach
            val byDate = byTask.getOrPut(check.taskId) { HashMap() }
            byDate[check.date] = (byDate[check.date] ?: 0) + max(1, check.count)
        }
        counts = byTask
    }

    fun countOn(taskId: String, date: LocalDate): Int = counts[taskId]?.get(date) ?: 0

    /** 该任务最晚的一条打卡日期；用于兼容跨时区留下的“未来日期”记录。 */
    fun latestDate(taskId: String): LocalDate? = counts[taskId]?.keys?.maxOrNull()

    fun isMet(rule: HabitRule, date: LocalDate): Boolean =
        countOn(rule.taskId, date) >= max(1, rule.targetCountPerPeriod)
}
