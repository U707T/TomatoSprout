package com.u707t.tomatosprout.notify

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import com.u707t.tomatosprout.domain.TimerState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 专注「实时活动」的唯一出口。
 *
 * 具体用澎湃 OS 超级岛、Android 16 进度型通知，还是自定义 RemoteViews 卡片，
 * 由 [LiveSurfacePolicy] / [FocusLiveNotification] 按机型能力决定；这里只负责
 * 首帧判定、节流与真正发通知。
 *
 * 节流的意义：系统实时活动/超级岛自己会走秒，不需要每秒重发；
 * 只有普通卡片必须逐秒刷新文字。
 */
@Singleton
class FocusLiveController @Inject constructor(@ApplicationContext private val context: Context) {

    private val builder = FocusLiveNotification(context)
    private var lastKey: String? = null
    private var lastPostedAtMono = 0L

    /**
     * @param firstFrame 本次专注的第一帧。系统首次加入岛读 `islandFirstFloat`，后续更新读
     *   `enableFloat`；只有真正开始一轮时才让岛浮现，暂停/继续/结算都不再重复弹出。
     */
    @SuppressLint("MissingPermission") // 已通过 NotificationPermission.canPost 显式校验
    fun update(
        state: TimerState,
        nowMono: Long,
        title: String,
        richSurface: Boolean = true,
        forceFocus: Boolean = false,
        firstFrame: Boolean = lastKey == null,
    ) {
        if (!NotificationPermission.canPost(context)) return
        val built = runCatching {
            builder.build(state, nowMono, title, richSurface, forceFocus, firstFrame)
        }.getOrNull() ?: return
        val now = SystemClock.elapsedRealtime()
        if (built.key == lastKey && now - lastPostedAtMono < built.minIntervalMillis) return
        val posted = runCatching {
            if (firstFrame) {
                // 新的一轮开始：上一轮的完成提示不该继续挂在通知栏
                NotificationManagerCompat.from(context).cancel(NotificationIds.FOCUS_DONE)
            }
            NotificationManagerCompat.from(context).notify(NotificationIds.FOCUS_LIVE, built.notification)
        }.isSuccess
        if (posted) {
            lastKey = built.key
            lastPostedAtMono = now
            LiveActivityDiagnostics.recordPosted(built.surface.name, built.reason, built.key)
        }
    }

    /**
     * 设置页的自检按钮：发一条独立 id 的假专注，60 秒后自己撤掉。
     * 用独立 id 是为了不打断正在进行的真实专注；自检时强制走焦点通知。
     */
    @SuppressLint("MissingPermission")
    fun postTest(state: TimerState, nowMono: Long, title: String): Boolean {
        if (!NotificationPermission.canPost(context)) return false
        val built = runCatching {
            builder.build(
                state = state,
                nowMono = nowMono,
                title = title,
                richSurface = true,
                forceFocus = true,
                firstFrame = true,
            )
        }.getOrNull() ?: return false
        val posted = runCatching {
            NotificationManagerCompat.from(context).notify(NotificationIds.FOCUS_TEST, built.notification)
        }.isSuccess
        if (posted) {
            LiveActivityDiagnostics.recordPosted("TEST/${built.surface.name}", built.reason, built.key)
        }
        return posted
    }

    fun cancelTest() {
        runCatching { NotificationManagerCompat.from(context).cancel(NotificationIds.FOCUS_TEST) }
    }

    /**
     * 专注完成 / 休息结束的一次性提示。没有权限时静默跳过，不影响结算。
     */
    @SuppressLint("MissingPermission")
    fun notifyCompleted(title: String, minutes: Int, isBreak: Boolean) {
        if (!NotificationPermission.canPost(context)) return
        runCatching {
            NotificationManagerCompat.from(context).notify(
                NotificationIds.FOCUS_DONE,
                builder.buildFinished(title, isBreak, minutes),
            )
        }
    }

    fun cancelCompleted() {
        runCatching { NotificationManagerCompat.from(context).cancel(NotificationIds.FOCUS_DONE) }
    }

    fun cancel() {
        runCatching {
            val manager = NotificationManagerCompat.from(context)
            manager.cancel(NotificationIds.FOCUS_LIVE)
            manager.cancel(NotificationIds.FOCUS_DONE)
        }
        lastKey = null
        lastPostedAtMono = 0L
    }
}

/**
 * 通知 / 岛上的按钮 → 广播 → 记下待处理命令，由专注页的低频轮询消费。
 *
 * 这里**不**拉起 Activity：Android 12 起禁止「通知/广播里启动 Activity」的 trampoline，
 * 真拉也只会被系统静默拦掉；而且暂停一下番茄就跳回 App 本身也很突兀。
 * App 进程不在时命令留在 SharedPreferences 里，下次打开专注页再执行，不会丢。
 */
class FocusActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        FocusCommandStore.put(context, action)
        if (action == ACTION_STOP) {
            NotificationManagerCompat.from(context).cancel(NotificationIds.FOCUS_LIVE)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.u707t.tomatosprout.focus.TOGGLE"
        const val ACTION_STOP = "com.u707t.tomatosprout.focus.STOP"
        /** 延长 5 分钟：只动状态，不重建计时。 */
        const val ACTION_EXTEND = "com.u707t.tomatosprout.focus.EXTEND"
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
