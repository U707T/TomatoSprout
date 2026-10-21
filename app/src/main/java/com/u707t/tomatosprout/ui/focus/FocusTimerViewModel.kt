package com.u707t.tomatosprout.ui.focus

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.AppSettings
import com.u707t.tomatosprout.domain.FocusEndReason
import com.u707t.tomatosprout.domain.FocusSessionUseCase
import com.u707t.tomatosprout.domain.FocusSettlementInput
import com.u707t.tomatosprout.domain.PauseResult
import com.u707t.tomatosprout.domain.SessionAttribution
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.domain.SystemAppClock
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskRepository
import com.u707t.tomatosprout.domain.TimerEngine
import com.u707t.tomatosprout.domain.TimerMode
import com.u707t.tomatosprout.domain.TimerPhase
import com.u707t.tomatosprout.domain.TimerState
import com.u707t.tomatosprout.notify.FocusActionReceiver
import com.u707t.tomatosprout.notify.FocusCommandStore
import com.u707t.tomatosprout.notify.FocusFallbackScheduler
import com.u707t.tomatosprout.notify.FocusLiveController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

/**
 * 沉浸式专注页的唯一状态源。
 *
 * 结算规则（全部委托给 FocusSessionUseCase）：
 * - 倒计时自然结束：直接记录并自动打卡；
 * - 正计时结束：≥30 秒弹窗确认，<30 秒按设置直接丢弃；
 * - 手动停止未达时长：记录 interrupted，不打卡；严格模式彻底丢弃；
 * - 跨午夜：默认结束日，开启午夜模式则开始日。
 */
