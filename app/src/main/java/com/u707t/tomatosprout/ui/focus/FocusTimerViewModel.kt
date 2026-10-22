package com.u707t.tomatosprout.ui.focus

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.data.focus.FocusTimerStore
import com.u707t.tomatosprout.di.ApplicationScope
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

/**
 * 沉浸式专注页的协调者。
 *
 * - 计时运行时（ticker / 快照 / 实时活动 / 兜底通知）在 [FocusTimerController]；
 * - 结算规则（完成 / 中断 / 丢弃 / 跨午夜）全部委托给 FocusSessionUseCase；
 * - 这里只保留：任务与时长选择、结算弹窗、音频与显示偏好、通知指令转发。
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
    @ApplicationScope private val appScope: CoroutineScope,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val startTaskId: String? = savedStateHandle.get<String>("taskId")
    private val startTimerMode: String? = savedStateHandle.get<String>("timerMode")
    private val startMinutes: Int? = savedStateHandle.get<Int>("minutes")

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

    private var dialogFrozen = false
    private var restored = false

    /** 结算与新一轮专注的先后关系；跨线程读写，必须 volatile。 */
    @Volatile
    private var settlementGeneration = 0

    private val controller = FocusTimerController(
        context = context,
        scope = viewModelScope,
        live = live,
        fallback = fallback,
        taskId = { selectedTask.value },
        taskTitle = { currentTask()?.title ?: "自由专注" },
        notificationsEnabled = { settings.value.notificationsEnabled },
        onCountdownFinished = { settle(FocusEndReason.COUNTDOWN_FINISHED) },
        onCommandPoll = { consumePendingCommand() },
    )

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
        combine(controller.timer, controller.now, activeTasks) { t, n, list -> CoreState(t, n, list) },
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
        controller.startTicker()
        // 任务与设置都就绪后再恢复计时 / 自动开始，避免结算时拿不到任务导致漏打卡
        viewModelScope.launch {
            val config = runCatching { settingsRepo.current() }.getOrDefault(AppSettings())
            if (controller.timer.value == null) {
                focusMinutes.value = config.defaultFocusMinutes
                breakMinutes.value = config.defaultBreakMinutes
            }
            activeTasks.value = runCatching { tasks.observeActiveTasks().first() }.getOrDefault(emptyList())
            restoreOrStart()
        }
        viewModelScope.launch {
            tasks.observeActiveTasks().collect { list -> activeTasks.value = list }
        }
    }

    private fun restoreOrStart() {
        if (restored) return
        restored = true
        // 用户可能在首帧就点了「开始」，此时不要再拿旧快照覆盖新计时
        if (controller.timer.value != null) {
            consumePendingCommand()
            return
        }
        val stored = FocusTimerStore.load(context)
        if (stored != null) {
            selectedTask.value = stored.taskId
            controller.adopt(stored.state)
            if (stored.state.phase == TimerPhase.RUNNING) controller.resumeRunning()
        } else if (!startTaskId.isNullOrBlank()) {
            // 从任务卡片「开始」进入并直接启动
            selectedTask.value = startTaskId
            startMinutes?.let { focusMinutes.value = it.coerceIn(5, 180) }
            startFocus(startTimerMode)
        }
        // 必须在计时恢复之后再消费通知按钮命令，否则冷启动时 stop/pause 会丢
        consumePendingCommand()
    }

    fun onResume() {
        controller.refresh(true)
        consumePendingCommand()
    }

    private fun consumePendingCommand() {
        when (FocusCommandStore.consume(context)) {
            FocusActionReceiver.ACTION_TOGGLE -> togglePause()
            FocusActionReceiver.ACTION_STOP -> requestStop()
        }
    }

    fun selectTask(id: String?) {
        if (controller.timer.value?.isActive == true) return
        selectedTask.value = id
        val task = activeTasks.value.firstOrNull { it.id == id }
        if (task != null && task.focusMinutes in 5..180) focusMinutes.value = task.focusMinutes
    }

    fun setFocusMinutes(minutes: Int) {
        if (controller.timer.value?.isActive == true) return
        focusMinutes.value = minutes.coerceIn(5, 180)
    }

    fun setBreakMinutes(minutes: Int) {
        breakMinutes.value = minutes.coerceIn(5, 30)
    }

    fun setMode(mode: TimerMode) {
        if (controller.timer.value?.isActive == true) return
        viewModelScope.launch { settingsRepo.update { it.copy(countUpMode = mode == TimerMode.COUNTUP) } }
    }

    fun setStrict(enabled: Boolean) {
        if (controller.timer.value?.isActive == true) return
        viewModelScope.launch { settingsRepo.update { it.copy(strictMode = enabled) } }
    }

    fun startFocus(overrideMode: String? = null) {
        if (controller.timer.value?.isActive == true) return
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
        if (controller.timer.value?.isActive == true) return
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
        dialogFrozen = false
        // 新一轮专注开始后，旧结算结果不再覆盖当前摘要
        settlementGeneration++
        summary.value = null
        recordPrompt.value = null
        stopConfirm.value = null
        controller.start(state)
    }

    fun togglePause() {
        if (stopConfirm.value != null || recordPrompt.value != null) return
        dialogFrozen = false
        when (val result = controller.togglePause()) {
            is PauseResult.Ok -> Unit
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
        val snap = controller.snapshot() ?: return
        val state = snap.state
        val elapsed = snap.elapsedSeconds
        if (snap.reachedEnd) {
            settle(FocusEndReason.COUNTDOWN_FINISHED)
            return
        }
        if (state.isBreak) {
            settle(FocusEndReason.MANUAL_STOP)
            return
        }
        when (state.mode) {
            TimerMode.COUNTUP -> {
                if (elapsed < 30 && settings.value.ignoreShortFocus) {
                    settle(FocusEndReason.DISCARDED)
                } else {
                    // 冻结计时，避免用户停留在弹窗时继续累计
                    controller.freezeForDialog()
                    recordPrompt.value = RecordPrompt(
                        taskId = selectedTask.value,
                        taskTitle = currentTask()?.title,
                        mode = "countup",
                        plannedSeconds = state.plannedSeconds,
                        actualSeconds = elapsed,
                        startedAt = state.startedAtEpoch,
                        endedAt = snap.nowWall,
                        interruptionCount = state.interruptionCount,
                    )
                }
            }
            TimerMode.COUNTDOWN -> {
                if (elapsed >= state.plannedSeconds) {
                    settle(FocusEndReason.COUNTDOWN_FINISHED)
                } else {
                    controller.freezeForDialog()
                    stopConfirm.value = StopConfirm(
                        actualSeconds = elapsed,
                        plannedSeconds = state.plannedSeconds,
                        strict = state.strict,
                    )
                }
            }
        }
    }

    fun cancelStop() {
        stopConfirm.value = null
        if (!dialogFrozen) return
        dialogFrozen = false
        controller.resumeFromDialog()
    }

    fun confirmStop() {
        stopConfirm.value = null
        dialogFrozen = false
        val snap = controller.snapshot() ?: return
        settle(
            if (snap.elapsedSeconds >= snap.state.plannedSeconds) {
                FocusEndReason.COUNTDOWN_FINISHED
            } else {
                FocusEndReason.MANUAL_STOP
            },
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
        if (controller.timer.value == null) return
        controller.clear()
        audio.stop()
        audioPlaying.value = false
        audioTrack.value = FocusAudioOption.NONE
        dialogFrozen = false
        settlementGeneration++
        summary.value = null
        recordPrompt.value = null
        stopConfirm.value = null
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
     * 统一结算入口：先取走计时快照并清理运行时，再把数据写入交给 UseCase。
     * 写入挂在应用级作用域上，退出页面也不会中断。
     */
    private fun settle(reason: FocusEndReason) {
        val snap = controller.snapshot() ?: return
        val stopped = snap.state
        val elapsed = snap.elapsedSeconds
        val nowWall = snap.nowWall
        // 倒计时自然结束时，真实结束时刻是「开始 + 计划时长」；
        // 若进程在后台被杀后才恢复，不能用恢复时刻把跨天归属算错。
        val scheduledEnd = stopped.startedAtEpoch + stopped.plannedSeconds * 1000L
        val endedAt = if (!stopped.isBreak &&
            reason == FocusEndReason.COUNTDOWN_FINISHED &&
            stopped.mode == TimerMode.COUNTDOWN
        ) {
            scheduledEnd.coerceAtMost(nowWall).coerceAtLeast(stopped.startedAtEpoch)
        } else {
            nowWall
        }
        val task = currentTask()
        val taskTitle = if (stopped.isBreak) "休息" else (task?.title ?: "自由专注")
        val mode = if (stopped.mode == TimerMode.COUNTUP) "countup" else "pomodoro"
        val crossDay = SessionAttribution.isShifted(
            stopped.startedAtEpoch,
            endedAt,
            SystemAppClock.zone(),
        )
        val generation = ++settlementGeneration

        controller.clear()
        dialogFrozen = false
        recordPrompt.value = null
        stopConfirm.value = null
        summary.value = null
        audio.stop()
        audioPlaying.value = false
        audioTrack.value = FocusAudioOption.NONE

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

        appScope.launch {
            runCatching {
                // 用户可能已经退出专注页：结算不挂在 viewModelScope 上，避免写入被取消
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
                // 如果用户已经开始了下一轮专注，旧摘要不要覆盖新界面
                if (generation == settlementGeneration) {
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
                if (generation == settlementGeneration) {
                    error.value = e.message ?: "专注结算失败"
                }
            }
        }
    }

    override fun onCleared() {
        audio.release()
    }
}
