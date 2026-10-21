package com.u707t.tomatosprout.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.u707t.tomatosprout.MainActivity
import com.u707t.tomatosprout.R
import com.u707t.tomatosprout.domain.TimerEngine
import com.u707t.tomatosprout.domain.TimerMode
import com.u707t.tomatosprout.domain.TimerPhase
import com.u707t.tomatosprout.domain.TimerState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 安卓「灵动岛」效果的唯一实现：高优先级通知 + 自定义 RemoteViews。
 * 不申请 SYSTEM_ALERT_WINDOW / POST_PROMOTED_NOTIFICATIONS，不使用覆盖窗口。
 * 通知权限被拒绝时整体静默关闭。
 */
@Singleton
class FocusLiveController @Inject constructor(@ApplicationContext private val context: Context) {

    @SuppressLint("MissingPermission") // 已通过 NotificationPermission.canPost 显式校验
    fun update(state: TimerState, nowMono: Long, title: String) {
        if (!NotificationPermission.canPost(context)) return
        val views = RemoteViews(context.packageName, R.layout.notification_focus)
        val elapsed = state.elapsedSeconds(nowMono)
        val displaySeconds = when (state.mode) {
            TimerMode.COUNTDOWN -> (state.remainingMillis(nowMono) / 1000L).toInt()
            TimerMode.COUNTUP -> elapsed
        }
        val progress = if (state.plannedSeconds <= 0) 0 else {
            ((elapsed.toFloat() / state.plannedSeconds) * 100f).toInt().coerceIn(0, 100)
        }
        views.setTextViewText(R.id.focus_title, title.ifBlank { if (state.isBreak) "休息一下" else "专注中" })
        views.setTextViewText(R.id.focus_time, TimerEngine.formatClock(displaySeconds.toLong()))
        views.setProgressBar(R.id.focus_progress, 100, progress, false)
        views.setImageViewResource(
            R.id.focus_toggle,
            if (state.phase == TimerPhase.PAUSED) R.drawable.ic_play else R.drawable.ic_pause,
        )
        views.setOnClickPendingIntent(R.id.focus_toggle, action(FocusActionReceiver.ACTION_TOGGLE, 1))
        views.setOnClickPendingIntent(R.id.focus_stop, action(FocusActionReceiver.ACTION_STOP, 2))

        val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "专注中" })
            .setContentText(TimerEngine.formatClock(displaySeconds.toLong()))
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(ContentIntents.openApp(context, MainActivity.NAV_FOCUS))

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NotificationIds.FOCUS_LIVE, builder.build())
        }
    }

    fun cancel() {
        runCatching { NotificationManagerCompat.from(context).cancel(NotificationIds.FOCUS_LIVE) }
    }

    /**
     * 直接用 Activity PendingIntent：Android 12+ 限制通知 trampoline（广播/服务里拉起 Activity），
     * 用广播再 startActivity 会被系统拦截。
     */
    private fun action(action: String, request: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE, MainActivity.NAV_FOCUS)
            putExtra(MainActivity.EXTRA_FOCUS_ACTION, action)
        }
        return PendingIntent.getActivity(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** 通知按钮 → 广播 → 记录待处理命令并回到 App；App 不在前台时也不会丢命令。 */
class FocusActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        FocusCommandStore.put(context, action)
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_NAVIGATE, MainActivity.NAV_FOCUS)
                },
            )
        }
        if (action == ACTION_STOP) {
            NotificationManagerCompat.from(context).cancel(NotificationIds.FOCUS_LIVE)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.u707t.tomatosprout.focus.TOGGLE"
        const val ACTION_STOP = "com.u707t.tomatosprout.focus.STOP"
    }
}

object FocusCommandStore {
    private const val PREFS = "focus_commands"
    private const val KEY = "pending"

    fun put(context: Context, action: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, action).apply()
    }

    fun consume(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY, null)
        if (value != null) prefs.edit().remove(KEY).apply()
        return value
    }
}
