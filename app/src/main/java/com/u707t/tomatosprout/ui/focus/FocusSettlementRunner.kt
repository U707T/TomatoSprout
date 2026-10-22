package com.u707t.tomatosprout.ui.focus

import com.u707t.tomatosprout.domain.AppSettings
import com.u707t.tomatosprout.domain.BreakCycle
import com.u707t.tomatosprout.domain.FocusEndReason
import com.u707t.tomatosprout.domain.FocusRepository
import com.u707t.tomatosprout.domain.FocusSessionUseCase
import com.u707t.tomatosprout.domain.FocusSettlementInput
import com.u707t.tomatosprout.domain.InterruptReason
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 专注结算：写入 FocusSession、自动打卡、长休息判断与一次性摘要。
 * 与计时 / 偏好 / 页面状态解耦，ViewModel 只负责准备输入与展示回调。
 *
 * 写库挂在应用级作用域上，用户结算后立刻退出页面也不会中断。
 */
class FocusSettlementRunner(
    private val appScope: CoroutineScope,
    private val useCase: FocusSessionUseCase,
    private val focusRepo: FocusRepository,
    private val events: FocusEventBus,
    private val onSummary: (SessionSummary) -> Unit,
    private val onError: (String) -> Unit,
) {
    @Volatile
    private var generation = 0

    /** 开始新一轮专注 / 重置时调用，旧结算结果不再覆盖新界面。 */
    fun beginRound() {
        generation++
    }

    fun settle(
        stopped: TimerState,
        elapsed: Int,
        endedAt: Long,
        task: Task?,
        taskTitle: String,
        mode: String,
        reason: FocusEndReason,
        interruptReason: InterruptReason?,
        settings: AppSettings,
        crossDay: Boolean,
    ) {
        val currentGeneration = ++generation
        appScope.launch {
            runCatching {
                useCase.settle(
                    FocusSettlementInput(
                        task = task,
                        mode = mode,
                        plannedSeconds = stopped.plannedSeconds,
                        actualSeconds = elapsed,
                        startedAt = stopped.startedAtEpoch,
                        endedAt = endedAt,
                        interruptionCount = stopped.interruptionCount,
                        strict = stopped.strict,
                        midnightMode = settings.midnightShift,
                        ignoreShortFocus = settings.ignoreShortFocus,
                        reason = reason,
                        interruptReason = interruptReason,
                    ),
                )
            }.onSuccess { result ->
                val seconds = result.session?.actualSeconds ?: elapsed
                // 长休息节奏：按归属日统计已完成的番茄数（包含刚写入的这一条）
                val longBreakDue = if (result.session?.mode == "pomodoro" && !result.interrupted) {
                    val count = runCatching {
                        focusRepo.completedPomodorosOn(result.attributedDate)
                    }.getOrDefault(0)
                    BreakCycle.isLongBreakDue(count)
                } else {
                    false
                }
                if (currentGeneration == generation) {
                    onSummary(
                        SessionSummary(
                            minutes = if (result.discarded) seconds / 60 else max(1, seconds / 60),
                            taskTitle = taskTitle,
                            newBadges = result.newBadges,
                            actualSeconds = seconds,
                            discarded = result.discarded,
                            discardReason = result.discardReason,
                            checkInWritten = result.checkInWritten,
                            streak = result.streak,
                            interrupted = result.interrupted,
                            attributedDate = result.attributedDate,
                            crossDay = crossDay,
                            interruptReason = result.interruptReason,
                            longBreakDue = longBreakDue,
                        ),
                    )
                }
                events.emit(
                    FocusSettledEvent(
                        taskId = task?.id,
                        taskTitle = taskTitle,
                        actualSeconds = result.session?.actualSeconds ?: elapsed,
                        checkInWritten = result.checkInWritten,
                        streak = result.streak,
                        interrupted = result.interrupted,
                        discarded = result.discarded,
                        discardReason = result.discardReason,
                        attributedDate = result.attributedDate,
                    ),
                )
            }.onFailure { e ->
                if (currentGeneration == generation) {
                    onError(e.message ?: "专注结算失败")
                }
            }
        }
    }
}
