package com.u707t.tomatosprout.ui.tasks

import com.u707t.tomatosprout.domain.TaskType

enum class QuickTaskType(val label: String, val taskType: TaskType) {
    POMODORO("普通番茄钟", TaskType.POMODORO),
    GOAL("定目标", TaskType.GOAL),
    HABIT("养习惯", TaskType.HABIT),
}

enum class QuickTimerMode(val label: String, val storage: String) {
    COUNTDOWN("倒计时", "countdown"),
    COUNTUP("正向计时", "countup"),
    NONE("不计时", "none"),
}

data class QuickAddUiState(
    val title: String = "",
    val type: QuickTaskType = QuickTaskType.POMODORO,
    val timer: QuickTimerMode = QuickTimerMode.COUNTDOWN,
    val durationMinutes: Int = 25,
    val customDuration: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)