@HiltViewModel
class FocusTimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val focusSessionUseCase: FocusSessionUseCase,
    private val tasks: TaskRepository,
    private val settingsRepo: SettingsRepository,
    private val live: FocusLiveController,
    private val fallback: FocusFallbackScheduler,
    private val audio: FocusAudioPlayer,
    private val events: FocusEventBus,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val startTaskId: String? = savedStateHandle.get<String>("taskId")
    private val startTimerMode: String? = savedStateHandle.get<String>("timerMode")
    private val startMinutes: Int? = savedStateHandle.get<Int>("minutes")

    private val timer = MutableStateFlow<TimerState?>(null)
    private val now = MutableStateFlow(SystemClock.elapsedRealtime())
    private val activeTasks = MutableStateFlow<List<Task>>(emptyList())
    private val selectedTask = MutableStateFlow<String?>(null)
    private val focusMinutes = MutableStateFlow(25)
    private val breakMinutes = MutableStateFlow(5)
    private val summary = MutableStateFlow<SessionSummary?>(null)
    private val recordPrompt = MutableStateFlow<RecordPrompt?>(null)
    private val stopConfirm = MutableStateFlow<StopConfirm?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val keepScreenOn = MutableStateFlow(false)
    private val brightness = MutableStateFlow(-1f)
    private val immersive = MutableStateFlow(false)
    private val audioTrack = MutableStateFlow(FocusAudioOption.NONE)
    private val audioPlaying = MutableStateFlow(false)
    private val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private var lastNotifiedSecond = -1L
    private var dialogFrozen = false

    private data class CoreState(val timer: TimerState?, val nowMono: Long, val tasks: List<Task>)
    private data class ConfigState(val taskId: String?, val focus: Int, val rest: Int, val settings: AppSettings)
    private data class OverlayState(
        val summary: SessionSummary?,
        val recordPrompt: RecordPrompt?,
        val stopConfirm: StopConfirm?,
        val error: String?,
    )
    private data class PrefState(
        val keep: Boolean,
        val bright: Float,
        val full: Boolean,
        val track: FocusAudioOption,
        val playing: Boolean,
    )

    val ui: StateFlow<FocusUiState> = combine(
        combine(timer, now, activeTasks) { t, n, list -> CoreState(t, n, list) },
        combine(selectedTask, focusMinutes, breakMinutes, settings) { id, focus, rest, s ->
            ConfigState(id, focus, rest, s)
        },
        combine(summary, recordPrompt, stopConfirm, error) { s, p, c, e -> OverlayState(s, p, c, e) },
        combine(keepScreenOn, brightness, immersive, audioTrack, audioPlaying) { keep, bright, full, track, playing ->
            PrefState(keep, bright, full, track, playing)
        },
    ) { core, config, overlay, prefs ->
        FocusUiState(
            loading = false,
            timer = core.timer,
            nowMono = core.nowMono,
            tasks = core.tasks,
            selectedTaskId = config.taskId,
            focusMinutes = config.focus,
            breakMinutes = config.rest,
            settings = config.settings,
            summary = overlay.summary,
            recordPrompt = overlay.recordPrompt,
            stopConfirm = overlay.stopConfirm,
            error = overlay.error,
            keepScreenOn = prefs.keep,
            brightness = prefs.bright,
            immersive = prefs.full,
            audioTrack = prefs.track,
            audioPlaying = prefs.playing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    init {
        viewModelScope.launch {
            tasks.observeActiveTasks().collect { list -> activeTasks.value = list }
        }
        viewModelScope.launch {
            val config = settingsRepo.current()
            if (timer.value == null) {
                focusMinutes.value = config.defaultFocusMinutes
                breakMinutes.value = config.defaultBreakMinutes
            }
            val stored = FocusTimerStore.load(context)
            if (stored != null) {
                selectedTask.value = stored.taskId
                timer.value = stored.state
                now.value = SystemClock.elapsedRealtime()
                if (stored.state.phase == TimerPhase.RUNNING) {
                    refresh(false)
                    scheduleFallback(stored.state, now.value)
                }
            } else if (!startTaskId.isNullOrBlank() && timer.value == null) {
                // 从任务卡片「开始」进入并直接启动
                selectedTask.value = startTaskId
                startMinutes?.let { focusMinutes.value = it.coerceIn(5, 180) }
                startFocus(startTimerMode)
            }
            // 必须在计时恢复之后再消费通知按钮命令，否则冷启动时 stop/pause 会丢
            consumePendingCommand()
        }
        viewModelScope.launch {
            var ticks = 0
            while (true) {
                delay(500)
                ticks++
                // 通知按钮在 Activity 前台时不会触发 onResume，需要轮询命令
                if (ticks % 4 == 0 && timer.value != null) consumePendingCommand()
                val state = timer.value ?: continue
                if (state.phase != TimerPhase.RUNNING) continue
                val current = SystemClock.elapsedRealtime()
                now.value = current
                val ticked = TimerEngine.tick(state, current)
                if (ticked.phase == TimerPhase.FINISHED) {
                    timer.value = ticked
                    settle(FocusEndReason.COUNTDOWN_FINISHED)
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

    fun onResume() {
        refresh(true)
        consumePendingCommand()
    }

    private fun consumePendingCommand() {
        when (FocusCommandStore.consume(context)) {
            FocusActionReceiver.ACTION_TOGGLE -> togglePause()
            FocusActionReceiver.ACTION_STOP -> requestStop()
        }
    }

    fun refresh(notify: Boolean) {
        now.value = SystemClock.elapsedRealtime()
        val state = timer.value ?: return
        val ticked = TimerEngine.tick(state, now.value)
        if (ticked.phase == TimerPhase.FINISHED) {
            timer.value = ticked
            settle(FocusEndReason.COUNTDOWN_FINISHED)
        } else if (ticked.phase == TimerPhase.RUNNING && notify) {
            updateLive(ticked, now.value)
        }
    }

    fun selectTask(id: String?) {
        if (timer.value?.isActive == true) return
        selectedTask.value = id
        val task = activeTasks.value.firstOrNull { it.id == id }
        if (task != null && task.focusMinutes in 5..180) focusMinutes.value = task.focusMinutes
    }

    fun setFocusMinutes(minutes: Int) {
        if (timer.value?.isActive == true) return
        focusMinutes.value = minutes.coerceIn(5, 180)
    }

    fun setBreakMinutes(minutes: Int) {
        breakMinutes.value = minutes.coerceIn(5, 30)
    }

    fun setMode(mode: TimerMode) {
        if (timer.value?.isActive == true) return
        viewModelScope.launch { settingsRepo.update { it.copy(countUpMode = mode == TimerMode.COUNTUP) } }
    }

    fun setStrict(enabled: Boolean) {
        if (timer.value?.isActive == true) return
        viewModelScope.launch { settingsRepo.update { it.copy(strictMode = enabled) } }
    }

    fun startFocus(overrideMode: String? = null) {
        if (timer.value?.isActive == true) return
        val config = settings.value
        val countUp = when (overrideMode) {
            "countup", "none" -> true
            "countdown" -> false
            else -> config.countUpMode
        }
        val state = TimerEngine.start(
            mode = if (countUp) TimerMode.COUNTUP else TimerMode.COUNTDOWN,
            plannedSeconds = focusMinutes.value * 60,
            nowMono = SystemClock.elapsedRealtime(),
            nowEpoch = System.currentTimeMillis(),
            strict = config.strictMode,
            isBreak = false,
        )
        start(state)
    }

    fun startBreak() {
        if (timer.value?.isActive == true) return
        summary.value = null
        val state = TimerEngine.start(
            mode = TimerMode.COUNTDOWN,
            plannedSeconds = breakMinutes.value * 60,
            nowMono = SystemClock.elapsedRealtime(),
            nowEpoch = System.currentTimeMillis(),
            strict = false,
            isBreak = true,
        )
        start(state)
    }

    private fun start(state: TimerState) {
        timer.value = state
        now.value = SystemClock.elapsedRealtime()
        lastNotifiedSecond = -1
        dialogFrozen = false
        summary.value = null
        recordPrompt.value = null
        stopConfirm.value = null
        FocusTimerStore.save(context, state, selectedTask.value)
        updateLive(state, now.value)
        scheduleFallback(state, now.value)
    }

    fun togglePause() {
        if (stopConfirm.value != null || recordPrompt.value != null) return
        val state = timer.value ?: return
        dialogFrozen = false
        when (val result = TimerEngine.togglePause(state, SystemClock.elapsedRealtime())) {
            is PauseResult.Ok -> {
                timer.value = result.state
                now.value = SystemClock.elapsedRealtime()
                FocusTimerStore.save(context, result.state, selectedTask.value)
                updateLive(result.state, now.value)
                if (result.state.phase == TimerPhase.RUNNING) {
                    scheduleFallback(result.state, now.value)
                } else {
                    fallback.cancel()
                }
            }
            PauseResult.StrictBlocked -> error.value = "严格模式已开启，本次专注不能暂停"
            PauseResult.NotRunning -> Unit
        }
    }

    /**
     * 用户点击「停止」：
     * - 倒计时：先弹结算拦截，确认后按是否达到计划时长决定 completed / interrupted；
     * - 正计时：≥30 秒弹「是否记录」，<30 秒且开启忽略则直接丢弃；
     * - 休息：直接结束，不写专注记录。
     */
    fun requestStop() {
        val state = timer.value ?: return
        val current = SystemClock.elapsedRealtime()
        now.value = current
        val ticked = TimerEngine.tick(state, current)
        if (ticked.phase == TimerPhase.FINISHED) {
            timer.value = ticked
            settle(FocusEndReason.COUNTDOWN_FINISHED)
            return
        }
        val elapsed = ticked.elapsedSeconds(current)
        if (ticked.isBreak) {
            settle(FocusEndReason.MANUAL_STOP)
            return
        }
        when (ticked.mode) {
            TimerMode.COUNTUP -> {
                if (elapsed < 30 && settings.value.ignoreShortFocus) {
                    settle(FocusEndReason.DISCARDED)
                } else {
                    // 冻结计时，避免用户停留在弹窗时继续累计
                    freezeForDialog()
                    recordPrompt.value = RecordPrompt(
                        taskId = selectedTask.value,
                        taskTitle = currentTask()?.title,
                        mode = "countup",
                        plannedSeconds = ticked.plannedSeconds,
                        actualSeconds = elapsed,
                        startedAt = ticked.startedAtEpoch,
                        endedAt = System.currentTimeMillis(),
                        interruptionCount = ticked.interruptionCount,
                    )
                }
            }
            TimerMode.COUNTDOWN -> {
                if (elapsed >= ticked.plannedSeconds) {
                    settle(FocusEndReason.COUNTDOWN_FINISHED)
                } else {
                    freezeForDialog()
                    stopConfirm.value = StopConfirm(
                        actualSeconds = elapsed,
                        plannedSeconds = ticked.plannedSeconds,
                        strict = ticked.strict,
                    )
                }
            }
        }
    }

    fun cancelStop() {
        stopConfirm.value = null
        if (!dialogFrozen) return
        dialogFrozen = false
        val state = timer.value ?: return
        if (state.phase != TimerPhase.PAUSED) return
        val current = SystemClock.elapsedRealtime()
        val resumed = state.copy(phase = TimerPhase.RUNNING, runningSince = current)
        timer.value = resumed
        now.value = current
        FocusTimerStore.save(context, resumed, selectedTask.value)
        updateLive(resumed, current)
        scheduleFallback(resumed, current)
    }

    fun confirmStop() {
        stopConfirm.value = null
        dialogFrozen = false
        val state = timer.value ?: return
        val elapsed = state.elapsedSeconds(SystemClock.elapsedRealtime())
        settle(
            if (elapsed >= state.plannedSeconds) FocusEndReason.COUNTDOWN_FINISHED
            else FocusEndReason.MANUAL_STOP,
        )
    }

    /** 正计时结束：用户确认记录。 */
    fun confirmRecord() {
        recordPrompt.value = null
        settle(FocusEndReason.COUNTUP_FINISHED)
    }

    /** 正计时结束：用户选择不记录。 */
    fun discardRecord() {
        recordPrompt.value = null
        settle(FocusEndReason.DISCARDED)
    }

    fun dismissSummary() {
        summary.value = null
    }

    fun clearError() {
        error.value = null
    }

    fun resetTimer() {
        if (timer.value == null) return
        clearRuntime()
        dialogFrozen = false
        summary.value = null
        recordPrompt.value = null
        stopConfirm.value = null
        now.value = SystemClock.elapsedRealtime()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        keepScreenOn.value = enabled
    }

    fun setBrightness(value: Float) {
        brightness.value = value.coerceIn(0.1f, 1f)
    }

    fun toggleImmersive() {
        immersive.value = !immersive.value
    }

    fun toggleAudio() {
        if (audioPlaying.value) {
            audio.pause()
            audioPlaying.value = false
        } else {
            val option = audioTrack.value.takeIf { it != FocusAudioOption.NONE } ?: FocusAudioOption.WHITE_NOISE
            audioTrack.value = option
            audio.play(option)
            audioPlaying.value = true
        }
    }

    fun selectAudio(option: FocusAudioOption) {
        audioTrack.value = option
        if (option == FocusAudioOption.NONE) {
            audio.stop()
            audioPlaying.value = false
        } else {
            audio.play(option)
            audioPlaying.value = true
        }
    }

    private fun currentTask(): Task? =
        activeTasks.value.firstOrNull { it.id == selectedTask.value }

    /**
     * 统一结算入口。先清理计时 UI，再把数据写入交给 UseCase；
     * 这样即使数据库较慢，界面也不会停留在 FINISHED 状态。
     */
    private fun settle(reason: FocusEndReason) {
        val state = timer.value ?: return
        val current = SystemClock.elapsedRealtime()
        val stopped = TimerEngine.stop(state, current)
        val elapsed = stopped.elapsedSeconds(current)
        val endedAt = System.currentTimeMillis()
        val task = currentTask()
        val taskTitle = if (stopped.isBreak) "休息" else (task?.title ?: "自由专注")
        val mode = if (stopped.mode == TimerMode.COUNTUP) "countup" else "pomodoro"
        val crossDay = SessionAttribution.isShifted(
            stopped.startedAtEpoch,
            endedAt,
            SystemAppClock.zone(),
        )

        timer.value = null
        now.value = current
        lastNotifiedSecond = -1
        dialogFrozen = false
        recordPrompt.value = null
        stopConfirm.value = null
        summary.value = null
        live.cancel()
        fallback.cancel()
        audio.stop()
        audioPlaying.value = false
        audioTrack.value = FocusAudioOption.NONE
        FocusTimerStore.clear(context)

        if (stopped.isBreak) {
            summary.value = SessionSummary(
                minutes = max(1, elapsed / 60),
                taskTitle = taskTitle,
                newBadges = emptyList(),
                actualSeconds = elapsed,
                isBreak = true,
            )
            return
        }

        viewModelScope.launch {
            runCatching {
                focusSessionUseCase.settle(
                    FocusSettlementInput(
                        task = task,
                        mode = mode,
                        plannedSeconds = stopped.plannedSeconds,
                        actualSeconds = elapsed,
                        startedAt = stopped.startedAtEpoch,
                        endedAt = endedAt,
                        interruptionCount = stopped.interruptionCount,
                        strict = stopped.strict,
                        midnightMode = settings.value.midnightShift,
                        ignoreShortFocus = settings.value.ignoreShortFocus,
                        reason = reason,
                    ),
                )
            }.onSuccess { result ->
                val seconds = result.session?.actualSeconds ?: elapsed
                summary.value = SessionSummary(
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
                )
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
                error.value = e.message ?: "专注结算失败"
            }
        }
    }

    private fun clearRuntime() {
        live.cancel()
        fallback.cancel()
        audio.stop()
        audioPlaying.value = false
        audioTrack.value = FocusAudioOption.NONE
        FocusTimerStore.clear(context)
        timer.value = null
        lastNotifiedSecond = -1
    }

    /** 弹窗期间冻结计时（不增加 interruptionCount），避免后台继续累计。 */
    private fun freezeForDialog() {
        val state = timer.value ?: return
        if (state.phase != TimerPhase.RUNNING) {
            dialogFrozen = false
            return
        }
        val current = SystemClock.elapsedRealtime()
        val frozen = state.copy(
            phase = TimerPhase.PAUSED,
            accumulatedMillis = state.elapsedMillis(current),
            runningSince = null,
        )
        timer.value = frozen
        now.value = current
        dialogFrozen = true
        FocusTimerStore.save(context, frozen, selectedTask.value)
        fallback.cancel()
        updateLive(frozen, current)
    }

    private fun scheduleFallback(state: TimerState, nowMono: Long) {
        fallback.cancel()
        if (state.phase != TimerPhase.RUNNING || state.mode != TimerMode.COUNTDOWN) return
        val remaining = state.remainingMillis(nowMono)
        val title = if (state.isBreak) "休息" else (currentTask()?.title ?: "自由专注")
        fallback.schedule(
            taskTitle = title,
            triggerAtMillis = System.currentTimeMillis() + remaining + 60_000L,
            isBreak = state.isBreak,
        )
    }

    private fun updateLive(state: TimerState, nowMono: Long) {
        if (!settings.value.notificationsEnabled) {
            live.cancel()
            return
        }
        if (state.isBreak) {
            live.update(state, nowMono, "休息一下")
            return
        }
        live.update(state, nowMono, currentTask()?.title ?: "自由专注")
    }

    override fun onCleared() {
        audio.release()
    }
}
