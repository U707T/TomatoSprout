package com.u707t.tomatosprout.notify

import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** 一次专注在系统里的三种呈现方式，互斥、不叠加。 */
internal enum class LiveSurface {
    /** 澎湃 OS ≤3 焦点通知 / 超级岛（`miui.focus.*` extras）。 */
    HYPER_OS,

    /** Android 16 起的「实时更新 / 提升通知」，澎湃 OS 4 的岛由系统按这条通道渲染。 */
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
 * 澎湃 OS 4 换了上岛的通道：系统不再看第三方应用的 `miui.focus.*`（那条路要过云端白名单），
 * 而是把 **Android 16 的实时更新（promoted ongoing notification）** 直接画进岛。
 * 参考实现（Live Clock 2.3.0）里没有任何 `miui.focus` 字样，只有
 * `android.requestPromotedOngoing` + `android.shortCriticalText` 两个 extras。
 *
 * 所以判定是：
 *
 * - 用户关掉实时活动 → 普通卡片；
 * - 强制焦点通知 → 始终走厂商参数（给装了白名单解锁模块、或 OS3 及更早的机型）；
 * - Android 16+ 且允许提升通知 → 实时更新（澎湃 OS 4 的默认路径）；
 * - 其余小米机型 → 焦点通知参数（OS2 / OS3 只认这条）；
 * - 都不是 → 普通卡片。
 *
 * 仍然不拿 `notification_focus_protocol` / `canShowFocus` 当闸门：那是白名单与系统开关的
 * 结果，不是我们该不该发参数的理由。
 */
internal object LiveSurfacePolicy {

    fun decide(context: Context, richSurface: Boolean, forceFocus: Boolean): LiveSurfaceDecision {
        if (!richSurface) {
            return LiveSurfaceDecision(LiveSurface.PLAIN, "实时活动已关闭")
        }
        if (forceFocus) {
            return LiveSurfaceDecision(LiveSurface.HYPER_OS, "已开启「强制焦点通知」")
        }
        val xiaomi = HyperOsFocus.isXiaomiDevice()
        if (Build.VERSION.SDK_INT >= 36 && canPromote(context)) {
            return LiveSurfaceDecision(
                LiveSurface.LIVE_UPDATE,
                if (xiaomi) "澎湃 OS 4：系统实时更新（岛由系统渲染）" else "Android 16 实时更新可用",
            )
        }
        if (xiaomi) {
            return LiveSurfaceDecision(
                LiveSurface.HYPER_OS,
                if (Build.VERSION.SDK_INT >= 36) {
                    "小米机型，但系统未允许提升通知：退回焦点通知参数"
                } else {
                    "澎湃 OS ≤3：焦点通知参数"
                },
            )
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
