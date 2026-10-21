package com.u707t.tomatosprout.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.CheckInResult
import com.u707t.tomatosprout.domain.CheckInUseCase
import com.u707t.tomatosprout.domain.StatsUseCase
import com.u707t.tomatosprout.domain.SubTaskRepository
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.domain.TaskUseCase
import com.u707t.tomatosprout.domain.attributedLocalDate
import com.u707t.tomatosprout.ui.focus.FocusEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val useCase: TaskUseCase,
    private val stats: StatsUseCase,
    private val subTasks: SubTaskRepository,
    private val checkInUseCase: CheckInUseCase,
    focusEvents: FocusEventBus,
) : ViewModel() {
    private val quickId = MutableStateFlow<String?>(null)
    private val refreshTick = MutableStateFlow(0)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init {
        // 专注页结算后主动刷新首页，并给出一次性反馈（与 Room Flow 双保险）
        viewModelScope.launch {
            focusEvents.events.collect { event ->
                refreshTick.update { it + 1 }
                val minutes = max(1, event.actualSeconds / 60)
                messages.emit(
                    when {
                        event.discarded -> "本次专注未记录：${event.discardReason ?: "已丢弃"}"
                        event.interrupted -> "已记录中断 $minutes 分钟，不计入打卡"
                        event.checkInWritten && event.streak > 0 ->
                            "专注 $minutes 分钟 · 打卡成功 · 连续 ${event.streak} 天"
                        event.checkInWritten -> "专注 $minutes 分钟 · 打卡成功"
                        else -> "专注 $minutes 分钟已记录"
                    },
                )
            }
        }
    }

    val ui: StateFlow<TodoUiState> = combine(
        useCase.observeAll(),
        stats.observe(),
        subTasks.observeAll(),
        quickId,
        refreshTick,
    ) { allTasks, stat, allSubTasks, selectedId, _ ->
        val zone = ZoneId.systemDefault()
        val today = stat.today
        val visible = allTasks.filter { it.deletedAt == null && it.parentId == null }
        val active = visible.sortedWith(
            compareBy(
                { it.status == TaskStatus.DONE },
                { it.sortOrder },
                { it.priority },
                { it.createdAt },
            ),
        )
        val cards = active.map { task ->
            val taskSessions = stat.sessions.filter { it.taskId == task.id }
            val completedSessions = taskSessions.filter { it.completed }
            val todayCompleted = completedSessions.filter { it.attributedLocalDate(zone) == today }
            val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val weekMinutes = (0..6).map { offset ->
                val date = weekStart.plusDays(offset.toLong())
                completedSessions.filter { it.attributedLocalDate(zone) == date }
                    .sumOf { it.actualSeconds } / 60
            }
            val children = allSubTasks.filter { it.parentTaskId == task.id }
            val habit = stat.habits.firstOrNull { it.task.id == task.id }
            val todayChecks = stat.checks.filter { it.taskId == task.id && it.date == today }
            val checkTarget = max(1, habit?.rule?.targetCountPerPeriod ?: 1)
            val checkCount = todayChecks.sumOf { max(1, it.count) }
            TodoCardUi(
                task = task,
                timerLabel = when (task.timerMode) {
                    "none" -> "不计时 · 直接打卡"
                    "countup" -> "正向计时"
                    else -> "倒计时 · ${task.focusMinutes} 分钟"
                },
                todayFocusCount = todayCompleted.size,
                todayFocusMinutes = todayCompleted.sumOf { it.actualSeconds } / 60,
                totalFocusCount = completedSessions.size,
                totalFocusSeconds = completedSessions.sumOf { it.actualSeconds.toLong() },
                weekMinutes = weekMinutes,
                subtaskDone = children.count { it.done },
                subtaskTotal = children.size,
                goal = stat.goalProgress[task.id],
                checkedToday = if (task.type == TaskType.HABIT) {
                    checkCount >= checkTarget
                } else {
                    todayChecks.isNotEmpty()
                },
                todayCheckInCount = checkCount,
                habitTarget = checkTarget,
                streak = habit?.currentStreak ?: 0,
            )
        }
        val selected = cards.firstOrNull { it.task.id == selectedId }
        val selectedIndex = cards.indexOfFirst { it.task.id == selectedId }
        TodoUiState(
            loading = false,
            today = today,
            cards = cards,
            quickAction = selected?.let {
                QuickActionUiState(
                    taskId = it.task.id,
                    title = it.task.title,
                    icon = it.task.icon,
                    color = it.task.color,
                    todayFocusCount = it.todayFocusCount,
                    totalFocusCount = it.totalFocusCount,
                    totalFocusSeconds = it.totalFocusSeconds,
                    weekMinutes = it.weekMinutes,
                    canMoveUp = selectedIndex > 0,
                    canMoveDown = selectedIndex >= 0 && selectedIndex < cards.lastIndex,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoUiState())

    fun refresh() {
        refreshTick.update { it + 1 }
    }

    fun showQuickActions(taskId: String) {
        quickId.value = taskId
    }

    fun dismissQuickActions() {
        quickId.value = null
    }

    fun toggleCheckIn(task: Task) {
        viewModelScope.launch {
            when (val result = checkInUseCase.toggleToday(task)) {
                is CheckInResult.Checked ->
                    messages.emit(
                        if (result.streak > 0) "已打卡 · 连续 ${result.streak} 天" else "已打卡",
                    )
                is CheckInResult.Undone -> messages.emit(result.message)
                is CheckInResult.Rejected -> messages.emit(result.reason)
            }
        }
    }

    fun markDone(task: Task, done: Boolean) {
        viewModelScope.launch { useCase.setDone(task, done, System.currentTimeMillis()) }
    }

    fun cycleColor(task: Task) {
        val palette = listOf<Long>(
            0xFFFF6B6BL, 0xFFF3B58AL, 0xFFE5B95CL, 0xFF8FAE83L,
            0xFF7FB5B5L, 0xFF8C9ED6L, 0xFFC59ACFL,
        )
        val index = palette.indexOf(task.color).let { if (it < 0) 0 else it }
        val next = palette[(index + 1) % palette.size]
        viewModelScope.launch {
            useCase.updateTask(task.copy(color = next, updatedAt = System.currentTimeMillis()))
        }
    }

    fun move(taskId: String, delta: Int) {
        viewModelScope.launch { useCase.moveTask(taskId, delta, System.currentTimeMillis()) }
    }

    /**
     * 物理删除在 v1.6 中改为软删除：任务立即从首页消失，
     * 但 CheckIn / FocusSession 保留，历史页显示「[已删除] 原任务名」。
     */
    fun delete(task: Task) {
        viewModelScope.launch {
            useCase.delete(task, System.currentTimeMillis())
            quickId.value = null
            messages.emit("已删除「${task.title}」，历史记录已保留")
        }
    }
}
