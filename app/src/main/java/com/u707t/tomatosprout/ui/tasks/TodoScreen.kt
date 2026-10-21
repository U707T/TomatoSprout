package com.u707t.tomatosprout.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.ui.components.ConfirmDialog
import com.u707t.tomatosprout.ui.components.EmptyState
import com.u707t.tomatosprout.ui.theme.Tomato
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    onStartTask: (Task) -> Unit,
    onEditTask: (String) -> Unit,
    onAdvancedAdd: (QuickAddUiState) -> Unit,
    onOpenHistory: (taskId: String?) -> Unit,
    onOpenStats: (taskId: String?) -> Unit,
    viewModel: TodoViewModel = hiltViewModel(),
    quickAddViewModel: QuickAddViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val addState by quickAddViewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }
    var finalDelete by remember { mutableStateOf<Task?>(null) }
    var pendingUndo by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(Unit) {
        quickAddViewModel.created.collect { task ->
            showAdd = false
            snackbar.showSnackbar("已创建「${task.title}」")
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("任务")
                        Text(
                            ui.today.format(DateTimeFormatter.ofPattern("M 月 d 日")),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenHistory(null) }) {
                        Icon(Icons.Default.DateRange, contentDescription = "历史时间轴")
                    }
                    IconButton(onClick = {
                        quickAddViewModel.reset()
                        showAdd = true
                    }) { Icon(Icons.Default.Add, contentDescription = "快捷添加任务") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    quickAddViewModel.reset()
                    showAdd = true
                },
                containerColor = Tomato,
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = 72.dp),
            ) { Icon(Icons.Default.Add, contentDescription = "新建任务") }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            ui.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.error != null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            ui.cards.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                EmptyState(
                    emoji = "🍅",
                    title = "今天想先做哪件事？",
                    message = "用一句话记下想做的事，再决定用番茄钟、正向计时，还是直接打卡。",
                    actionLabel = "快捷添加",
                    onAction = {
                        quickAddViewModel.reset()
                        showAdd = true
                    },
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "summary") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "共 ${ui.cards.size} 个任务 · 长按卡片打开快捷面板",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onOpenHistory(null) }) {
                            Text(
                                "历史 ›",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                items(ui.cards, key = { it.task.id }) { card ->
                    TodoBigCard(
                        card = card,
                        onStart = { onStartTask(card.task) },
                        onEdit = { onEditTask(card.task.id) },
                        onLongPress = { viewModel.showQuickActions(card.task.id) },
                        onCheckIn = {
                            if (card.checkedToday) {
                                pendingUndo = card.task
                            } else {
                                viewModel.toggleCheckIn(card.task)
                            }
                        },
                        onToggleDone = {
                            viewModel.markDone(card.task, card.task.status != TaskStatus.DONE)
                        },
                    )
                }
            }
        }
    }

    if (showAdd) {
        QuickAddDialog(
            state = addState,
            onTitle = quickAddViewModel::setTitle,
            onType = quickAddViewModel::setType,
            onTimer = quickAddViewModel::setTimer,
            onDuration = quickAddViewModel::setDuration,
            onCustomDuration = quickAddViewModel::setCustomDuration,
            onAdvanced = {
                showAdd = false
                onAdvancedAdd(addState)
            },
            onCreate = quickAddViewModel::create,
            onDismiss = {
                showAdd = false
                quickAddViewModel.clearError()
            },
        )
    }

    ui.quickAction?.let { quick ->
        QuickActionSheet(
            state = quick,
            onDismiss = viewModel::dismissQuickActions,
            onEdit = {
                viewModel.dismissQuickActions()
                onEditTask(quick.taskId)
            },
            onReminder = {
                viewModel.dismissQuickActions()
                onEditTask(quick.taskId)
            },
            onChangeColor = {
                ui.cards.firstOrNull { it.task.id == quick.taskId }?.task?.let(viewModel::cycleColor)
            },
            onMoveUp = { viewModel.move(quick.taskId, -1) },
            onMoveDown = { viewModel.move(quick.taskId, 1) },
            onDelete = {
                viewModel.dismissQuickActions()
                pendingDelete = ui.cards.firstOrNull { it.task.id == quick.taskId }?.task
            },
            onHistory = {
                viewModel.dismissQuickActions()
                onOpenHistory(quick.taskId)
            },
            onStats = {
                viewModel.dismissQuickActions()
                onOpenStats(quick.taskId)
            },
        )
    }

    pendingDelete?.let { task ->
        ConfirmDialog(
            title = "删除「${task.title}」？",
            message = "任务会从首页移除。已有的打卡与专注记录会保留，在历史页显示为「[已删除] ${task.title}」，统计不受影响。",
            confirmLabel = "继续删除",
            onConfirm = {
                pendingDelete = null
                finalDelete = task
            },
            onDismiss = { pendingDelete = null },
        )
    }

    finalDelete?.let { task ->
        ConfirmDialog(
            title = "最后确认",
            message = "删除后无法恢复这个任务；历史记录不会丢失。",
            confirmLabel = "确认删除",
            onConfirm = {
                viewModel.delete(task)
                finalDelete = null
            },
            onDismiss = { finalDelete = null },
        )
    }

    pendingUndo?.let { task ->
        ConfirmDialog(
            title = "撤销今天的打卡？",
            message = "会撤销「${task.title}」今天的全部打卡次数，连续天数可能随之变化。",
            confirmLabel = "撤销打卡",
            onConfirm = {
                viewModel.toggleCheckIn(task)
                pendingUndo = null
            },
            onDismiss = { pendingUndo = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoBigCard(
    card: TodoCardUi,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
    onCheckIn: () -> Unit,
    onToggleDone: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val cardHeight = (screenHeight / 7).coerceAtLeast(136.dp)
    val raw = runCatching { Color(card.task.color.toInt()) }.getOrDefault(Tomato)
    val base = lerp(raw, Color(0xFF6E625D), 0.16f)
    val done = card.isDone
    val gradient = if (done) {
        Brush.linearGradient(listOf(Color(0xFF8A8580), Color(0xFF5D5855)))
    } else {
        Brush.linearGradient(
            listOf(
                lerp(base, Color.White, 0.24f),
                lerp(base, Color.Black, 0.14f),
            ),
        )
    }
    val progressLabel = when {
        card.subtaskTotal > 0 -> "子任务 ${card.subtaskDone}/${card.subtaskTotal}"
        card.goal != null -> {
            val goal = card.goal
            if (goal.unit == "小时") {
                "目标 ${goal.used / 3600}/${(goal.target ?: 0) / 3600} 小时"
            } else {
                "目标 ${percentLabel(goal.used, goal.target)} 次"
            }
        }
        card.isHabit && card.streak > 0 -> "连续 ${card.streak} 天"
        else -> null
    }
    val modeLabel = if (card.isCountBased) "按次打卡" else card.timerLabel
    val metaLine = listOfNotNull(modeLabel, progressLabel).joinToString(" · ")

    Box(
        Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .combinedClickable(onClick = onEdit, onLongClick = onLongPress),
    ) {
        // 防眩光遮罩
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.10f)))
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f))),
            ),
        )
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((card.task.icon ?: "🍅") + " ", fontSize = 18.sp, color = Color.White)
                Text(
                    card.task.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                metaLine,
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            done -> "已完成"
                            card.isCountBased && card.checkedToday ->
                                "今日已打卡 ${card.todayCheckInCount}/${card.habitTarget}"
                            card.isCountBased && card.partiallyChecked ->
                                "今日 ${card.todayCheckInCount}/${card.habitTarget} · 还差 " +
                                    "${(card.habitTarget - card.todayCheckInCount).coerceAtLeast(1)} 次"
                            card.isCountBased -> "今日待打卡"
                            card.isHabit && card.checkedToday ->
                                "今日已打卡 ${card.todayCheckInCount}/${card.habitTarget} · 可继续专注"
                            card.todayFocusCount > 0 -> "今日已专注 ${card.todayFocusCount} 次"
                            else -> "准备开始"
                        },
                        color = Color.White.copy(alpha = 0.94f),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "今日 ${card.todayFocusMinutes} 分钟 · 累计 ${formatCardDuration(card.totalFocusSeconds)}",
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
                val actionLabel = when {
                    done -> "重新打开"
                    !card.isCountBased -> "开始"
                    card.checkedToday -> "已打卡"
                    card.partiallyChecked -> "继续打卡"
                    else -> "打卡"
                }
                val filled = !done && (!card.isCountBased || !card.checkedToday)
                Surface(
                    onClick = when {
                        done -> onToggleDone
                        card.isCountBased -> onCheckIn
                        else -> onStart
                    },
                    shape = RoundedCornerShape(22.dp),
                    color = if (filled) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                    modifier = Modifier.height(44.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    ) {
                        Text(
                            actionLabel,
                            color = if (filled) Tomato else Color.White.copy(alpha = 0.92f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun formatCardDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return when {
        safe <= 0 -> "0 分钟"
        safe < 60 -> "<1 分钟"
        safe < 3600 -> "${safe / 60} 分钟"
        else -> "${safe / 3600} 小时 ${(safe % 3600) / 60} 分"
    }
}
