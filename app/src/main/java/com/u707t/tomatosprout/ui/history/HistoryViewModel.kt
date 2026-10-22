package com.u707t.tomatosprout.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.AppClock
import com.u707t.tomatosprout.domain.CheckInResult
import com.u707t.tomatosprout.domain.CheckInUseCase
import com.u707t.tomatosprout.domain.FocusRepository
import com.u707t.tomatosprout.domain.HabitSchedule
import com.u707t.tomatosprout.domain.InterruptReason
import com.u707t.tomatosprout.domain.StatsData
import com.u707t.tomatosprout.domain.StatsUseCase
import com.u707t.tomatosprout.domain.TaskDisplay
import com.u707t.tomatosprout.domain.attributedLocalDate
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
import kotlin.math.max

data class HistoryRecordUi(
    val id: String,
    val kind: String,
    /** 任务已删除时为 null，UI 不提供跳转/编辑入口。 */
    val taskId: String?,
    val title: String,
    val taskDeleted: Boolean,
    val color: Long,
    val startLabel: String,
    val endLabel: String?,
    val statusLabel: String,
    val durationSeconds: Int,
    val plannedSeconds: Int,
    val note: String?,
    val mood: Int?,
    val isMakeup: Boolean,
    val interrupted: Boolean,
    val interruptReason: String?,
    val atMillis: Long,
)

data class HistoryHabitUi(val taskId: String, val title: String, val icon: String?)

data class HistoryUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val recordDays: Set<LocalDate> = emptySet(),
    val dayCheckIns: Map<LocalDate, Int> = emptyMap(),
    val dayFocusSeconds: Map<LocalDate, Long> = emptyMap(),
    val records: List<HistoryRecordUi> = emptyList(),
    val missingHabits: List<HistoryHabitUi> = emptyList(),
    val canMakeup: Boolean = false,
    val error: String? = null,
)

