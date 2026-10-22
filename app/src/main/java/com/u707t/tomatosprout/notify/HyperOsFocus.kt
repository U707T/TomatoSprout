package com.u707t.tomatosprout.notify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings

/**
 * 小米澎湃 OS「焦点通知 / 超级岛」能力探测。
 *
 * 不申请任何权限、不引入小米 SDK，只读两处公开可见的入口：
 *
 * 1. `Settings.System.getInt("notification_focus_protocol")`
 *    —— 澎湃 OS 自己写入的焦点通知协议版本，读不到就是 0（非小米设备）。
 *    2 = 焦点通知（OS2），3 及以上 = 超级岛（OS3 / OS4）。
 * 2. `content://miui.statusbar.notification.public` 的 `canShowFocus` 调用
 *    —— 系统里本 App 的「焦点通知」开关是否打开。没有该 Provider 时返回 null，
 *    与「明确关闭」区分开，避免把「查不到」当成「不让用」。
 *
 * 两者都包在 runCatching 里：任何 ROM 差异最多退化成普通通知，不会崩。
 */
object HyperOsFocus {

    /** 焦点通知协议版本键。 */
    const val KEY_PROTOCOL = "notification_focus_protocol"

    /** 澎湃 OS 系统界面暴露的查询 Provider。 */
    const val AUTHORITY = "content://miui.statusbar.notification.public"

    /** 最低支持焦点通知的协议版本（OS2）。 */
    private const val PROTOCOL_FOCUS = 2

    /** 最低支持超级岛的协议版本（OS3）。 */
    private const val PROTOCOL_ISLAND = 3

    data class Support(
        /** 0 = 不支持；1/2 = 焦点通知；3+ = 超级岛。 */
        val protocol: Int,
        /** true = 已允许；false = 已关闭；null = 系统没提供查询接口。 */
        val canShowFocus: Boolean?,
    ) {
        val supportsFocus: Boolean get() = protocol >= PROTOCOL_FOCUS
        val supportsIsland: Boolean get() = protocol >= PROTOCOL_ISLAND

        /** 只有「明确关闭」才算不可用；查不到时仍尝试发焦点通知，失败也只是退回普通通知。 */
        val usable: Boolean get() = supportsFocus && canShowFocus != false
    }

    fun probe(context: Context): Support = Support(
        protocol = protocol(context),
        canShowFocus = canShowFocus(context),
    )

    fun protocol(context: Context): Int = runCatching {
        Settings.System.getInt(context.contentResolver, KEY_PROTOCOL, 0)
    }.getOrDefault(0)

    fun supportsIsland(context: Context): Boolean = protocol(context) >= PROTOCOL_ISLAND

    fun canShowFocus(context: Context): Boolean? = runCatching {
        val bundle = context.contentResolver.call(
            Uri.parse(AUTHORITY),
            "canShowFocus",
            null,
            Bundle().apply { putString("package", context.packageName) },
        ) ?: return@runCatching null
        bundle.getBoolean("canShowFocus", false)
    }.getOrNull()

    /**
     * 小米「通知设置」页。澎湃 OS 的「焦点通知」开关就挂在本应用的通知设置里，
     * 用标准 Intent 打开，不猜厂商私有 Activity。
     */
    fun notificationSettings(context: Context): Intent = Intent(
        Settings.ACTION_APP_NOTIFICATION_SETTINGS,
    ).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            putExtra("app_package", context.packageName)
            putExtra("app_uid", context.applicationInfo.uid)
        }
    }
}
