package com.u707t.tomatosprout.ui.tasks

import com.u707t.tomatosprout.domain.GoalProgress
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.domain.TaskType
import java.time.LocalDate

data class TodoCardUi(
    val task: Task,
    val timerLabel: String,
    val todayFocusCount: Int,
    val todayFocusMinutes: Int,
    val totalFocusCount: Int,
    val totalFocusSeconds: Long,
    val weekMinutes: List<Int>,
    val subtaskDone: Int,
    val subtaskTotal: Int,
    val goal: GoalProgress?,
    val checkedToday: Boolean = false,
    val todayCheckInCount: Int = 0,
    val habitTarget: Int = 1,
    val streak: Int = 0,
) {
    val isHabit: Boolean get() = task.type == TaskType.HABIT
    val isDone: Boolean get() = task.status == TaskStatus.DONE
    /** 不计时 = 按次，直接在首页打卡；倒计时/正向计时 = 计时任务，只显示「开始」。 */
    val isCountBased: Boolean get() = task.timerMode == "none"
    val isTimed: Boolean get() = !isCountBased
    val canFocus: Boolean get() = isTimed
    val partiallyChecked: Boolean get() = todayCheckInCount > 0 && !checkedToday
}

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
    val countBased: Boolean = false,
    val isDone: Boolean = false,
)

data class TodoUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val cards: List<TodoCardUi> = emptyList(),
    val quickAction: QuickActionUiState? = null,
    val error: String? = null,
)
