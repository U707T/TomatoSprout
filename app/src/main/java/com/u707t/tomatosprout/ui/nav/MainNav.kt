package com.u707t.tomatosprout.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import androidx.navigation.compose.rememberNavController
import com.u707t.tomatosprout.domain.AppSettings
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.ui.badges.BadgesScreen
import com.u707t.tomatosprout.ui.calendar.CalendarTimelineScreen
import com.u707t.tomatosprout.ui.edit.HabitEditScreen
import com.u707t.tomatosprout.ui.focus.FocusTimerScreen
import com.u707t.tomatosprout.ui.settings.AboutScreen
import com.u707t.tomatosprout.ui.settings.SettingsScreen
import com.u707t.tomatosprout.ui.settings.SyncLogsScreen
import com.u707t.tomatosprout.ui.settings.WebDavScreen
import com.u707t.tomatosprout.ui.stats.StatsScreen
import com.u707t.tomatosprout.ui.tasks.TaskListScreen
import com.u707t.tomatosprout.ui.tasks.TodoScreen
import com.u707t.tomatosprout.ui.theme.Tomato
import com.u707t.tomatosprout.ui.theme.TomatoTheme
import com.u707t.tomatosprout.ui.today.TodayScreen
import com.u707t.tomatosprout.ui.wishlist.WishlistScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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
            it.hasRoute<TodayRoute>() || it.hasRoute<TasksRoute>() ||
                it.hasRoute<StatsRoute>() || it.hasRoute<SettingsRoute>()
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
                startDestination = TodayRoute,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable<TodayRoute> {
                    TodayScreen(
                        onAddHabit = { nav.navigate(EditRoute()) },
                        onEditTask = { id -> nav.navigate(EditRoute(id)) },
                        onOpenTasks = { nav.navigate(TasksRoute) },
                        onOpenCalendar = { nav.navigate(CalendarRoute()) },
                    )
                }
                composable<FocusRoute> {
                    FocusTimerScreen(onExit = { nav.navigate(TodayRoute) { popUpTo(TodayRoute) } })
                }
                composable<StatsRoute> { entry ->
                    val route = entry.toRoute<StatsRoute>()
                    StatsScreen(
                        onAddHabit = { nav.navigate(EditRoute()) },
                        onOpenBadges = { nav.navigate(BadgesRoute) },
                        onOpenCalendar = { date, taskId ->
                            nav.navigate(CalendarRoute(taskId = taskId, date = date.toString()))
                        },
                    )
                }
                composable<CalendarRoute> {
                    CalendarTimelineScreen(onBack = { nav.popBackStack() })
                }
                composable<TasksRoute> {
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
                        onEditTask = { id -> nav.navigate(EditRoute(id)) },
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
                        onOpenArchive = { nav.navigate(ArchiveRoute) },
                        onOpenHistory = { taskId ->
                            nav.navigate(CalendarRoute(taskId = taskId, date = java.time.LocalDate.now().toString()))
                        },
                        onOpenStats = { taskId ->
                            nav.navigate(StatsRoute(taskId = taskId, date = java.time.LocalDate.now().toString()))
                        },
                    )
                }
                composable<ArchiveRoute> {
                    TaskListScreen(
                        onBack = { nav.popBackStack() },
                        onEdit = { id -> nav.navigate(EditRoute(id)) },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onOpenWebDav = { nav.navigate(WebDavRoute) },
                        onOpenBadges = { nav.navigate(BadgesRoute) },
                        onOpenWishlist = { nav.navigate(WishlistRoute) },
                        onOpenTasks = { nav.navigate(TasksRoute) },
                        onOpenAbout = { nav.navigate(AboutRoute) },
                    )
                }
                composable<EditRoute> {
                    HabitEditScreen(onBack = { nav.popBackStack() })
                }
                composable<TasksRoute> {
                    TaskListScreen(
                        onBack = { nav.popBackStack() },
                        onEdit = { id -> nav.navigate(EditRoute(id)) },
                    )
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
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                ) {
                    NavItem(
                        label = "今日",
                        icon = Icons.Default.Home,
                        selected = destination?.hasRoute<TodayRoute>() == true,
                    ) { goTab(TodayRoute) }
                    NavItem(
                        label = "任务",
                        icon = Icons.Default.List,
                        selected = destination?.hasRoute<TasksRoute>() == true,
                    ) { goTab(TasksRoute) }
                    NavItem(
                        label = "专注",
                        icon = Icons.Default.PlayArrow,
                        selected = false,
                    ) { goTab(FocusRoute()) }
                    NavItem(
                        label = "统计",
                        icon = Icons.Default.DateRange,
                        selected = destination?.hasRoute<StatsRoute>() == true,
                    ) { goTab(StatsRoute) }
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
