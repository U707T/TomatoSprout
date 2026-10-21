package com.u707t.tomatosprout.ui.focus

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** 专注结算完成后广播给首页，用于刷新卡片并给出一次性提示。 */
data class FocusSettledEvent(
    val taskId: String?,
    val taskTitle: String?,
    val actualSeconds: Int,
    val checkInWritten: Boolean,
    val streak: Int,
    val interrupted: Boolean,
    val discarded: Boolean,
    val discardReason: String?,
    val attributedDate: LocalDate,
)

@Singleton
class FocusEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<FocusSettledEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<FocusSettledEvent> = _events.asSharedFlow()

    fun emit(event: FocusSettledEvent) {
        _events.tryEmit(event)
    }
}
