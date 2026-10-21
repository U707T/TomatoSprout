package com.u707t.tomatosprout.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.GoalProgress
import com.u707t.tomatosprout.domain.StatsUseCase
import com.u707t.tomatosprout.domain.SubTaskRepository
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.domain.TaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val useCase: TaskUseCase,
    private val stats: StatsUseCase,
    private val subTasks: SubTaskRepository,
) : ViewModel() {
    private val quickId = MutableStateFlow<String?>(null)

    val ui: StateFlow<TodoUiState> = combine(
        useCase.observeAll(),
        stats.observe(),
        subTasks.observeAll(),
        quickId,
    ) { allTasks, stat, allSubTasks, selectedId ->
        val zone = ZoneId.systemDefault()
        val today = stat.today
        val visible = allTasks.filter { it.deletedAt == null && it.parentId == null }
        val active = visible.filter { it.status != TaskStatus.ARCHIVED }
            .sortedWith(compareBy({ it.status == TaskStatus.DONE }, { it.sortOrder }, { it.priority }, { it.createdAt }))
        val sessions = stat.sessions
        val cards = active.map { task ->
            val taskSessions = sessions.filter { it.taskId == task.id }
            val todaySessions = taskSessions.filter {
                Instant.ofEpochMilli(it.endedAt).atZone(zone).toLocalDate() == today
            }
            val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val weekMinutes = (0..6).map { offset ->
                val date = weekStart.plusDays(offset.toLong())
                taskSessions.filter {
                    Instant.ofEpochMilli(it.endedAt).atZone(zone).toLocalDate() == date
                }.sumOf { it.actualSeconds } / 60
            }
            val children = allSubTasks.filter { it.parentTaskId == task.id }
            TodoCardUi(
                task = task,
                timerLabel = when (task.timerMode) {
                    "none" -> "不计时"
                    "countup" -> "正向计时"
                    else -> "倒计时 · ${task.focusMinutes} 分钟"
                },
                todayFocusCount = todaySessions.size,
                totalFocusCount = taskSessions.size,
                totalFocusSeconds = taskSessions.sumOf { it.actualSeconds.toLong() },
                weekMinutes = weekMinutes,
                subtaskDone = children.count { it.done },
                subtaskTotal = children.size,
                goal = stat.goalProgress[task.id],
            )
        }
        val selected = cards.firstOrNull { it.task.id == selectedId }
        val selectedIndex = cards.indexOfFirst { it.task.id == selectedId }
        TodoUiState(
            loading = false,
            cards = cards,
            archivedCount = visible.count { it.status == TaskStatus.ARCHIVED },
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

    fun showQuickActions(taskId: String) {
        quickId.value = taskId
    }

    fun dismissQuickActions() {
        quickId.value = null
    }

    fun cycleColor(task: Task) {
        val palette = listOf<Long>(0xFFFF6B6BL, 0xFFF3B58AL, 0xFFE5B95CL, 0xFF8FAE83L, 0xFF7FB5B5L, 0xFF8C9ED6L, 0xFFC59ACFL)
        val index = palette.indexOf(task.color).let { if (it < 0) 0 else it }
        val next = palette[(index + 1) % palette.size]
        viewModelScope.launch { useCase.updateTask(task.copy(color = next, updatedAt = System.currentTimeMillis())) }
    }

    fun move(taskId: String, delta: Int) {
        viewModelScope.launch { useCase.moveTask(taskId, delta, System.currentTimeMillis()) }
    }

    fun markDone(task: Task, done: Boolean) {
        viewModelScope.launch { useCase.setDone(task, done, System.currentTimeMillis()) }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            useCase.delete(task, System.currentTimeMillis())
            quickId.value = null
        }
    }

    fun updateGoalProgress(taskId: String, progress: GoalProgress) {
        // 由统计流自动刷新，无需本地冗余
    }
}
