package com.u707t.tomatosprout.data.focus

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.u707t.tomatosprout.domain.TimerMode
import com.u707t.tomatosprout.domain.TimerPhase
import com.u707t.tomatosprout.domain.TimerState

/** 进程被杀后恢复计时；runningSince 用 elapsedRealtime，重启后自动失效（返回 null）。 */
object FocusTimerStore {
    private const val PREFS = "focus_timer"
    private const val KEY_PHASE = "phase"
    private const val KEY_MODE = "mode"
    private const val KEY_PLANNED = "planned"
    private const val KEY_ACCUM = "accum"
    private const val KEY_RUNNING_SINCE = "running_since"
    private const val KEY_STARTED = "started_epoch"
    private const val KEY_STRICT = "strict"
    private const val KEY_INTERRUPTIONS = "interruptions"
    private const val KEY_BREAK = "is_break"
    private const val KEY_TASK = "task_id"
    private const val KEY_BOOT = "boot_count"

    data class Stored(val state: TimerState, val taskId: String?)

    fun save(context: Context, state: TimerState, taskId: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_PHASE, state.phase.name)
            putString(KEY_MODE, state.mode.name)
            putInt(KEY_PLANNED, state.plannedSeconds)
            putLong(KEY_ACCUM, state.accumulatedMillis)
            putLong(KEY_RUNNING_SINCE, state.runningSince ?: -1L)
            putLong(KEY_STARTED, state.startedAtEpoch)
            putBoolean(KEY_STRICT, state.strict)
            putInt(KEY_INTERRUPTIONS, state.interruptionCount)
            putBoolean(KEY_BREAK, state.isBreak)
            putString(KEY_TASK, taskId)
            putInt(KEY_BOOT, bootCount(context))
        }.apply()
    }

    fun load(context: Context): Stored? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // 重启后 elapsedRealtime 归零，旧快照不可恢复
        if (p.getInt(KEY_BOOT, -1) != bootCount(context)) {
            clear(context)
            return null
        }
        val phase = p.getString(KEY_PHASE, null)?.let { runCatching { TimerPhase.valueOf(it) }.getOrNull() }
            ?: return null
        if (phase == TimerPhase.IDLE || phase == TimerPhase.FINISHED) return null
        val mode = p.getString(KEY_MODE, null)?.let { runCatching { TimerMode.valueOf(it) }.getOrNull() }
            ?: TimerMode.COUNTDOWN
        val running = p.getLong(KEY_RUNNING_SINCE, -1L).takeIf { it >= 0 }
        if (phase == TimerPhase.RUNNING && running == null) return null
        // 单调时钟比快照还小，说明设备重启/时钟异常，旧计时不可恢复
        if (running != null && running > SystemClock.elapsedRealtime() + 5_000) {
            clear(context)
            return null
        }
        val state = TimerState(
            mode = mode,
            phase = phase,
            plannedSeconds = p.getInt(KEY_PLANNED, 1500),
            accumulatedMillis = p.getLong(KEY_ACCUM, 0L),
            runningSince = running,
            startedAtEpoch = p.getLong(KEY_STARTED, System.currentTimeMillis()),
            strict = p.getBoolean(KEY_STRICT, false),
            interruptionCount = p.getInt(KEY_INTERRUPTIONS, 0),
            isBreak = p.getBoolean(KEY_BREAK, false),
        )
        return Stored(state, p.getString(KEY_TASK, null))
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun bootCount(context: Context): Int =
        runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        }.getOrDefault(-1)
}
