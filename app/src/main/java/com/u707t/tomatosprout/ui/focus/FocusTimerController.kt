package com.u707t.tomatosprout.ui.focus

import android.content.Context
import android.os.SystemClock
import com.u707t.tomatosprout.data.focus.FocusTimerStore
import com.u707t.tomatosprout.domain.PauseResult
import com.u707t.tomatosprout.domain.TimerEngine
import com.u707t.tomatosprout.domain.TimerMode
import com.u707t.tomatosprout.domain.TimerPhase
import com.u707t.tomatosprout.domain.TimerState
import com.u707t.tomatosprout.notify.FocusFallbackScheduler
import com.u707t.tomatosprout.notify.FocusLiveController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 专注计时运行时：只负责「时钟 + 快照 + 实时活动 + 兜底通知」。
 *
 * 与旧实现的区别：ticker 在空闲/暂停时挂在 `active.first { it }` 上，
 * 不产生 500ms 唤醒；只有计时真正 RUNNING 时才进行秒级刷新。
 * 结算、音频、UI 覆盖层都不在这里，ViewModel 保持为协调者。
 */
class FocusTimerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val live: FocusLiveController,
    private val fallback: FocusFallbackScheduler,
    private val taskId: () -> String?,
    private val taskTitle: () -> String,
    private val notificationsEnabled: () -> Boolean,
    /** 是否允许使用系统实时活动 / 超级岛；关闭后只发普通通知卡片。 */
    private val richSurface: () -> Boolean,
    /** 是否无视系统探测、强制发小米焦点通知。 */
    private val forceFocus: () -> Boolean,
    private val onCountdownFinished: () -> Unit,
    private val onCommandPoll: () -> Unit,
) {
    private val _timer = MutableStateFlow<TimerState?>(null)
    val timer: StateFlow<TimerState?> = _timer.asStateFlow()

    private val _now = MutableStateFlow(SystemClock.elapsedRealtime())
    val now: StateFlow<Long> = _now.asStateFlow()

    /** 是否有计时存在（RUNNING 或 PAUSED）；为空时 ticker 真正挂起。 */
    private val active = MutableStateFlow(false)
    private var lastNotifiedSecond = -1L
    private var ticker: Job? = null

    data class Stopped(
        val state: TimerState,
        val elapsedSeconds: Int,
        val nowMono: Long,
        val nowWall: Long,
        /** 快照前倒计时是否已经归零（stop 后 phase 永远是 FINISHED，不能用来判断）。 */
        val reachedEnd: Boolean,
    )

    fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var ticks = 0
            while (isActive) {
                if (!active.value) {
                    // 空闲：真正挂起，不轮询
                    active.first { it }
                    ticks = 0
                    continue
                }
                delay(500)
                ticks++
                // 通知按钮在 Activity 前台时不会触发 onResume，需要低频轮询（暂停时也要）
                if (ticks % 4 == 0) onCommandPoll()
                val state = _timer.value
                if (state == null) {
                    active.value = false
                    continue
                }
                if (state.phase != TimerPhase.RUNNING) continue
                val current = SystemClock.elapsedRealtime()
                _now.value = current
                val ticked = TimerEngine.tick(state, current)
                if (ticked.phase == TimerPhase.FINISHED) {
                    _timer.value = ticked
                    active.value = false
                    onCountdownFinished()
                } else {
                    val second = ticked.elapsedSeconds(current).toLong()
                    if (second != lastNotifiedSecond) {
                        lastNotifiedSecond = second
                        updateLive(ticked, current)
                    }
                }
            }
        }
    }

    /** 进程恢复：只接管状态，等 VM 设置好选中任务后再调用 [resumeRunning]。 */
    fun adopt(state: TimerState) {
        _timer.value = state
        _now.value = SystemClock.elapsedRealtime()
        lastNotifiedSecond = -1
        active.value = true
    }

    /** 恢复后继续跑：刷新一次（可能立即结算）并重排兜底通知。 */
    fun resumeRunning() {
        val state = _timer.value ?: return
        if (state.phase != TimerPhase.RUNNING) return
        active.value = true
        refresh(false)
        _timer.value?.let { scheduleFallback(it, _now.value) }
    }

    fun start(state: TimerState) {
        _timer.value = state
        _now.value = SystemClock.elapsedRealtime()
        lastNotifiedSecond = -1
        active.value = true
        FocusTimerStore.save(context, state, taskId())
        updateLive(state, _now.value)
        scheduleFallback(state, _now.value)
    }

    fun refresh(notify: Boolean) {
        _now.value = SystemClock.elapsedRealtime()
        val state = _timer.value ?: return
        val ticked = TimerEngine.tick(state, _now.value)
        if (ticked.phase == TimerPhase.FINISHED) {
            _timer.value = ticked
            active.value = false
            onCountdownFinished()
        } else if (ticked.phase == TimerPhase.RUNNING && notify) {
            updateLive(ticked, _now.value)
        }
    }

    fun togglePause(): PauseResult {
        val state = _timer.value ?: return PauseResult.NotRunning
        val result = TimerEngine.togglePause(state, SystemClock.elapsedRealtime())
        if (result !is PauseResult.Ok) return result
        _timer.value = result.state
        _now.value = SystemClock.elapsedRealtime()
        FocusTimerStore.save(context, result.state, taskId())
        updateLive(result.state, _now.value)
        if (result.state.phase == TimerPhase.RUNNING) {
            active.value = true
            scheduleFallback(result.state, _now.value)
        } else {
            // 暂停时保留低频命令轮询，避免通知里的「继续」要等 onResume 才生效
            active.value = true
            fallback.cancel()
        }
        return result
    }

    /** 弹窗期间冻结计时（不增加 interruptionCount）。 */
    fun freezeForDialog() {
        val state = _timer.value ?: return
        if (state.phase != TimerPhase.RUNNING) return
        val current = SystemClock.elapsedRealtime()
        val frozen = state.copy(
            phase = TimerPhase.PAUSED,
            accumulatedMillis = state.elapsedMillis(current),
            runningSince = null,
        )
        _timer.value = frozen
        _now.value = current
        // 弹窗期间仍需消费通知命令，保留 ticker 的低频轮询
        active.value = true
        FocusTimerStore.save(context, frozen, taskId())
        fallback.cancel()
        updateLive(frozen, current)
    }

    /** 取消停止确认后继续。 */
    fun resumeFromDialog() {
        val state = _timer.value ?: return
        if (state.phase != TimerPhase.PAUSED) return
        val current = SystemClock.elapsedRealtime()
        val resumed = state.copy(phase = TimerPhase.RUNNING, runningSince = current)
        _timer.value = resumed
        _now.value = current
        active.value = true
        FocusTimerStore.save(context, resumed, taskId())
        updateLive(resumed, current)
        scheduleFallback(resumed, current)
    }

    /**
     * 通知/岛上的「+5 分钟」：延长当前倒计时。
     * 返回是否真的改了计划时长（正计时、已暂停、已结束都不改）。
     */
    fun extend(seconds: Int = 300): Boolean {
        val state = _timer.value ?: return false
        val nowMono = SystemClock.elapsedRealtime()
        val extended = TimerEngine.extend(TimerEngine.tick(state, nowMono), seconds)
        if (extended == state) return false
        _timer.value = extended
        _now.value = nowMono
        FocusTimerStore.save(context, extended, taskId())
        updateLive(extended, nowMono)
        scheduleFallback(extended, nowMono)
        return true
    }

    /** 结算到调用时刻，不做清理；用于停止/确认前的判断。 */
    fun snapshot(): Stopped? {
        val state = _timer.value ?: return null
        val current = SystemClock.elapsedRealtime()
        _now.value = current
        val ticked = TimerEngine.tick(state, current)
        val stopped = TimerEngine.stop(ticked, current)
        return Stopped(
            state = stopped,
            elapsedSeconds = stopped.elapsedSeconds(current),
            nowMono = current,
            nowWall = System.currentTimeMillis(),
            reachedEnd = ticked.phase == TimerPhase.FINISHED,
        )
    }

    /** 结算后的统一清理；不会触发结算回调。 */
    fun clear() {
        live.cancel()
        fallback.cancel()
        FocusTimerStore.clear(context)
        _timer.value = null
        _now.value = SystemClock.elapsedRealtime()
        lastNotifiedSecond = -1
        active.value = false
    }

    private fun scheduleFallback(state: TimerState, nowMono: Long) {
        fallback.cancel()
        if (state.phase != TimerPhase.RUNNING || state.mode != TimerMode.COUNTDOWN) return
        val remaining = state.remainingMillis(nowMono)
        fallback.schedule(
            taskTitle = if (state.isBreak) "休息" else taskTitle(),
            triggerAtMillis = System.currentTimeMillis() + remaining + 60_000L,
            isBreak = state.isBreak,
        )
    }

    private fun updateLive(state: TimerState, nowMono: Long) {
        if (!notificationsEnabled()) {
            live.cancel()
            return
        }
        live.update(
            state = state,
            nowMono = nowMono,
            title = if (state.isBreak) "休息一下" else taskTitle(),
            richSurface = richSurface(),
            forceFocus = forceFocus(),
        )
    }
}
