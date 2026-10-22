package com.u707t.tomatosprout.ui.stats

import java.time.LocalDate
import java.time.YearMonth

enum class DistributionRange(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    CUSTOM("自定义"),
}

data class ChartSlice(
    val label: String,
    val seconds: Long,
    val colorIndex: Int,
    val taskId: String?,
    val taskDeleted: Boolean = false,
)

data class MonthlyBar(
    val day: Int,
    val seconds: Long,
    val selected: Boolean,
)

data class HourBucket(
    val hour: Int,
    val seconds: Long,
)

data class StatsDashboard(
    val selectedDate: LocalDate,
    val range: DistributionRange,
    val customStart: LocalDate,
    val customEnd: LocalDate,
    val totalSeconds: Long,
    val totalFocusCount: Int,
    val avgDailySeconds: Long,
    val dailyFocusCount: Int,
    val dailyFocusSeconds: Long,
    val dailyCheckIns: Int,
    val slices: List<ChartSlice>,
    val month: YearMonth,
    val bars: List<MonthlyBar>,
    val barMaxSeconds: Long,
    val hasFocusData: Boolean,
    val hours: List<HourBucket> = emptyList(),
    val bestHour: Int? = null,
)
