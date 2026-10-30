package com.u707t.tomatosprout.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
import com.u707t.tomatosprout.ui.feedback.FeedbackController
import com.u707t.tomatosprout.ui.feedback.LocalFeedback
import com.u707t.tomatosprout.ui.focus.FocusTimerScreen
import com.u707t.tomatosprout.ui.history.HistoryScreen
import com.u707t.tomatosprout.ui.settings.AboutScreen
import com.u707t.tomatosprout.ui.settings.SettingsScreen
import com.u707t.tomatosprout.ui.settings.SyncLogsScreen
import com.u707t.tomatosprout.ui.settings.WebDavScreen
import com.u707t.tomatosprout.ui.stats.StatsScreen
import com.u707t.tomatosprout.ui.tasks.TodoScreen
import com.u707t.tomatosprout.ui.theme.AccentTheme
import com.u707t.tomatosprout.ui.theme.Motion
import com.u707t.tomatosprout.ui.theme.Sizes
import com.u707t.tomatosprout.ui.theme.TomatoTheme
import com.u707t.tomatosprout.ui.wishlist.WishlistScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    val feedback: FeedbackController,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** 首页「排序」弹层里可以顺手切换简约模式，不必绕到设置页。 */
    fun setSimple(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { if (it.simpleMode == enabled) it else it.copy(simpleMode = enabled) }
        }
    }
}

@Composable
fun TomatoApp(
    initialNav: String?,
    onNavConsumed: () -> Unit,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalFeedback provides viewModel.feedback) {
    TomatoTheme(
        mode = settings.themeMode,
        accent = AccentTheme.of(settings.accent),
    ) {
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
                        simpleMode = settings.simpleMode,
                        onSimpleModeChange = viewModel::setSimple,
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
                composable<EditRoute>(
                enterTransition = {
                    fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 14 }
                },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 14 }
                },
            ) {
                    HabitEditScreen(onBack = { nav.popBackStack() })
                }
                composable<BadgesRoute>(
                enterTransition = {
                    fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 14 }
                },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 14 }
                },
            ) {
                    BadgesScreen(onBack = { nav.popBackStack() })
                }
                composable<WebDavRoute>(
                enterTransition = {
                    fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 14 }
                },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 14 }
                },
            ) {
                    WebDavScreen(
                        onBack = { nav.popBackStack() },
                        onOpenLogs = { nav.navigate(SyncLogsRoute) },
                    )
                }
                composable<SyncLogsRoute>(
                enterTransition = {
                    fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 14 }
                },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 14 }
                },
            ) {
                    SyncLogsScreen(onBack = { nav.popBackStack() })
                }
                composable<WishlistRoute>(
                enterTransition = {
                    fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 14 }
                },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 14 }
                },
            ) {
                    WishlistScreen(onBack = { nav.popBackStack() })
                }
                composable<AboutRoute>(
                enterTransition = {
                    fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 14 }
                },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 14 }
                },
            ) {
                    AboutScreen(onBack = { nav.popBackStack() })
                }
            }

            if (isTab) {
                BottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    items = listOf(
                        BottomTab("任务", Icons.Default.Home, destination?.hasRoute<TodoRoute>() == true) { goTab(TodoRoute) },
                        BottomTab("统计", Icons.Default.DateRange, destination?.hasRoute<StatsRoute>() == true) { goTab(StatsRoute()) },
                        BottomTab("我的", Icons.Default.Settings, destination?.hasRoute<SettingsRoute>() == true) { goTab(SettingsRoute) },
                    ),
                )
            }
        }
    }
    }
}

private class BottomTab(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val accent: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun BottomBar(
    items: List<BottomTab>,
    modifier: Modifier = Modifier,
) {
    val feedback = LocalFeedback.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(Sizes.bottomBar),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { tab ->
                    val iconScale by animateFloatAsState(
                        targetValue = if (tab.selected) 1.08f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
                        label = "tabScale",
                    )
                    val tint by animateColorAsState(
                        targetValue = if (tab.selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(Motion.fast),
                        label = "tabTint",
                    )
                    val pill by animateColorAsState(
                        targetValue = if (tab.selected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        animationSpec = tween(Motion.fast),
                        label = "tabPill",
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = tab.selected,
                                role = Role.Tab,
                                onClick = {
                                    feedback?.tap()
                                    tab.onClick()
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (tab.accent) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.label,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        } else {
                            Box(
                                Modifier
                                    .width(52.dp)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(pill),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.label,
                                    tint = tint,
                                    modifier = Modifier.size(21.dp).graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tab.accent) MaterialTheme.colorScheme.primary else tint,
                        )
                    }
                }
            }
        }
    }
}
