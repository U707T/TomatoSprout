package com.u707t.tomatosprout.ui.tasks

import com.u707t.tomatosprout.domain.GoalProgress
import com.u707t.tomatosprout.domain.Task

data class TodoCardUi(
    val task: Task,
    val timerLabel: String,
    val todayFocusCount: Int,
    val totalFocusCount: Int,
    val totalFocusSeconds: Long,
    val weekMinutes: List<Int>,
    val subtaskDone: Int,
    val subtaskTotal: Int,
    val goal: GoalProgress?,
)

data class QuickActionUiState(
    val taskId: String,
    val title: String,
    val icon: String?,
    val color: Long,
    val todayFocusCount: Int,
    val totalFocusCount: Int,
    val totalFocusSeconds: Long,
    val weekMinutes: List<Int>,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

data class TodoUiState(
    val loading: Boolean = true,
    val cards: List<TodoCardUi> = emptyList(),
    val archivedCount: Int = 0,
    val quickAction: QuickActionUiState? = null,
    val error: String? = null,
)
