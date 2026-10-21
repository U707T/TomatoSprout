package com.u707t.tomatosprout.ui.focus

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.AppSettings
import com.u707t.tomatosprout.domain.Badge
import com.u707t.tomatosprout.domain.FocusUseCase
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskRepository
import com.u707t.tomatosprout.domain.TaskStatus
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class FocusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val focusUseCase: FocusUseCase,
    private val tasks: TaskRepository,
    private val settingsRepo: SettingsRepository,
    private val live: FocusLiveController,
    private val fallback: FocusFallbackScheduler,
    private val audio: FocusAudioPlayer,
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
    private val shortPrompt = MutableStateFlow<ShortFocusPrompt?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val keepScreenOn = MutableStateFlow(false)
    private val brightness = MutableStateFlow(-1f)
    private val immersive = MutableStateFlow(false)
    private val audioTrack = MutableStateFlow(FocusAudioOption.NONE)
    private val audioPlaying = MutableStateFlow(false)
    private val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private var lastNotifiedSecond = -1L

    val ui: StateFlow<FocusUiState> = run {
        val core = combine(timer, now, activeTasks) { t, n, tasks -> Triple(t, n, tasks) }
        val config = combine(selectedTask, focusMinutes, breakMinutes, settings) { id, focus, rest, s ->
            Config(id, focus, rest, s)
        }
        val top = combine(core, config, summary, shortPrompt, error) { c, cfg, s, prompt, e ->
            TopState(c, cfg, s, prompt, e)
        }
        val prefs = combine(keepScreenOn, brightness, immersive, audioTrack, audioPlaying) { keep, bright, full, track, playing ->
            PrefState(keep, bright, full, track, playing)
        }
        combine(top, prefs) { t, p ->
            val c = t.core
            val cfg = t.config
            FocusUiState(
                loading = false,
                timer = c.first,
                nowMono = c.second,
                tasks = c.third,
                selectedTaskId = cfg.taskId,
                focusMinutes = cfg.focus,
                breakMinutes = cfg.rest,
                settings = cfg.settings,
                summary = t.summary,
                shortPrompt = t.prompt,
                error = t.error,
                keepScreenOn = p.keep,
                brightness = p.bright,
                immersive = p.full,
                audioTrack = p.track,
                audioPlaying = p.playing,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    private data class Config(val taskId: String?, val focus: Int, val rest: Int, val settings: AppSettings)
    private data class TopState(val core: Triple<TimerState?, Long, List<Task>>, val config: Config, val summary: SessionSummary?, val prompt: ShortFocusPrompt?, val error: String?)
    private data class PrefState(val keep: Boolean, val bright: Float, val full: Boolean, val track: FocusAudioOption, val playing: Boolean)

    init {
        viewModelScope.launch {
            tasks.observeActiveTasks().collect { list ->
                activeTasks.value = list
            }
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
                // 从任务卡片「开始」直接进入并启动
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
                // 通知按钮在 Activity 已处于前台时不会触发 onResume，需轮询命令；
                // 计时尚未恢复时不要抢消费，否则冷启动命令会丢
                if (ticks % 4 == 0 && timer.value != null) consumePendingCommand()
                val state = timer.value ?: continue
                if (state.phase != TimerPhase.RUNNING) continue
                val current = SystemClock.elapsedRealtime()
                now.value = current
                val ticked = TimerEngine.tick(state, current)
                if (ticked.phase == TimerPhase.FINISHED) {
                    timer.value = ticked
                    settle(auto = true)
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
            FocusActionReceiver.ACTION_STOP -> stop()
        }
    }

    fun refresh(notify: Boolean) {
        now.value = SystemClock.elapsedRealtime()
        val state = timer.value ?: return
        val ticked = TimerEngine.tick(state, now.value)
        if (ticked.phase == TimerPhase.FINISHED) {
            timer.value = ticked
            settle(auto = true)
        } else if (ticked.phase == TimerPhase.RUNNING && notify) {
            updateLive(ticked, now.value)
        }
    }

    fun selectTask(id: String?) {
        if (timer.value?.isActive == true) return
        selectedTask.value = id
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
        viewModelScope.launch {
            settingsRepo.update { it.copy(countUpMode = mode == TimerMode.COUNTUP) }
        }
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
        summary.value = null
        shortPrompt.value = null
        FocusTimerStore.save(context, state, selectedTask.value)
        updateLive(state, now.value)
        scheduleFallback(state, now.value)
    }

    fun togglePause() {
        val state = timer.value ?: return
        when (val result = TimerEngine.togglePause(state, SystemClock.elapsedRealtime())) {
            is com.u707t.tomatosprout.domain.PauseResult.Ok -> {
                timer.value = result.state
                now.value = SystemClock.elapsedRealtime()
                FocusTimerStore.save(context, result.state, selectedTask.value)
                updateLive(result.state, now.value)
                if (result.state.phase == TimerPhase.RUNNING) scheduleFallback(result.state, now.value) else fallback.cancel()
            }
            com.u707t.tomatosprout.domain.PauseResult.StrictBlocked -> {
                error.value = "严格模式已开启，本次专注不能暂停"
            }
            com.u707t.tomatosprout.domain.PauseResult.NotRunning -> Unit
        }
    }

    fun stop() {
        if (timer.value == null) return
        settle(auto = false)
    }

    fun dismissSummary() {
        summary.value = null
    }

    fun resetTimer() {
        if (timer.value?.isActive != true) return
        fallback.cancel()
        live.cancel()
        FocusTimerStore.clear(context)
        audio.stop()
        audioTrack.value = FocusAudioOption.NONE
        audioPlaying.value = false
        timer.value = null
        now.value = SystemClock.elapsedRealtime()
        summary.value = null
        shortPrompt.value = null
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

    fun recordShortFocus() {
        val prompt = shortPrompt.value ?: return
        shortPrompt.value = null
        saveFocusSession(
            taskId = prompt.taskId,
            taskTitle = prompt.taskTitle,
            mode = prompt.mode,
            plannedSeconds = prompt.plannedSeconds,
            actualSeconds = prompt.actualSeconds,
            startedAt = prompt.startedAt,
            endedAt = prompt.endedAt,
            interruptionCount = prompt.interruptionCount,
        )
    }

    fun discardShortFocus() {
        shortPrompt.value = null
    }

    fun clearError() {
        error.value = null
    }

    /** auto=true 表示倒计时自然结束（严格模式也允许记录）；false 表示用户手动结束。 */
    private fun settle(auto: Boolean) {
        val state = timer.value ?: return
        val current = SystemClock.elapsedRealtime()
        val stopped = TimerEngine.stop(state, current)
        val elapsed = stopped.elapsedSeconds(current)
        if (!stopped.isBreak && elapsed >= 1) {
            val strictDiscard = stopped.strict && !auto &&
                (stopped.mode == TimerMode.COUNTUP || elapsed < stopped.plannedSeconds)
            if (strictDiscard) {
                summary.value = SessionSummary(
                    minutes = 0,
                    taskTitle = null,
                    newBadges = emptyList(),
                    strictDiscarded = true,
                )
            } else {
                val task = activeTasks.value.firstOrNull { it.id == selectedTask.value }
                val mode = if (stopped.mode == TimerMode.COUNTUP) "countup" else "pomodoro"
                val endedAt = System.currentTimeMillis()
                if (stopped.mode == TimerMode.COUNTUP && elapsed < 30 && settings.value.ignoreShortFocus) {
                    shortPrompt.value = ShortFocusPrompt(
                        taskId = task?.id,
                        taskTitle = task?.title,
                        mode = mode,
                        plannedSeconds = stopped.plannedSeconds,
                        actualSeconds = elapsed,
                        startedAt = stopped.startedAtEpoch,
                        endedAt = endedAt,
                        interruptionCount = stopped.interruptionCount,
                    )
                } else {
                    saveFocusSession(
                        taskId = task?.id,
                        taskTitle = task?.title,
                        mode = mode,
                        plannedSeconds = stopped.plannedSeconds,
                        actualSeconds = elapsed,
                        startedAt = stopped.startedAtEpoch,
                        endedAt = endedAt,
                        interruptionCount = stopped.interruptionCount,
                    )
                }
            }
        }
        live.cancel()
        fallback.cancel()
        audio.stop()
        audioPlaying.value = false
        audioTrack.value = FocusAudioOption.NONE
        FocusTimerStore.clear(context)
        timer.value = null
        now.value = current
        lastNotifiedSecond = -1
    }

    private fun saveFocusSession(
        taskId: String?,
        taskTitle: String?,
        mode: String,
        plannedSeconds: Int,
        actualSeconds: Int,
        startedAt: Long,
        endedAt: Long,
        interruptionCount: Int,
    ) {
        viewModelScope.launch {
            val task = activeTasks.value.firstOrNull { it.id == taskId }
                ?: taskId?.let { tasks.task(it) }
            val completion = focusUseCase.complete(
                task = task,
                mode = mode,
                plannedSeconds = plannedSeconds,
                actualSeconds = actualSeconds,
                startedAt = startedAt,
                endedAt = endedAt,
                midnightShift = settings.value.midnightShift,
                interruptionCount = interruptionCount,
            )
            summary.value = SessionSummary(
                minutes = max(1, actualSeconds / 60),
                taskTitle = taskTitle,
                newBadges = completion?.newBadges.orEmpty(),
                actualSeconds = actualSeconds,
            )
        }
    }

    private fun scheduleFallback(state: TimerState, nowMono: Long) {
        fallback.cancel()
        if (state.phase != TimerPhase.RUNNING || state.mode != TimerMode.COUNTDOWN) return
        val remaining = state.remainingMillis(nowMono)
        val title = if (state.isBreak) "休息" else {
            activeTasks.value.firstOrNull { it.id == selectedTask.value }?.title ?: "自由专注"
        }
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
        val title = activeTasks.value.firstOrNull { it.id == selectedTask.value }?.title ?: "自由专注"
        live.update(state, nowMono, title)
    }

    override fun onCleared() {
        audio.release()
        super.onCleared()
    }
}
