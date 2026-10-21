package com.u707t.tomatosprout.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

enum class TimerPhase { IDLE, RUNNING, PAUSED, FINISHED }
enum class TimerMode { COUNTDOWN, COUNTUP }

/**
 * 不可变计时状态。所有时间计算只基于单调时钟（elapsedRealtime），
 * 因此修改系统时间不会影响已流逝时长。
 */
data class TimerState(
    val mode: TimerMode,
    val phase: TimerPhase,
    val plannedSeconds: Int,
    val accumulatedMillis: Long = 0L,
    val runningSince: Long? = null,
    val startedAtEpoch: Long = 0L,
    val strict: Boolean = false,
    val interruptionCount: Int = 0,
    val isBreak: Boolean = false,
) {
    fun elapsedMillis(nowMono: Long): Long =
        accumulatedMillis + if (phase == TimerPhase.RUNNING && runningSince != null) {
            max(0L, nowMono - runningSince)
        } else 0L

    fun elapsedSeconds(nowMono: Long): Int = (elapsedMillis(nowMono) / 1000L).toInt()

    fun remainingMillis(nowMono: Long): Long =
        max(0L, plannedSeconds * 1000L - elapsedMillis(nowMono))

    val isActive: Boolean get() = phase == TimerPhase.RUNNING || phase == TimerPhase.PAUSED
}

sealed interface PauseResult {
    data class Ok(val state: TimerState) : PauseResult
    data object StrictBlocked : PauseResult
    data object NotRunning : PauseResult
}

object TimerEngine {
    fun start(
        mode: TimerMode,
        plannedSeconds: Int,
        nowMono: Long,
        nowEpoch: Long,
        strict: Boolean = false,
        isBreak: Boolean = false,
    ): TimerState = TimerState(
        mode = mode,
        phase = TimerPhase.RUNNING,
        plannedSeconds = plannedSeconds.coerceIn(60, 10_800),
        accumulatedMillis = 0L,
        runningSince = nowMono,
        startedAtEpoch = nowEpoch,
        strict = strict,
        isBreak = isBreak,
    )

    /** 暂停/继续共用一个入口；严格模式禁止暂停。 */
    fun togglePause(state: TimerState, nowMono: Long): PauseResult {
        if (!state.isActive) return PauseResult.NotRunning
        return when (state.phase) {
            TimerPhase.RUNNING -> {
                if (state.strict) return PauseResult.StrictBlocked
                PauseResult.Ok(
                    state.copy(
                        phase = TimerPhase.PAUSED,
                        accumulatedMillis = state.elapsedMillis(nowMono),
                        runningSince = null,
                        interruptionCount = state.interruptionCount + 1,
                    ),
                )
            }
            TimerPhase.PAUSED -> PauseResult.Ok(
                state.copy(phase = TimerPhase.RUNNING, runningSince = nowMono),
            )
            else -> PauseResult.NotRunning
        }
    }

    /** 结算当前计时；返回的状态为 FINISHED，保留真实流逝时长。 */
    fun stop(state: TimerState, nowMono: Long): TimerState =
        if (state.phase == TimerPhase.FINISHED) state
        else state.copy(
            phase = TimerPhase.FINISHED,
            accumulatedMillis = state.elapsedMillis(nowMono),
            runningSince = null,
        )

    /** 每秒/回到前台时调用：倒计时归零自动进入 FINISHED，正计时不自动结束。 */
    fun tick(state: TimerState, nowMono: Long): TimerState {
        if (state.phase != TimerPhase.RUNNING) return state
        if (state.mode == TimerMode.COUNTDOWN && state.remainingMillis(nowMono) <= 0L) {
            return state.copy(
                phase = TimerPhase.FINISHED,
                accumulatedMillis = max(state.accumulatedMillis, state.plannedSeconds * 1000L),
                runningSince = null,
            )
        }
        return state
    }

    fun formatClock(totalSeconds: Long): String {
        val safe = max(0L, totalSeconds)
        val h = safe / 3600
        val m = (safe % 3600) / 60
        val s = safe % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}

/**
 * 归属日期：默认取开始当天；开启「午夜归属」且开始时间在 00:00–03:59 时归到前一天。
 * 这样凌晨 00:20 开始的专注会记在昨晚的打卡上。
 */
object SessionAttribution {
    fun dateOf(startedAtEpoch: Long, midnightShift: Boolean, zone: ZoneId): LocalDate {
        val start = Instant.ofEpochMilli(startedAtEpoch).atZone(zone)
        val date = start.toLocalDate()
        return if (midnightShift && start.hour < 4) date.minusDays(1) else date
    }

    fun isShifted(startedAtEpoch: Long, midnightShift: Boolean, zone: ZoneId): Boolean {
        if (!midnightShift) return false
        return Instant.ofEpochMilli(startedAtEpoch).atZone(zone).hour < 4
    }
}
