package com.u707t.tomatosprout.notify

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * 设置页里那行「系统实时活动状态」的来源。
 *
 * 三档能力各自对应不同的系统开关，把判断集中在这里，
 * 免得界面层到处写 `if (Build.VERSION.SDK_INT >= 36)`。
 */
object LiveActivityStatus {

    data class Info(
        val summary: String,
        /** 非空表示还有系统开关没打开，点一行可以直接跳过去。 */
        val settingsIntent: Intent?,
    )

    fun describe(context: Context, enabled: Boolean): Info {
        if (!enabled) {
            return Info("已关闭：专注时只显示普通通知卡片", null)
        }
        val focus = runCatching { HyperOsFocus.probe(context) }
            .getOrDefault(HyperOsFocus.Support(0, null))
        if (focus.supportsFocus) {
            val name = if (focus.supportsIsland) "超级岛" else "焦点通知"
            val settings = HyperOsFocus.notificationSettings(context)
            return when (focus.canShowFocus) {
                true -> Info("澎湃 OS $name · 已允许", null)
                false -> Info("澎湃 OS $name 未允许，点这里去开启", settings)
                null -> Info("澎湃 OS $name · 状态未知，点这里检查", settings)
            }
        }
        if (Build.VERSION.SDK_INT >= 36) {
            val allowed = runCatching {
                context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
            }.getOrDefault(true)
            return if (allowed) {
                Info("Android 实时更新 · 已允许", null)
            } else {
                Info("Android 实时更新未允许，点这里去开启", promotionSettings(context))
            }
        }
        return Info("当前系统只显示普通通知卡片", null)
    }

    private fun promotionSettings(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 36) return null
        return Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }
}