/**
 * 二级历史页：月历 + 选中日时间轴。
 * 数据全部来自 StatsUseCase / CheckInUseCase，不再单独查询数据库。
 * 任务被删除后 CheckIn / FocusSession 仍然参与展示与统计，只是标题变为
 * 「[已删除] 原任务名」并置灰、不可点击。
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val stats: StatsUseCase,
    private val checkIn: CheckInUseCase,
    private val focusRepo: FocusRepository,
    private val clock: AppClock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val taskFilter: String? = savedStateHandle.get<String>("taskId")
    private val initialDate: LocalDate = savedStateHandle.get<String>("date")
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: clock.today()

    private val selectedDate = MutableStateFlow(initialDate)
    private val month = MutableStateFlow(YearMonth.from(initialDate))
    private val statsState = stats.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val ui: StateFlow<HistoryUiState> = combine(statsState, selectedDate, month) { data, date, selectedMonth ->
        if (data == null) {
            HistoryUiState(loading = true)
        } else {
            build(data, date, selectedMonth)
        }
    }
        .catch { emit(HistoryUiState(loading = false, error = it.message ?: "历史记录加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private fun build(data: StatsData, date: LocalDate, selectedMonth: YearMonth): HistoryUiState {
        val zone: ZoneId = clock.zone()
        val monthDays = data.days.filterKeys { YearMonth.from(it) == selectedMonth }
        val taskById = data.tasks.associateBy { it.id }

        val focusRecords = data.sessions
            .asSequence()
            .filter { it.actualSeconds >= 0 && it.endedAt > 0 }
            .filter { it.attributedLocalDate(zone) == date }
            .filter { taskFilter == null || it.taskId == taskFilter }
            .map { session ->
                val task = session.taskId?.let { taskById[it] }
                val deleted = session.taskId != null && TaskDisplay.isDeleted(task)
                val start = Instant.ofEpochMilli(session.startedAt).atZone(zone)
                val end = Instant.ofEpochMilli(session.endedAt).atZone(zone)
                val reasonLabel = session.interruptReason?.let { raw ->
                    runCatching { InterruptReason.valueOf(raw).label }.getOrDefault(raw)
                }
                HistoryRecordUi(
                    id = session.id,
                    kind = "focus",
                    taskId = session.taskId?.takeIf { !deleted },
                    title = if (session.taskId == null) "自由专注" else TaskDisplay.title(task),
                    taskDeleted = deleted,
                    color = task?.color ?: 0xFFFF6B6B,
                    startLabel = start.format(HISTORY_TIME),
                    endLabel = end.format(HISTORY_TIME),
                    statusLabel = when {
                        session.outcome != "interrupted" -> "已完成"
                        reasonLabel != null -> "中断 · $reasonLabel"
                        else -> "中断"
                    },
                    durationSeconds = session.actualSeconds,
                    plannedSeconds = session.plannedSeconds,
                    note = session.note,
                    mood = null,
                    isMakeup = false,
                    interrupted = session.outcome == "interrupted",
                    interruptReason = reasonLabel,
                    atMillis = session.endedAt,
                )
            }
            .toList()

        val checkRecords = data.checks
            .asSequence()
            .filter { it.date == date }
            .filter { taskFilter == null || it.taskId == taskFilter }
            .map { check ->
                val task = taskById[check.taskId]
                val deleted = TaskDisplay.isDeleted(task)
                val at = Instant.ofEpochMilli(check.createdAt).atZone(zone)
                HistoryRecordUi(
                    id = check.id,
                    kind = "checkin",
                    taskId = check.taskId.takeIf { !deleted },
                    title = TaskDisplay.title(task, fallback = "习惯"),
                    taskDeleted = deleted,
                    color = task?.color ?: 0xFF8FAE83,
                    startLabel = at.format(HISTORY_TIME),
                    endLabel = null,
                    statusLabel = if (check.isMakeup) "补卡" else "已打卡 ×${max(1, check.count)}",
                    durationSeconds = 0,
                    plannedSeconds = 0,
                    note = check.note,
                    mood = check.mood,
                    isMakeup = check.isMakeup,
                    interrupted = false,
                    interruptReason = null,
                    atMillis = check.createdAt,
                )
            }
            .toList()

        val records = (focusRecords + checkRecords).sortedBy { it.atMillis }

        val missing = data.habits
            .filter { habit ->
                (taskFilter == null || habit.task.id == taskFilter) &&
                    HabitSchedule.isScheduled(habit.rule, date) &&
                    data.checks.none { it.taskId == habit.task.id && it.date == date } &&
                    date.isBefore(data.today) &&
                    !date.isBefore(data.today.minusDays(7))
            }
            .map { HistoryHabitUi(it.task.id, it.task.title, it.task.icon) }

        return HistoryUiState(
            loading = false,
            today = data.today,
            month = selectedMonth,
            selectedDate = date,
            recordDays = data.days.keys,
            dayCheckIns = monthDays.mapValues { it.value.checkIns },
            dayFocusSeconds = monthDays.mapValues { it.value.focusSeconds },
            records = records,
            missingHabits = missing,
            canMakeup = missing.isNotEmpty(),
        )
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        month.value = YearMonth.from(date)
    }

    fun previousMonth() {
        val next = month.value.minusMonths(1)
        month.value = next
        selectedDate.value = next.atDay(1)
    }

    fun nextMonth() {
        val next = month.value.plusMonths(1)
        month.value = next
        selectedDate.value = next.atDay(1)
    }

    fun goToday() {
        selectDate(clock.today())
    }

    fun saveNote(record: HistoryRecordUi, note: String) {
        if (record.taskDeleted) {
            viewModelScope.launch { messages.emit("任务已删除，历史记录只读") }
            return
        }
        viewModelScope.launch {
            runCatching {
                if (record.kind == "focus") {
                    focusRepo.updateNote(record.id, note.takeIf { it.isNotBlank() }, clock.nowMillis())
                } else {
                    checkIn.updateMeta(record.id, note.takeIf { it.isNotBlank() }, record.mood)
                }
            }.onSuccess { messages.emit("心得已保存") }
                .onFailure { messages.emit(it.message ?: "保存失败") }
        }
    }

    fun makeup(taskId: String) {
        val data = statsState.value ?: return
        val habit = data.habits.firstOrNull { it.task.id == taskId } ?: return
        val date = selectedDate.value
        viewModelScope.launch {
            when (val result = checkIn.makeup(habit.task, habit.rule, date)) {
                is CheckInResult.Checked ->
                    messages.emit(
                        if (result.streak > 0) "补卡成功 · 连续 ${result.streak} 天" else "补卡成功",
                    )
                is CheckInResult.Rejected -> messages.emit(result.reason)
                is CheckInResult.Undone -> messages.emit(result.message)
            }
        }
    }

    private companion object {
        val HISTORY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
