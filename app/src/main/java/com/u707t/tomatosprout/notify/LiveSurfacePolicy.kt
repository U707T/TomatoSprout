package com.u707t.tomatosprout.notify

import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** 一次专注在系统里的三种呈现方式，互斥、不叠加。 */
internal enum class LiveSurface {
    /** 澎湃 OS 焦点通知 / 超级岛（`miui.focus.*` extras）。 */
    HYPER_OS,

    /** Android 16 起的进度型通知（`Notification.ProgressStyle` + 常驻提升）。 */
    LIVE_UPDATE,

    /** 自绘 RemoteViews 卡片，所有系统都能显示。 */
    PLAIN,
}

internal data class LiveSurfaceDecision(
    val surface: LiveSurface,
    /** 给自检面板用的一句话，说明为什么走这一档。 */
    val reason: String,
)

/**
 * 选呈现方式的唯一入口。
 *
 * 遵循 v1.10.1 定下的原则：**不要把系统探测当闸门**。澎湃 OS 4 上
 * `notification_focus_protocol` 可能整个缺失、`canShowFocus` 又会因为云端白名单返回
 * false，一旦拿它们决定「发不发 extras」，就等于把上岛的路整条关掉。
 *
 * 所以判定只剩三件事：
 *
 * - 用户关掉了实时活动 → 普通卡片；
 * - 小米 / Redmi / POCO 机型，或用户勾了「强制焦点通知」→ 带焦点通知 extras，
 *   能不能显示、显示成焦点通知还是普通通知，交给系统；
 * - 其余设备：Android 16 且允许提升通知 → 实时更新，否则普通卡片。
 */
internal object LiveSurfacePolicy {

    fun decide(context: Context, richSurface: Boolean, forceFocus: Boolean): LiveSurfaceDecision {
        if (!richSurface) {
            return LiveSurfaceDecision(LiveSurface.PLAIN, "实时活动已关闭")
        }
        if (forceFocus) {
            return LiveSurfaceDecision(LiveSurface.HYPER_OS, "已开启「强制焦点通知」")
        }
        if (HyperOsFocus.isXiaomiDevice()) {
            return LiveSurfaceDecision(LiveSurface.HYPER_OS, "小米机型：带焦点通知参数")
        }
        if (Build.VERSION.SDK_INT >= 36 && canPromote(context)) {
            return LiveSurfaceDecision(LiveSurface.LIVE_UPDATE, "Android 16 实时更新可用")
        }
        return LiveSurfaceDecision(LiveSurface.PLAIN, "当前系统只支持普通通知卡片")
    }

    /** 用户是否允许「提升通知」；查不到时按允许处理，避免少发一帧。 */
    fun canPromote(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return runCatching {
            context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
        }.getOrDefault(true)
    }
}
