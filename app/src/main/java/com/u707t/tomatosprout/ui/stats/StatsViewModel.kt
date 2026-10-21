package com.u707t.tomatosprout.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.CheckInResult
import com.u707t.tomatosprout.domain.CheckInUseCase
import com.u707t.tomatosprout.domain.HabitSchedule
import com.u707t.tomatosprout.domain.StatsData
import com.u707t.tomatosprout.domain.StatsUseCase
import com.u707t.tomatosprout.domain.today
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.Instant
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
    private val checkIn: CheckInUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val initialTaskFilter: String? = savedStateHandle.get<String>("taskId")
    private val taskFilter: String? = initialTaskFilter
    private val initialDate = savedStateHandle.get<String>("date")
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: statsToday()
    private val filter = MutableStateFlow(
        FilterState(
            selectedDate = initialDate,
            range = DistributionRange.WEEK,
            customStart = initialDate.minusDays(13),
            customEnd = initialDate,
        ),
    )
    private val statsFlow = stats.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ui: StateFlow<StatsUiState> = combine(statsFlow, filter) { data, f ->
        if (data == null) return@combine StatsUiState.Loading
        if (data.habits.isEmpty() && data.days.isEmpty()) {
            StatsUiState.Empty(data.today) as StatsUiState
        } else {
            StatsUiState.Content(data, buildDashboard(data, f))
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

    private fun statsToday(): LocalDate = LocalDate.now()

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
        val zone = ZoneId.systemDefault()
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
        val taskById = data.tasks.associateBy { it.id }
        val inRange = data.sessions.filter {
            (taskFilter == null || it.taskId == taskFilter) &&
                Instant.ofEpochMilli(it.endedAt).atZone(zone).toLocalDate().let { d ->
                    !d.isBefore(rangeStart) && !d.isAfter(rangeEnd)
                }
        }
        val grouped = inRange.groupBy { it.taskId }
            .map { (taskId, sessions) ->
                ChartSlice(
                    label = taskId?.let { taskById[it]?.title } ?: "自由专注",
                    seconds = sessions.sumOf { it.actualSeconds.toLong() },
                    colorIndex = 0,
                    taskId = taskId,
                )
            }
            .sortedByDescending { it.seconds }
        val top = grouped.take(6)
        val rest = grouped.drop(6)
        val slices = if (rest.isEmpty()) {
            top
        } else {
            top + ChartSlice(
                label = "其他",
                seconds = rest.sumOf { it.seconds },
                colorIndex = 6,
                taskId = null,
            )
        }
        val colored = slices.mapIndexed { index, slice -> slice.copy(colorIndex = index) }
        val total = inRange.sumOf { it.actualSeconds.toLong() }
        val focusDays = data.sessions
            .map { Instant.ofEpochMilli(it.endedAt).atZone(zone).toLocalDate() }
            .distinct()
            .size
            .coerceAtLeast(1)
        val daily = data.days[selected]
        val monthDays = (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            MonthlyBar(
                day = day,
                seconds = data.days[date]?.focusSeconds ?: 0L,
                selected = date == selected,
            )
        }
        return StatsDashboard(
            selectedDate = selected,
            range = f.range,
            customStart = f.customStart,
            customEnd = f.customEnd,
            totalSeconds = total,
            totalFocusCount = inRange.size,
            avgDailySeconds = data.totalFocusSeconds / focusDays,
            dailyFocusCount = inRange.count {
                Instant.ofEpochMilli(it.endedAt).atZone(zone).toLocalDate() == selected
            },
            dailyFocusSeconds = daily?.focusSeconds ?: 0L,
            dailyCheckIns = daily?.checkIns ?: 0,
            slices = colored,
            month = month,
            bars = monthDays,
            barMaxSeconds = (monthDays.maxOfOrNull { it.seconds } ?: 0L).coerceAtLeast(1L),
        )
    }

    fun makeup(taskId: String, date: LocalDate) {
        val data = (ui.value as? StatsUiState.Content)?.data ?: return
        val habit = data.habits.firstOrNull { it.task.id == taskId } ?: return
        if (!HabitSchedule.isScheduled(habit.rule, date)) {
            messages.tryEmit("该日期不在排期中")
            return
        }
        viewModelScope.launch {
            when (val result = checkIn.makeup(habit.task, habit.rule, date)) {
                is CheckInResult.Checked -> messages.emit("补卡成功 · 当前连续 ${result.streak} 天")
                is CheckInResult.Rejected -> messages.emit(result.reason)
                is CheckInResult.Undone -> messages.emit(result.message)
            }
        }
    }
}
