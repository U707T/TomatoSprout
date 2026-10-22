package com.u707t.tomatosprout.ui.nav

import kotlinx.serialization.Serializable

/**
 * v1.6 路由：
 * - 底部导航为 Todo / Stats / Settings，中间另有「专注」动作入口；
 * - History 是二级页，从统计页或任务长按面板进入；
 * - Focus 是全屏页，不显示底栏。
 */
@Serializable
data object TodoRoute

@Serializable
data class StatsRoute(
    val taskId: String? = null,
    val date: String? = null,
)

@Serializable
data object SettingsRoute

@Serializable
data class HistoryRoute(
    val taskId: String? = null,
    val date: String? = null,
)

@Serializable
data class FocusRoute(
    val taskId: String? = null,
    val timerMode: String? = null,
    val minutes: Int? = null,
)

@Serializable
data class EditRoute(
    val taskId: String? = null,
    val prefillTitle: String? = null,
    val prefillType: String? = null,
    val prefillTimerMode: String? = null,
    val prefillMinutes: Int? = null,
)

@Serializable
data object BadgesRoute

@Serializable
data object WebDavRoute

@Serializable
data object SyncLogsRoute

@Serializable
data object WishlistRoute

@Serializable
data object AboutRoute
