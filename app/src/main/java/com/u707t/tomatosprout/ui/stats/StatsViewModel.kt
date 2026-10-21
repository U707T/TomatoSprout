package com.u707t.tomatosprout.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.AppClock
import com.u707t.tomatosprout.domain.StatsData
import com.u707t.tomatosprout.domain.StatsUseCase
import com.u707t.tomatosprout.domain.TaskDisplay
import com.u707t.tomatosprout.domain.attributedLocalDate
import com.u707t.tomatosprout.domain.today
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Empty(val today: LocalDate) : StatsUiState
    data class Content(val data: StatsData, val dashboard: StatsDashboard) : StatsUiState
    data class Error(val message: String) : StatsUiState
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val stats: StatsUseCase,
    private val clock: AppClock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val initialTaskFilter: String? = savedStateHandle.get<String>("taskId")
    private val taskFilter: String? = initialTaskFilter
    private val initialDate = savedStateHandle.get<String>("date")
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: clock.today()

    private val filter = MutableStateFlow(
        FilterState(
            selectedDate = initialDate,
            range = DistributionRange.WEEK,
            customStart = initialDate.minusDays(13),
            customEnd = initialDate,
        ),
    )

    private val statsFlow = stats.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ui: StateFlow<StatsUiState> = combine(statsFlow, filter) { data, f ->
        when {
            data == null -> StatsUiState.Loading
            data.habits.isEmpty() && data.days.isEmpty() && data.sessions.isEmpty() ->
                StatsUiState.Empty(data.today)
            else -> StatsUiState.Content(data, buildDashboard(data, f))
        }
    }
        .catch { emit(StatsUiState.Error(it.message ?: "统计加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState.Loading)

    private data class FilterState(
        val selectedDate: LocalDate,
        val range: DistributionRange,
        val customStart: LocalDate,
        val customEnd: LocalDate,
    )

    fun selectDate(date: LocalDate) {
        filter.value = filter.value.copy(selectedDate = date)
    }

    fun shiftDate(delta: Long) {
        filter.value = filter.value.copy(selectedDate = filter.value.selectedDate.plusDays(delta))
    }

    fun setRange(range: DistributionRange) {
        filter.value = filter.value.copy(range = range)
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        filter.value = filter.value.copy(
            range = DistributionRange.CUSTOM,
            customStart = minOf(start, end),
            customEnd = maxOf(start, end),
        )
    }

    fun selectBar(day: Int) {
        val f = filter.value
        val month = YearMonth.from(f.selectedDate)
        if (day in 1..month.lengthOfMonth()) {
            filter.value = f.copy(selectedDate = month.atDay(day), range = DistributionRange.DAY)
        }
    }

    fun previousMonth() {
        val month = YearMonth.from(filter.value.selectedDate).minusMonths(1)
        filter.value = filter.value.copy(selectedDate = month.atDay(1))
    }

    fun nextMonth() {
        val month = YearMonth.from(filter.value.selectedDate).plusMonths(1)
        filter.value = filter.value.copy(selectedDate = month.atDay(1))
    }

    private fun buildDashboard(data: StatsData, f: FilterState): StatsDashboard {
        val zone: ZoneId = clock.zone()
        val selected = f.selectedDate
        val month = YearMonth.from(selected)
        val weekStart = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)
        val rangeStart = when (f.range) {
            DistributionRange.DAY -> selected
            DistributionRange.WEEK -> weekStart
            DistributionRange.MONTH -> month.atDay(1)
            DistributionRange.CUSTOM -> f.customStart
        }
        val rangeEnd = when (f.range) {
            DistributionRange.DAY -> selected
            DistributionRange.WEEK -> weekEnd
            DistributionRange.MONTH -> month.atEndOfMonth()
            DistributionRange.CUSTOM -> f.customEnd
        }

        // 第一道数据防线：过滤 duration < 0、结束时间非法的脏数据
        val safeSessions = data.sessions.filter { it.actualSeconds >= 0 && it.endedAt > 0 }
        val taskById = data.tasks.associateBy { it.id }
        val inRange = safeSessions.filter {
            (taskFilter == null || it.taskId == taskFilter) &&
                it.attributedLocalDate(zone).let { d -> !d.isBefore(rangeStart) && !d.isAfter(rangeEnd) }
        }

        val grouped = inRange.groupBy { it.taskId }
            .map { (taskId, sessions) ->
                val task = taskId?.let { taskById[it] }
                val deleted = taskId != null && TaskDisplay.isDeleted(task)
                ChartSlice(
                    label = if (taskId == null) "自由专注" else TaskDisplay.title(task),
                    seconds = sessions.sumOf { it.actualSeconds.toLong() }.coerceAtLeast(0L),
                    colorIndex = 0,
                    taskId = taskId,
                    taskDeleted = deleted,
                )
            }
            .filter { it.seconds > 0 }
            .sortedByDescending { it.seconds }

        val top = grouped.take(6)
        val rest = grouped.drop(6)
        val slices = if (rest.isEmpty()) {
            top
        } else {
            top + ChartSlice(
                label = "其他",
                seconds = rest.sumOf { it.seconds }.coerceAtLeast(0L),
                colorIndex = 6,
                taskId = null,
            )
        }
        val colored = slices.mapIndexed { index, slice -> slice.copy(colorIndex = index) }
        val total = inRange.sumOf { it.actualSeconds.toLong() }.coerceAtLeast(0L)
        val focusDays = safeSessions
            .map { it.attributedLocalDate(zone) }
            .distinct()
            .size
            .coerceAtLeast(1)
        val daily = data.days[selected]
        val monthDays = (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            MonthlyBar(
                day = day,
                seconds = (data.days[date]?.focusSeconds ?: 0L).coerceAtLeast(0L),
                selected = date == selected,
            )
        }
        val barMax = (monthDays.maxOfOrNull { it.seconds } ?: 0L).coerceAtLeast(1L)
        return StatsDashboard(
            selectedDate = selected,
            range = f.range,
            customStart = f.customStart,
            customEnd = f.customEnd,
            totalSeconds = total,
            totalFocusCount = inRange.size,
            avgDailySeconds = if (data.totalFocusSeconds <= 0) 0L else data.totalFocusSeconds / focusDays,
            dailyFocusCount = inRange.count { it.attributedLocalDate(zone) == selected },
            dailyFocusSeconds = (daily?.focusSeconds ?: 0L).coerceAtLeast(0L),
            dailyCheckIns = (daily?.checkIns ?: 0).coerceAtLeast(0),
            slices = colored,
            month = month,
            bars = monthDays,
            barMaxSeconds = barMax,
            hasFocusData = total > 0 && colored.isNotEmpty(),
        )
    }
}
