package com.u707t.tomatosprout.ui.nav

import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data object FocusRoute

@Serializable
data object StatsRoute

@Serializable
data object SettingsRoute

@Serializable
data class EditRoute(val taskId: String? = null)

@Serializable
data object TasksRoute

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
