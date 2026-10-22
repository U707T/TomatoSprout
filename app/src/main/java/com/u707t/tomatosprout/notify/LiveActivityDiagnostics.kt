package com.u707t.tomatosprout.notify

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「实时活动」自检。
 *
 * 出岛链条上有三处不由应用控制：小米是否给本应用开放焦点通知、系统版本是否认识模板、
 * 用户是否允许提升通知。任何一处没对，表现都是「什么都没发生」。
 *
 * 所以这里把判定依据、上次发送结果全部摊开，并提供一条 60 秒的测试岛，
 * 让「为什么没上岛」可以当场定位，而不是靠猜。
 */
object LiveActivityDiagnostics {

    @Volatile
    private var lastPost: String = "尚未发送"

    fun recordPosted(context: Context, surface: String, key: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        lastPost = "$time · $surface · $key"
    }

    fun lastPostText(): String = lastPost

    fun report(context: Context, enabled: Boolean): String {
        val support = HyperOsFocus.probe(context)
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
                append(
                    if (runCatching {
                            context.getSystemService(NotificationManager::class.java)
                                .canPostPromotedNotifications()
                        }.getOrDefault(false)
                    ) {
                        "是"
                    } else {
                        "否"
                    },
                )
                append("\n")
            }
            append("通知权限：")
            append(if (NotificationPermission.canPost(context)) "已允许" else "未允许")
            append("\n")
            append("实时活动开关：").append(if (enabled) "开" else "关").append("\n")
            append("本次走：").append(describeSurface(context, enabled)).append("\n")
            append("最近一次发送：").append(lastPost)
        }
    }

    /** 当前会走哪一档，和 [FocusLiveNotification] 的判定保持一致。 */
    fun describeSurface(context: Context, enabled: Boolean): String = when {
        !enabled -> "普通通知卡片（实时活动已关闭）"
        HyperOsFocus.isXiaomiDevice() -> "澎湃 OS 焦点通知 / 超级岛"
        Build.VERSION.SDK_INT >= 36 -> "Android 16 实时更新"
        else -> "普通通知卡片（系统不支持）"
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
                    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            }.getOrNull()
        }
        return null
    }
}
