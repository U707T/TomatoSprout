package com.u707t.tomatosprout.ui.focus

import android.content.Context
import android.os.SystemClock
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

data class SessionSummary(
    val minutes: Int,
    val taskTitle: String?,
    val newBadges: List<Badge>,
    val strictDiscarded: Boolean = false,
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
    val error: String? = null,
)

@HiltViewModel
class FocusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val focusUseCase: FocusUseCase,
    private val tasks: TaskRepository,
    private val settingsRepo: SettingsRepository,
    private val live: FocusLiveController,
) : ViewModel() {
    private val timer = MutableStateFlow<TimerState?>(null)
    private val now = MutableStateFlow(SystemClock.elapsedRealtime())
    private val activeTasks = MutableStateFlow<List<Task>>(emptyList())
    private val selectedTask = MutableStateFlow<String?>(null)
    private val focusMinutes = MutableStateFlow(25)
    private val breakMinutes = MutableStateFlow(5)
    private val summary = MutableStateFlow<SessionSummary?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private var lastNotifiedSecond = -1L

    val ui: StateFlow<FocusUiState> = run {
        val core = combine(timer, now, activeTasks) { t, n, tasks -> Triple(t, n, tasks) }
        val config = combine(selectedTask, focusMinutes, breakMinutes, settings) { id, focus, rest, s ->
            Config(id, focus, rest, s)
        }
        combine(core, config, summary, error) { c, cfg, s, e ->
            FocusUiState(
                loading = false,
                timer = c.first,
                nowMono = c.second,
                tasks = c.third,
                selectedTaskId = cfg.taskId,
                focusMinutes = cfg.focus,
                breakMinutes = cfg.rest,
                settings = cfg.settings,
                summary = s,
                error = e,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    private data class Config(val taskId: String?, val focus: Int, val rest: Int, val settings: AppSettings)

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
                if (stored.state.phase == TimerPhase.RUNNING) refresh(false)
            }
        }
        viewModelScope.launch {
            var ticks = 0
            while (true) {
                delay(500)
                ticks++
                // 通知按钮在 Activity 已处于前台时不会触发 onResume，需轮询命令
                if (ticks % 4 == 0) consumePendingCommand()
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
        consumePendingCommand()
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

    fun startFocus() {
        if (timer.value?.isActive == true) return
        val config = settings.value
        val state = TimerEngine.start(
            mode = if (config.countUpMode) TimerMode.COUNTUP else TimerMode.COUNTDOWN,
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
        FocusTimerStore.save(context, state, selectedTask.value)
        updateLive(state, now.value)
    }

    fun togglePause() {
        val state = timer.value ?: return
        when (val result = TimerEngine.togglePause(state, SystemClock.elapsedRealtime())) {
            is com.u707t.tomatosprout.domain.PauseResult.Ok -> {
                timer.value = result.state
                now.value = SystemClock.elapsedRealtime()
                FocusTimerStore.save(context, result.state, selectedTask.value)
                updateLive(result.state, now.value)
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
                viewModelScope.launch {
                    val completion = focusUseCase.complete(
                        task = task,
                        mode = if (stopped.mode == TimerMode.COUNTUP) "countup" else "pomodoro",
                        plannedSeconds = stopped.plannedSeconds,
                        actualSeconds = elapsed,
                        startedAt = stopped.startedAtEpoch,
                        endedAt = System.currentTimeMillis(),
                        midnightShift = settings.value.midnightShift,
                        interruptionCount = stopped.interruptionCount,
                    )
                    summary.value = SessionSummary(
                        minutes = max(1, elapsed / 60),
                        taskTitle = task?.title,
                        newBadges = completion?.newBadges.orEmpty(),
                    )
                }
            }
        }
        live.cancel()
        FocusTimerStore.clear(context)
        timer.value = null
        now.value = current
        lastNotifiedSecond = -1
    }

    private fun updateLive(state: TimerState, nowMono: Long) {
        if (state.isBreak) {
            live.update(state, nowMono, "休息一下")
            return
        }
        val title = activeTasks.value.firstOrNull { it.id == selectedTask.value }?.title ?: "自由专注"
        live.update(state, nowMono, title)
    }

    fun randomId(): String = UUID.randomUUID().toString()
}
