package com.u707t.tomatosprout.notify

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * 设置页里那行「系统实时活动状态」的来源。
 *
 * 三种呈现各自对应不同的系统开关，判断集中在 [LiveSurfacePolicy]，
 * 这里只负责把它翻译成一句人话 + 需要跳转的设置页。
 */
object LiveActivityStatus {

    data class Info(
        val summary: String,
        /** 非空表示还有系统开关没打开，点一行可以直接跳过去。 */
        val settingsIntent: Intent?,
    )

    fun describe(
        context: Context,
        enabled: Boolean,
        forceFocus: Boolean = false,
    ): Info {
        if (!enabled) {
            return Info("已关闭：专注时只显示普通通知卡片", null)
        }
        val decision = LiveSurfacePolicy.decide(context, enabled, forceFocus)
        return when (decision.surface) {
            LiveSurface.LIVE_UPDATE -> {
                if (LiveSurfacePolicy.canPromote(context)) {
                    Info(
                        if (HyperOsFocus.isXiaomiDevice()) {
                            "澎湃 OS 4 实时更新 · 已允许（岛走这条）"
                        } else {
                            "Android 实时更新 · 已允许"
                        },
                        null,
                    )
                } else {
                    Info("实时更新未允许，点这里去开启", promotionSettings(context))
                }
            }
            LiveSurface.HYPER_OS -> {
                val focus = runCatching { HyperOsFocus.probe(context) }
                    .getOrDefault(HyperOsFocus.Support(HyperOsFocus.PROTOCOL_UNKNOWN, null))
                val name = when {
                    !focus.protocolKnown -> "焦点通知 / 超级岛"
                    focus.supportsIsland -> "超级岛"
                    else -> "焦点通知"
                }
                val settings = HyperOsFocus.notificationSettings(context)
                when (focus.canShowFocus) {
                    true -> Info("澎湃 OS $name · 已允许", null)
                    false -> Info("澎湃 OS $name 未允许，点这里去开启", settings)
                    else -> Info("澎湃 OS $name · 状态未知（OS4 由白名单决定），点这里检查", settings)
                }
            }
            LiveSurface.PLAIN -> Info("当前系统只显示普通通知卡片", null)
        }
    }

    private fun promotionSettings(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 36) return null
        return Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }
}
