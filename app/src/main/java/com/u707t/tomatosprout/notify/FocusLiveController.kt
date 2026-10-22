package com.u707t.tomatosprout.notify

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import com.u707t.tomatosprout.MainActivity
import com.u707t.tomatosprout.domain.TimerState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 专注「实时活动」的唯一出口。
 *
 * 具体用澎湃 OS 超级岛、Android 16 进度型通知，还是自定义 RemoteViews 卡片，
 * 由 [FocusLiveNotification] 按机型能力决定；这里只负责节流与真正发通知。
 *
 * 节流的意义：系统实时活动/超级岛自己会走秒，不需要每秒重发；
 * 只有普通卡片必须逐秒刷新文字。
 */
@Singleton
class FocusLiveController @Inject constructor(@ApplicationContext private val context: Context) {

    private val builder = FocusLiveNotification(context)
    private var lastKey: String? = null
    private var lastPostedAtMono = 0L

    @SuppressLint("MissingPermission") // 已通过 NotificationPermission.canPost 显式校验
    fun update(state: TimerState, nowMono: Long, title: String, richSurface: Boolean = true) {
        if (!NotificationPermission.canPost(context)) return
        val built = runCatching { builder.build(state, nowMono, title, richSurface) }.getOrNull() ?: return
        val now = SystemClock.elapsedRealtime()
        if (built.key == lastKey && now - lastPostedAtMono < built.minIntervalMillis) return
        val posted = runCatching {
            NotificationManagerCompat.from(context).notify(NotificationIds.FOCUS_LIVE, built.notification)
        }.isSuccess
        if (posted) {
            lastKey = built.key
            lastPostedAtMono = now
            LiveActivityDiagnostics.recordPosted(context, built.surface.name, built.key)
        }
    }

    fun cancel() {
        runCatching { NotificationManagerCompat.from(context).cancel(NotificationIds.FOCUS_LIVE) }
        lastKey = null
        lastPostedAtMono = 0L
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
