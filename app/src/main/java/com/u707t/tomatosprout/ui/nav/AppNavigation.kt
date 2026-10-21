package com.u707t.tomatosprout.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.u707t.tomatosprout.domain.AppSettings
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.ui.badges.BadgesScreen
import com.u707t.tomatosprout.ui.edit.HabitEditScreen
import com.u707t.tomatosprout.ui.focus.FocusTimerScreen
import com.u707t.tomatosprout.ui.history.HistoryScreen
import com.u707t.tomatosprout.ui.settings.AboutScreen
import com.u707t.tomatosprout.ui.settings.SettingsScreen
import com.u707t.tomatosprout.ui.settings.SyncLogsScreen
import com.u707t.tomatosprout.ui.settings.WebDavScreen
import com.u707t.tomatosprout.ui.stats.StatsScreen
import com.u707t.tomatosprout.ui.tasks.TodoScreen
import com.u707t.tomatosprout.ui.theme.Tomato
import com.u707t.tomatosprout.ui.theme.TomatoTheme
import com.u707t.tomatosprout.ui.wishlist.WishlistScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(settingsRepo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
}

@Composable
fun TomatoApp(
    initialNav: String?,
    onNavConsumed: () -> Unit,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    TomatoTheme(settings.themeMode) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val destination = entry?.destination
        val isTab = destination?.let {
            it.hasRoute<TodoRoute>() || it.hasRoute<StatsRoute>() || it.hasRoute<SettingsRoute>()
        } ?: true

        fun goTab(route: Any) {
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        LaunchedEffect(initialNav) {
            if (initialNav == "focus") {
                nav.navigate(FocusRoute()) { launchSingleTop = true }
                onNavConsumed()
            }
        }

        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = TodoRoute,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable<TodoRoute> {
                    TodoScreen(
                        onStartTask = { task ->
                            nav.navigate(
                                FocusRoute(
                                    taskId = task.id,
                                    timerMode = task.timerMode,
                                    minutes = task.focusMinutes,
                                ),
                            )
                        },
                        onEditTask = { id -> nav.navigate(EditRoute(taskId = id)) },
                        onAdvancedAdd = { state ->
                            nav.navigate(
                                EditRoute(
                                    prefillTitle = state.title,
                                    prefillType = state.type.taskType.name,
                                    prefillTimerMode = state.timer.storage,
                                    prefillMinutes = state.durationMinutes,
                                ),
                            )
                        },
                        onOpenHistory = { taskId ->
                            nav.navigate(HistoryRoute(taskId = taskId, date = LocalDate.now().toString()))
                        },
                        onOpenStats = { taskId ->
                            nav.navigate(StatsRoute(taskId = taskId, date = LocalDate.now().toString()))
                        },
                    )
                }
                composable<StatsRoute> {
                    StatsScreen(
                        onAddHabit = { nav.navigate(EditRoute()) },
                        onOpenBadges = { nav.navigate(BadgesRoute) },
                        onOpenHistory = { date, taskId ->
                            nav.navigate(HistoryRoute(taskId = taskId, date = date?.toString()))
                        },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onOpenWebDav = { nav.navigate(WebDavRoute) },
                        onOpenBadges = { nav.navigate(BadgesRoute) },
                        onOpenWishlist = { nav.navigate(WishlistRoute) },
                        onOpenHistory = { nav.navigate(HistoryRoute()) },
                        onOpenAbout = { nav.navigate(AboutRoute) },
                    )
                }
                composable<HistoryRoute>(
                    enterTransition = { fadeIn(tween(180)) },
                    exitTransition = { fadeOut(tween(150)) },
                    popEnterTransition = { fadeIn(tween(180)) },
                    popExitTransition = { fadeOut(tween(150)) },
                ) {
                    HistoryScreen(onBack = { nav.popBackStack() })
                }
                composable<FocusRoute>(
                    enterTransition = { fadeIn(tween(240)) },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(240)) },
                    popExitTransition = { fadeOut(tween(180)) },
                ) {
                    FocusTimerScreen(onExit = { nav.popBackStack() })
                }
                composable<EditRoute> {
                    HabitEditScreen(onBack = { nav.popBackStack() })
                }
                composable<BadgesRoute> {
                    BadgesScreen(onBack = { nav.popBackStack() })
                }
                composable<WebDavRoute> {
                    WebDavScreen(
                        onBack = { nav.popBackStack() },
                        onOpenLogs = { nav.navigate(SyncLogsRoute) },
                    )
                }
                composable<SyncLogsRoute> {
                    SyncLogsScreen(onBack = { nav.popBackStack() })
                }
                composable<WishlistRoute> {
                    WishlistScreen(onBack = { nav.popBackStack() })
                }
                composable<AboutRoute> {
                    AboutScreen(onBack = { nav.popBackStack() })
                }
            }

            if (isTab) {
                NavigationBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    NavItem(
                        label = "任务",
                        icon = Icons.Default.List,
                        selected = destination?.hasRoute<TodoRoute>() == true,
                    ) { goTab(TodoRoute) }
                    NavItem(
                        label = "统计",
                        icon = Icons.Default.DateRange,
                        selected = destination?.hasRoute<StatsRoute>() == true,
                    ) { goTab(StatsRoute()) }
                    NavItem(
                        label = "我的",
                        icon = Icons.Default.Settings,
                        selected = destination?.hasRoute<SettingsRoute>() == true,
                    ) { goTab(SettingsRoute) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Tomato,
            selectedTextColor = Tomato,
            indicatorColor = Tomato.copy(alpha = 0.12f),
        ),
    )
}
