package com.u707t.tomatosprout.ui.focus

import com.u707t.tomatosprout.domain.AppSettings
import com.u707t.tomatosprout.domain.Badge
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TimerState
import java.time.LocalDate

data class SessionSummary(
    val minutes: Int,
    val taskTitle: String?,
    val newBadges: List<Badge>,
    val actualSeconds: Int = 0,
    val discarded: Boolean = false,
    val discardReason: String? = null,
    val checkInWritten: Boolean = false,
    val streak: Int = 0,
    val interrupted: Boolean = false,
    val isBreak: Boolean = false,
    val attributedDate: LocalDate? = null,
    val crossDay: Boolean = false,
)

/** 正向计时结束后询问「是否记录」；倒计时自然结束不会出现。 */
data class RecordPrompt(
    val taskId: String?,
    val taskTitle: String?,
    val mode: String,
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val startedAt: Long,
    val endedAt: Long,
    val interruptionCount: Int,
)

/** 手动停止倒计时前的拦截确认。 */
data class StopConfirm(
    val actualSeconds: Int,
    val plannedSeconds: Int,
    val strict: Boolean,
) {
    val remainingSeconds: Int get() = (plannedSeconds - actualSeconds).coerceAtLeast(0)
}

data class FocusUiState(
    val loading: Boolean = true,
    val timer: TimerState? = null,
    val nowMono: Long = 0L,
    val tasks: List<Task> = emptyList(),
    val selectedTaskId: String? = null,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val settings: AppSettings = AppSettings(),
    val summary: SessionSummary? = null,
    val recordPrompt: RecordPrompt? = null,
    val stopConfirm: StopConfirm? = null,
    val error: String? = null,
    val keepScreenOn: Boolean = false,
    val brightness: Float = -1f,
    val immersive: Boolean = false,
    val audioTrack: FocusAudioOption = FocusAudioOption.NONE,
    val audioPlaying: Boolean = false,
) {
    val isRunning: Boolean get() = timer?.isActive == true
    val selectedTask: Task? get() = tasks.firstOrNull { it.id == selectedTaskId }
}
