package com.u707t.tomatosprout.notify

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「实时活动」自检。
 *
 * 出岛链条上有几处不由应用控制：小米是否给本应用开放焦点通知（澎湃 OS 4 还要过云端白名单）、
 * 系统版本是否认识模板、用户是否允许提升通知。任何一处没对，表现都是「什么都没发生」。
 *
 * 所以这里把判定依据、本次档位、上次发送结果全部摊开，并提供一条 60 秒的测试岛，
 * 让「为什么没上岛」可以当场定位，而不是靠猜。
 */
object LiveActivityDiagnostics {

    @Volatile
    private var lastPost: String = "尚未发送"

    fun recordPosted(surface: String, reason: String, key: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val shortKey = if (key.length > 48) key.take(47) + "…" else key
        lastPost = "$time · $surface · $reason · $shortKey"
    }

    fun report(context: Context, enabled: Boolean, forceFocus: Boolean = false): String {
        val support = HyperOsFocus.probe(context)
        val decision = LiveSurfacePolicy.decide(context, enabled, forceFocus)
        return buildString {
            append(HyperOsFocus.describe(context, support))
            append("\n")
            append("Android：").append(Build.VERSION.RELEASE)
            append("（SDK ").append(Build.VERSION.SDK_INT)
            if (Build.VERSION.SDK_INT >= 36) {
                append(" · full ").append(Build.VERSION.SDK_INT_FULL)
            }
            append("）\n")
            if (Build.VERSION.SDK_INT >= 36) {
                append("允许提升通知：")
                append(if (LiveSurfacePolicy.canPromote(context)) "是" else "否")
                append("\n")
            }
            append("通知权限：")
            append(if (NotificationPermission.canPost(context)) "已允许" else "未允许")
            append("\n")
            append("实时活动开关：").append(if (enabled) "开" else "关").append("\n")
            append("强制焦点通知：").append(if (forceFocus) "开" else "关").append("\n")
            append("本次走：").append(surfaceLabel(decision.surface)).append("\n")
            append("判定原因：").append(decision.reason).append("\n")
            append("最近一次发送：").append(lastPost)
            hint(support, forceFocus)?.let { append("\n提示：").append(it) }
        }
    }

    internal fun surfaceLabel(surface: LiveSurface): String = when (surface) {
        LiveSurface.HYPER_OS -> "澎湃 OS 焦点通知 / 超级岛"
        LiveSurface.LIVE_UPDATE -> "Android 16 实时更新"
        LiveSurface.PLAIN -> "普通通知卡片"
    }

    private fun hint(support: HyperOsFocus.Support, forceFocus: Boolean): String? {
        if (!HyperOsFocus.isXiaomiDevice()) {
            return if (forceFocus) {
                "已强制按小米焦点通知发送；非小米 ROM 会忽略这些参数，不影响通知本身"
            } else {
                null
            }
        }
        return when {
            forceFocus ->
                "已无视机型与开关探测强制发送；系统若仍然拦截，表现就是一条普通通知，计时与结算不受影响"
            support.canShowFocus == false ->
                "系统关闭了本应用的焦点通知：通知设置 → 番茄芽 → 焦点通知，打开后再试。澎湃 OS 4 还会按云端白名单放行，未收录的第三方应用打不开这个开关"
            support.canShowFocus == null ->
                "系统没有提供焦点通知开关。澎湃 OS 4 按云端白名单放行，未收录的第三方应用不会出岛，但通知仍会正常显示"
            else ->
                "若已允许仍不出岛，多半是澎湃 OS 云端白名单未收录本应用（第三方常见），需要系统模块解锁；不影响通知、计时与结算"
        }
    }

    /** 需要跳系统设置时返回 Intent，否则 null。 */
    fun settingsIntent(context: Context): Intent? {
        if (!NotificationPermission.canPost(context)) {
            return HyperOsFocus.notificationSettings(context)
        }
        if (HyperOsFocus.isXiaomiDevice()) {
            // 焦点通知开关挂在应用自己的通知设置页
            return HyperOsFocus.notificationSettings(context)
        }
        if (Build.VERSION.SDK_INT >= 36) {
            return runCatching {
                if (context.getSystemService(NotificationManager::class.java)
                        .canPostPromotedNotifications()
                ) {
                    null
                } else {
                    Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            }.getOrNull()
        }
        return null
    }
}
