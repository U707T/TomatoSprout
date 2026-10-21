package com.u707t.tomatosprout.ui.nav

import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data class FocusRoute(
    val taskId: String? = null,
    val timerMode: String? = null,
    val minutes: Int? = null,
)

@Serializable
data class StatsRoute(val taskId: String? = null, val date: String? = null)

@Serializable
data class CalendarRoute(val taskId: String? = null, val date: String? = null)

@Serializable
data object SettingsRoute

@Serializable
data class EditRoute(
    val taskId: String? = null,
    val prefillTitle: String? = null,
    val prefillType: String? = null,
    val prefillTimerMode: String? = null,
    val prefillMinutes: Int? = null,
)

@Serializable
data object TasksRoute

@Serializable
data object ArchiveRoute

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
