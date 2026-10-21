package com.u707t.tomatosprout.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.AppClock
import com.u707t.tomatosprout.domain.CheckInResult
import com.u707t.tomatosprout.domain.CheckInUseCase
import com.u707t.tomatosprout.domain.FocusRepository
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class CalendarRecordUi(
    val id: String,
    val kind: String,
    val taskId: String?,
    val title: String,
    val color: Long,
    val startLabel: String,
    val endLabel: String?,
    val status: String,
    val durationSeconds: Int,
    val plannedSeconds: Int,
    val note: String?,
    val mood: Int?,
)

data class CalendarHabitMissing(val taskId: String, val title: String, val icon: String?)

data class CalendarUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val recordDays: Set<LocalDate> = emptySet(),
    val dayCheckIns: Map<LocalDate, Int> = emptyMap(),
    val dayFocusSeconds: Map<LocalDate, Long> = emptyMap(),
    val records: List<CalendarRecordUi> = emptyList(),
    val missingHabits: List<CalendarHabitMissing> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val stats: StatsUseCase,
    private val checkIn: CheckInUseCase,
    private val focusRepo: FocusRepository,
    private val clock: AppClock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val taskFilter: String? = savedStateHandle.get<String>("taskId")
    private val initialDate = savedStateHandle.get<String>("date")
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: clock.today()
    private val selectedDate = MutableStateFlow(initialDate)
    private val month = MutableStateFlow(YearMonth.from(initialDate))
    private val statsState = stats.observe().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val ui: StateFlow<CalendarUiState> = combine(statsState, selectedDate, month) { data, date, selectedMonth ->
        if (data == null) return@combine CalendarUiState(loading = true)
        build(data, date, selectedMonth)
    }.catch { emit(CalendarUiState(loading = false, error = it.message ?: "日历加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    private fun build(data: StatsData, date: LocalDate, selectedMonth: YearMonth): CalendarUiState {
        val zone = ZoneId.systemDefault()
        val monthDays = data.days.filterKeys { YearMonth.from(it) == selectedMonth }
        val taskById = data.tasks.associateBy { it.id }

        val focusRecords = data.sessions
            .filter { Instant.ofEpochMilli(it.endedAt).atZone(zone).toLocalDate() == date }
            .map { session ->
                val start = Instant.ofEpochMilli(session.startedAt).atZone(zone)
                val end = Instant.ofEpochMilli(session.endedAt).atZone(zone)
                val title = session.taskId?.let { taskById[it]?.title } ?: "自由专注"
                CalendarRecordUi(
                    id = session.id,
                    kind = "focus",
                    taskId = session.taskId,
                    title = title,
                    color = session.taskId?.let { taskById[it]?.color } ?: 0xFFFF6B6B,
                    startLabel = start.format(DateTimeFormatter.ofPattern("HH:mm")),
                    endLabel = end.format(DateTimeFormatter.ofPattern("HH:mm")),
                    status = "已完成",
                    durationSeconds = session.actualSeconds,
                    plannedSeconds = session.plannedSeconds,
                    note = session.note,
                    mood = null,
                )
            }
        val checkRecords = data.checks
            .filter { taskFilter == null || it.taskId == taskFilter }
            .filter { it.date == date }
            .map { check ->
                val at = Instant.ofEpochMilli(check.createdAt).atZone(zone)
                CalendarRecordUi(
                    id = check.id,
                    kind = "checkin",
                    taskId = check.taskId,
                    title = taskById[check.taskId]?.title ?: "已删除的习惯",
                    color = taskById[check.taskId]?.color ?: 0xFF8FAE83,
                    startLabel = at.format(DateTimeFormatter.ofPattern("HH:mm")),
                    endLabel = null,
                    status = if (check.isMakeup) "补卡" else "已打卡",
                    durationSeconds = 0,
                    plannedSeconds = 0,
                    note = check.note,
                    mood = check.mood,
                )
            }
        val records = (focusRecords + checkRecords).sortedBy { it.startLabel }

        val missing = data.habits.filter { habit ->
            (taskFilter == null || habit.task.id == taskFilter) &&
            HabitSchedule.isScheduled(habit.rule, date) &&
                data.checks.none { it.taskId == habit.task.id && it.date == date } &&
                date.isBefore(data.today) &&
                !date.isBefore(data.today.minusDays(7))
        }.map { CalendarHabitMissing(it.task.id, it.task.title, it.task.icon) }

        return CalendarUiState(
            loading = false,
            today = data.today,
            month = selectedMonth,
            selectedDate = date,
            recordDays = data.days.keys,
            dayCheckIns = monthDays.mapValues { it.value.checkIns },
            dayFocusSeconds = monthDays.mapValues { it.value.focusSeconds },
            records = records,
            missingHabits = missing,
        )
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        month.value = YearMonth.from(date)
    }

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
        selectedDate.value = month.value.atDay(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
        selectedDate.value = month.value.atDay(1)
    }

    fun saveNote(record: CalendarRecordUi, note: String) {
        viewModelScope.launch {
            if (record.kind == "focus") {
                focusRepo.updateNote(record.id, note.takeIf { it.isNotBlank() })
            } else {
                checkIn.updateMeta(record.id, note.takeIf { it.isNotBlank() }, record.mood)
            }
            messages.emit("心得已保存")
        }
    }

    fun makeup(taskId: String) {
        val data = statsState.value ?: return
        val habit = data.habits.firstOrNull { it.task.id == taskId } ?: return
        val date = selectedDate.value
        viewModelScope.launch {
            when (val result = checkIn.makeup(habit.task, habit.rule, date)) {
                is CheckInResult.Checked -> messages.emit("补卡成功")
                is CheckInResult.Rejected -> messages.emit(result.reason)
                is CheckInResult.Undone -> messages.emit(result.message)
            }
        }
    }
}
