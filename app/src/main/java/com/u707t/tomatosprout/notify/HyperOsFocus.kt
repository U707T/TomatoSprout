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
 * 不申请任何权限、不引入小米 SDK，也不反射系统属性（`persist.sys.feature.island`
 * 在 Android 9+ 属于非 SDK 接口，应用侧反射拿不到，读到的永远是 null）。
 * 只读官方文档给出的两处入口：
 *
 * 1. `Settings.System["notification_focus_protocol"]`
 *    —— 澎湃 OS 自己写入的焦点通知协议版本：1 = OS1、2 = OS2、3 = OS3（起支持岛）。
 *    澎湃 OS 4 上这个键可能整个缺失，所以用 `getString` 读并区分
 *    「未提供（-1）」与「读到了 0」，不把读不到当成不支持。
 * 2. `content://miui.statusbar.notification.public` 的 `canShowFocus`
 *    —— 系统里本 App 的「焦点通知」开关是否打开。没有该 Provider / 返回空 Bundle
 *    时给 null（未知），与「明确关闭」区分开。
 *
 * 两处都包在 runCatching 里：任何 ROM 差异最多退化成普通通知，不会崩。
 *
 * 注意：这些值**只进自检面板**，不作为「发不发焦点通知」的闸门 —— 读不到不等于不让用，
 * 真正的判定在 [LiveSurfacePolicy]：只要是小米机型就带 extras，显示与否交给系统。
 */
object HyperOsFocus {

    /** 焦点通知协议版本键。 */
    const val KEY_PROTOCOL = "notification_focus_protocol"

    /** 澎湃 OS 系统界面暴露的查询 Provider。 */
    const val AUTHORITY = "content://miui.statusbar.notification.public"

    /** 系统没有提供协议版本（澎湃 OS 4 常见）。 */
    const val PROTOCOL_UNKNOWN = -1

    /** 最低支持焦点通知的协议版本（OS2）。 */
    private const val PROTOCOL_FOCUS = 2

    /** 起支持超级岛的协议版本（OS3）。 */
    private const val PROTOCOL_ISLAND = 3

    /** 小米系机型：焦点通知 / 超级岛只在自家 ROM 上有意义。 */
    private val XIAOMI_BRANDS = setOf("xiaomi", "redmi", "poco")

    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer in XIAOMI_BRANDS || brand in XIAOMI_BRANDS
    }

    data class Support(
        /** -1 = 系统未提供；1/2 = 焦点通知；3+ = 超级岛。 */
        val protocol: Int,
        /** true = 已允许；false = 已关闭；null = 系统没提供查询接口。 */
        val canShowFocus: Boolean?,
    ) {
        val protocolKnown: Boolean get() = protocol >= 0
        val supportsFocus: Boolean get() = !protocolKnown || protocol >= PROTOCOL_FOCUS
        val supportsIsland: Boolean get() = !protocolKnown || protocol >= PROTOCOL_ISLAND

        /**
         * 诊断用的「看起来能用吗」。查询不到一律按能用处理 ——
         * 澎湃 OS 4 的云端白名单会让 `canShowFocus` 返回 false（或干脆没这个键），
         * 拿它当闸门就等于把上岛的路整条关掉。
         */
        val likelyAvailable: Boolean get() = supportsFocus && canShowFocus != false
    }

    fun probe(context: Context): Support = Support(
        protocol = protocol(context),
        canShowFocus = canShowFocus(context),
    )

    /** 诊断用的一行摘要，设置页与日志共用。 */
    fun describe(context: Context, support: Support = probe(context)): String = buildString {
        append("小米机型：").append(if (isXiaomiDevice()) "是" else "否")
        append("（").append(Build.MANUFACTURER).append('/').append(Build.BRAND).append("）\n")
        append("焦点协议：").append(
            when {
                !support.protocolKnown -> "系统未提供（OS4 常见，按支持处理）"
                support.supportsIsland -> "${support.protocol}（超级岛）"
                support.supportsFocus -> "${support.protocol}（焦点通知）"
                else -> "${support.protocol}（低于 OS2，仅普通通知）"
            },
        )
        append("\n")
        append("焦点通知开关：").append(
            when (support.canShowFocus) {
                true -> "已允许"
                false -> "已关闭（系统或云端白名单未放行）"
                null -> "系统未提供接口"
            },
        )
    }

    /**
     * 协议版本：读不到给 -1，而不是 0。
     *
     * 用 `getString` 是因为澎湃 OS 4 上这个设置项可能整个缺失；`getInt(..., 0)` 会把
     * 「缺失」和「OS1 的 0」混为一谈。
     */
    fun protocol(context: Context): Int = runCatching {
        Settings.System.getString(context.contentResolver, KEY_PROTOCOL)
            ?.trim()
            ?.toIntOrNull()
            ?: PROTOCOL_UNKNOWN
    }.getOrDefault(PROTOCOL_UNKNOWN)

    /**
     * 系统里本应用的焦点通知开关（对话是跨进程的，官方标明「耗时操作」，别在主线程循环调用）。
     *
     * 返回空 Bundle / 没有 `canShowFocus` 键时给 null：澎湃 OS 4 会按云端白名单拦截，
     * 但「查不到」和「明确关闭」要分开，前者仍值得试一帧。
     */
    fun canShowFocus(context: Context): Boolean? = runCatching {
        val bundle = context.contentResolver.call(
            Uri.parse(AUTHORITY),
            "canShowFocus",
            null,
            Bundle().apply { putString("package", context.packageName) },
        ) ?: return@runCatching null
        if (!bundle.containsKey("canShowFocus")) return@runCatching null
        bundle.getBoolean("canShowFocus", false)
    }.getOrNull()

    /** 应用的通知设置页：不同 ROM 的「焦点通知」开关都在这里。 */
    fun notificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
