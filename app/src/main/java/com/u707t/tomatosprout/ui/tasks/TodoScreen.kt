package com.u707t.tomatosprout.ui.tasks

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    onStartTask: (Task) -> Unit,
    onEditTask: (String) -> Unit,
    onAdvancedAdd: (QuickAddUiState) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenHistory: (String) -> Unit,
    onOpenStats: (String) -> Unit,
    viewModel: TodoViewModel = hiltViewModel(),
    quickAddViewModel: QuickAddViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val addState by quickAddViewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(Unit) {
        quickAddViewModel.created.collect { task ->
            showAdd = false
            snackbar.showSnackbar("已创建「${task.title}」")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("任务")
                        Text(
                            LocalDate.now().format(DateTimeFormatter.ofPattern("M 月 d 日")),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onOpenArchive) {
                        Text(if (ui.archivedCount > 0) "归档 ${ui.archivedCount}" else "归档")
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
                modifier = Modifier.padding(bottom = 64.dp),
            ) { Icon(Icons.Default.Add, contentDescription = "新建任务") }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            ui.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.error != null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
            ui.cards.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                EmptyState(
                    emoji = "🗂",
                    title = "还没有任务",
                    message = "用一句话记下想做的事，再决定用番茄钟还是正向计时。",
                    actionLabel = "快捷添加",
                    onAction = {
                        quickAddViewModel.reset()
                        showAdd = true
                    },
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "共 ${ui.cards.size} 个任务 · 长按卡片打开快捷面板",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(ui.cards, key = { it.task.id }) { card ->
                    TodoBigCard(
                        card = card,
                        onStart = { onStartTask(card.task) },
                        onEdit = { onEditTask(card.task.id) },
                        onLongPress = { viewModel.showQuickActions(card.task.id) },
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
            message = "任务会从列表隐藏，历史专注记录仍会保留。",
            confirmLabel = "删除",
            onConfirm = {
                viewModel.delete(task)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
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
    onToggleDone: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val cardHeight = (screenHeight / 6).coerceAtLeast(132.dp)
    val raw = runCatching { Color(card.task.color.toInt()) }.getOrDefault(Tomato)
    val base = lerp(raw, Color(0xFF6E625D), 0.16f)
    val done = card.task.status == TaskStatus.DONE
    val gradient = if (done) {
        Brush.linearGradient(listOf(Color(0xFF8A8580), Color(0xFF5D5855)))
    } else {
        Brush.linearGradient(
            listOf(
                lerp(base, Color.White, 0.18f),
                lerp(base, Color.Black, 0.18f),
            ),
        )
    }
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
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        card.task.title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val progressLabel = when {
                        card.subtaskTotal > 0 -> "子任务 ${card.subtaskDone}/${card.subtaskTotal}"
                        card.goal != null -> {
                            val goal = card.goal
                            if (goal.unit == "小时") {
                                val usedH = goal.used / 3600
                                val targetH = (goal.target ?: 0) / 3600
                                "目标 ${usedH}/${targetH} 小时"
                            } else {
                                "目标 ${percentLabel(goal.used, goal.target)} 次"
                            }
                        }
                        else -> null
                    }
                    if (progressLabel != null) {
                        Text(
                            progressLabel,
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (card.task.type == com.u707t.tomatosprout.domain.TaskType.TODO && card.subtaskTotal == 0) {
                    Surface(
                        onClick = onToggleDone,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (done) "✓" else "", color = Color.White, fontSize = 15.sp)
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (done) "已完成" else card.timerLabel,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "今日已专注 ${card.todayFocusCount} 次",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Surface(
                    onClick = { if (!done) onStart() },
                    enabled = !done,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = if (done) 0.08f else 0.20f),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.55f)),
                    modifier = Modifier.size(62.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (done) "完成" else "开始",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }
}
