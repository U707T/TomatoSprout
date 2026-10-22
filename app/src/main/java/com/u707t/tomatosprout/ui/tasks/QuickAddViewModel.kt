package com.u707t.tomatosprout.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.GoalRule
import com.u707t.tomatosprout.domain.HabitRule
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.domain.TaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val useCase: TaskUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(QuickAddUiState())
    val state: StateFlow<QuickAddUiState> = _state.asStateFlow()

    val created = MutableSharedFlow<Task>(extraBufferCapacity = 1)

    fun reset() = _state.update { QuickAddUiState() }
    fun setTitle(value: String) = _state.update { it.copy(title = value.take(60), error = null) }
    fun setType(value: QuickTaskType) = _state.update {
        val defaultTimer = when (value) {
            QuickTaskType.POMODORO -> QuickTimerMode.COUNTDOWN
            QuickTaskType.HABIT, QuickTaskType.GOAL, QuickTaskType.TODO -> QuickTimerMode.NONE
        }
        it.copy(type = value, timer = defaultTimer)
    }
    fun setTimer(value: QuickTimerMode) = _state.update { it.copy(timer = value) }
    fun setDuration(value: Int) = _state.update {
        it.copy(durationMinutes = value.coerceIn(5, 180))
    }
    fun clearError() = _state.update { it.copy(error = null) }

    fun create() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(error = "请输入待办名称") }
            return
        }
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val task = Task(
                id = id,
                type = s.type.taskType,
                title = s.title.trim(),
                note = null,
                color = when (s.type) {
                    QuickTaskType.POMODORO -> 0xFFFF6B6B
                    QuickTaskType.GOAL -> 0xFF8FAE83
                    QuickTaskType.HABIT -> 0xFF8C9ED6
                    QuickTaskType.TODO -> 0xFF7FB5B5
                },
                icon = when (s.type) {
                    QuickTaskType.POMODORO -> "🍅"
                    QuickTaskType.GOAL -> "🎯"
                    QuickTaskType.HABIT -> "🌱"
                    QuickTaskType.TODO -> "📝"
                },
                priority = 1,
                createdAt = now,
                updatedAt = now,
                timerMode = s.timer.storage,
                focusMinutes = s.durationMinutes,
            )
            val habit = if (s.type == QuickTaskType.HABIT) {
                HabitRule(taskId = id, startDate = LocalDate.now())
            } else null
            val goal = if (s.type == QuickTaskType.GOAL) {
                GoalRule(
                    taskId = id,
                    startDate = LocalDate.now(),
                    endDate = LocalDate.now().plusDays(30),
                    targetTotalCount = null,
                    targetTotalSeconds = null,
                )
            } else null
            runCatching { useCase.save(task, habit, goal, null) }
                .onFailure { e ->
                    _state.update { it.copy(saving = false, error = e.message ?: "创建失败") }
                    return@launch
                }
            _state.value = QuickAddUiState()
            created.emit(task)
        }
    }
}
