package com.u707t.tomatosprout.ui.focus

import com.u707t.tomatosprout.domain.AppSettings
import com.u707t.tomatosprout.domain.Badge
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TimerState

data class SessionSummary(
    val minutes: Int,
    val taskTitle: String?,
    val newBadges: List<Badge>,
    val actualSeconds: Int = 0,
    val strictDiscarded: Boolean = false,
)

/** 正向计时不足 30 秒时先询问，默认不记录。 */
data class ShortFocusPrompt(
    val taskId: String?,
    val taskTitle: String?,
    val mode: String,
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val startedAt: Long,
    val endedAt: Long,
    val interruptionCount: Int,
)

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
    val shortPrompt: ShortFocusPrompt? = null,
    val error: String? = null,
    val keepScreenOn: Boolean = false,
    val brightness: Float = -1f,
    val immersive: Boolean = false,
    val audioTrack: FocusAudioOption = FocusAudioOption.NONE,
    val audioPlaying: Boolean = false,
)
