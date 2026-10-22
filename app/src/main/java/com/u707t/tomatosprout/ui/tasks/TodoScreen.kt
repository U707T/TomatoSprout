package com.u707t.tomatosprout.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.R
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.ui.components.ConfirmDialog
import com.u707t.tomatosprout.ui.components.FilterPill
import com.u707t.tomatosprout.ui.components.IllustrationEmptyState
import com.u707t.tomatosprout.ui.components.LargeHeader
import com.u707t.tomatosprout.ui.components.Metric
import com.u707t.tomatosprout.ui.components.ProgressRing
import com.u707t.tomatosprout.ui.components.ThinProgress
import com.u707t.tomatosprout.ui.components.greetingFor
import com.u707t.tomatosprout.ui.components.rememberHaptics
import com.u707t.tomatosprout.ui.components.showUndo
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class TaskFilter(val label: String) {
    ALL("全部"),
    HABIT("习惯"),
    TODO("待办"),
    GOAL("目标"),
    POMODORO("番茄"),
}

private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoScreen(
    onStartTask: (Task) -> Unit,
    onEditTask: (String) -> Unit,
    onAdvancedAdd: (QuickAddUiState) -> Unit,
    onOpenHistory: (taskId: String?) -> Unit,
    onOpenStats: (taskId: String?) -> Unit,
    simpleMode: Boolean = false,
    viewModel: TodoViewModel = hiltViewModel(),
    quickAddViewModel: QuickAddViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val addState by quickAddViewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }
    var pendingUncheck by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(Unit) {
        quickAddViewModel.created.collect { task ->
            showAdd = false
            haptics.confirm()
            snackbar.showSnackbar("已创建「${task.title}」")
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    val today = ui.today
    val cards = ui.cards
    val filter = TaskFilter.entries[filterIndex.coerceIn(0, TaskFilter.entries.lastIndex)]
    val visible = remember(cards, filter) {
        when (filter) {
            TaskFilter.ALL -> cards
            TaskFilter.HABIT -> cards.filter { it.task.type == TaskType.HABIT }
            TaskFilter.TODO -> cards.filter { it.task.type == TaskType.TODO }
            TaskFilter.GOAL -> cards.filter { it.task.type == TaskType.GOAL }
            TaskFilter.POMODORO -> cards.filter { it.task.type == TaskType.POMODORO }
        }
    }

    val doneCount = cards.count { it.isDone || it.checkedToday }
    val totalFocusMinutes = cards.sumOf { it.todayFocusMinutes }
    val totalPomos = cards.sumOf { it.todayFocusCount }
    val completion = if (cards.isEmpty()) 0f else doneCount.toFloat() / cards.size

    fun counts(f: TaskFilter): Int = when (f) {
        TaskFilter.ALL -> cards.size
        TaskFilter.HABIT -> cards.count { it.task.type == TaskType.HABIT }
        TaskFilter.TODO -> cards.count { it.task.type == TaskType.TODO }
        TaskFilter.GOAL -> cards.count { it.task.type == TaskType.GOAL }
        TaskFilter.POMODORO -> cards.count { it.task.type == TaskType.POMODORO }
    }

    fun openAdd() {
        quickAddViewModel.reset()
        showAdd = true
    }

    fun handleCheck(card: TodoCardUi) {
        if (card.checkedToday) {
            if (card.todayCheckInCount > 1) {
                pendingUncheck = card.task
            } else {
                haptics.tap()
                viewModel.toggleCheckIn(card.task)
                scope.launch {
                    if (snackbar.showUndo("已撤销「${card.task.title}」的打卡")) {
                        viewModel.toggleCheckIn(card.task)
                    }
                }
            }
            return
        }
        haptics.confirm()
        viewModel.toggleCheckIn(card.task)
    }

    Scaffold(
        topBar = {
            LargeHeader(
                title = greetingFor(LocalTime.now()),
                eyebrow = "${today.format(DateTimeFormatter.ofPattern("M 月 d 日"))} · ${weekdayNames[today.dayOfWeek.value - 1]}",
                subtitle = when {
                    cards.isEmpty() -> "先记下一件想做的事"
                    doneCount == cards.size -> "今天的事情都完成了，休息一下"
                    else -> "还有 ${cards.size - doneCount} 件没完成，专注 $totalFocusMinutes 分钟"
                },
                trailing = {
                    IconButton(onClick = { onOpenHistory(null) }) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "历史时间轴",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { openAdd() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(Radii.md),
                modifier = Modifier.navigationBarsPadding().padding(bottom = 66.dp),
            ) {
                Row(Modifier.padding(horizontal = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text("新建", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            ui.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center,
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

            ui.error != null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(Space.xxl),
                Alignment.Center,
            ) {
                Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            cards.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center,
            ) {
                IllustrationEmptyState(
                    image = R.drawable.illus_sun,
                    title = "今天想先做哪件事？",
                    message = "写下一个具体动作，再决定用番茄钟、正向计时，还是直接打卡。",
                    actionLabel = "新建第一件事",
                    onAction = { openAdd() },
                )
            }

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                item(key = "overview") {
                    OverviewCard(
                        pomos = totalPomos,
                        minutes = totalFocusMinutes,
                        done = doneCount,
                        total = cards.size,
                        completion = completion,
                        onOpenStats = { onOpenStats(null) },
                    )
                }
                item(key = "filters") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = Space.xs),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        TaskFilter.entries.forEach { f ->
                            FilterPill(
                                text = "${f.label} ${counts(f)}",
                                selected = f == filter,
                                onClick = { filterIndex = f.ordinal },
                            )
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item(key = "empty-filter") {
                        Column(
                            Modifier.fillMaxWidth().padding(top = Space.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "这个分类下还没有任务",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(visible, key = { it.task.id }) { card ->
                        TaskCard(
                            card = card,
                            simple = simpleMode,
                            onOpen = { onEditTask(card.task.id) },
                            onLongPress = { viewModel.showQuickActions(card.task.id) },
                            onAction = {
                                when {
                                    card.isDone -> {
                                        haptics.tap()
                                        viewModel.markDone(card.task, false)
                                    }
                                    card.isCountBased -> handleCheck(card)
                                    else -> onStartTask(card.task)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        QuickAddWindow(
            state = addState,
            onTitle = quickAddViewModel::setTitle,
            onType = quickAddViewModel::setType,
            onTimer = quickAddViewModel::setTimer,
            onDuration = quickAddViewModel::setDuration,
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
            onToggleDone = {
                haptics.tap()
                ui.cards.firstOrNull { it.task.id == quick.taskId }?.task?.let { task ->
                    viewModel.markDone(task, !quick.isDone)
                }
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
            message = "任务会从首页移除，打卡与专注历史会保留。提醒和习惯排期会一并删除。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                haptics.reject()
                viewModel.delete(task)
            },
            onDismiss = { pendingDelete = null },
        )
    }

    pendingUncheck?.let { task ->
        val card = cards.firstOrNull { it.task.id == task.id }
        ConfirmDialog(
            title = "撤销今天的打卡？",
            message = "会撤销今天全部 ${card?.todayCheckInCount ?: 0} 次打卡，连续天数可能随之变化。",
            confirmLabel = "全部撤销",
            destructive = true,
            onConfirm = {
                pendingUncheck = null
                haptics.tap()
                viewModel.toggleCheckIn(task)
            },
            onDismiss = { pendingUncheck = null },
        )
    }
}

@Composable
private fun OverviewCard(
    pomos: Int,
    minutes: Int,
    done: Int,
    total: Int,
    completion: Float,
    onOpenStats: () -> Unit,
) {
    Surface(
        onClick = onOpenStats,
        shape = RoundedCornerShape(Radii.md),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
    ) {
        Row(
            Modifier.padding(horizontal = Space.lg, vertical = Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Metric(pomos.toString(), "今日番茄", Modifier.weight(1f))
            Metric(minutes.toString(), "专注时长", Modifier.weight(1f), unit = "分")
            Metric("$done/$total", "完成", Modifier.weight(1f))
            ProgressRing(
                progress = completion,
                size = 44.dp,
                stroke = 4.dp,
                label = null,
            )
        }
    }
}

@Composable
private fun TaskCard(
    card: TodoCardUi,
    simple: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onAction: () -> Unit,
) {
    val task = card.task
    val accent = remember(task.color) {
        runCatching { Color(task.color.toInt()) }.getOrDefault(PaletteFallback)
    }
    val done = card.isDone
    val dim = done || card.checkedToday
    val container = MaterialTheme.colorScheme.surface
    val meta = if (simple) {
        when {
            done -> "已完成"
            card.isHabit && card.streak > 0 -> "连续 ${card.streak} 天"
            card.checkedToday -> "今日已打卡"
            card.isCountBased -> "按次打卡"
            else -> "${task.focusMinutes} 分钟"
        }
    } else {
        buildList {
            add(
                when (task.type) {
                    TaskType.HABIT -> "习惯"
                    TaskType.TODO -> "待办"
                    TaskType.GOAL -> "目标"
                    TaskType.POMODORO -> "番茄"
                },
            )
            when (task.timerMode) {
                "none" -> Unit
                "countup" -> add("正向计时")
                else -> add("${task.focusMinutes} 分钟")
            }
            if (card.isHabit && card.streak > 0) add("连续 ${card.streak} 天")
            if (card.isTimed && card.todayFocusCount > 0) add("今日 ${card.todayFocusCount} 个番茄")
            if (done) add("已完成")
        }.joinToString(" · ")
    }

    val goal = card.goal
    val goalLabel: String? = goal?.takeIf { (it.target ?: 0) > 0 }?.let { g ->
        val target = g.target ?: 0
        if (g.unit == "小时") "目标 ${g.used / 3600}/${target / 3600} 小时"
        else "目标 ${g.used}/$target 次"
    }
    val progressLabel: String? = when {
        task.timerMode == "none" && card.habitTarget > 1 -> "今日 ${card.todayCheckInCount}/${card.habitTarget}"
        card.isHabit && card.habitTarget > 1 -> "今日 ${card.todayCheckInCount}/${card.habitTarget}"
        goalLabel != null -> goalLabel
        card.subtaskTotal > 0 -> "子任务 ${card.subtaskDone}/${card.subtaskTotal}"
        task.estimatedPomos > 0 -> "番茄 ${task.completedPomos}/${task.estimatedPomos}"
        else -> null
    }
    val progress: Float? = when {
        task.timerMode == "none" && card.habitTarget > 1 ->
            card.todayCheckInCount.toFloat() / card.habitTarget
        goal?.ratio != null -> goal.ratio
        card.subtaskTotal > 0 -> card.subtaskDone.toFloat() / card.subtaskTotal
        task.estimatedPomos > 0 -> task.completedPomos.toFloat() / task.estimatedPomos
        else -> null
    }

    Surface(
        shape = RoundedCornerShape(Radii.md),
        color = container,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (card.checkedToday || done) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        Row(
            Modifier.padding(horizontal = Space.lg, vertical = Space.md).heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(Radii.sm))
                    .background(accent.copy(alpha = if (dim) 0.08f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(task.icon ?: "🍅", fontSize = 19.sp)
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!simple && progress != null && progressLabel != null) {
                    Spacer(Modifier.height(Space.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThinProgress(
                            progress = progress,
                            modifier = Modifier.weight(1f),
                            color = if (dim) accent.copy(alpha = 0.5f) else accent,
                            height = 3.dp,
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.width(Space.sm))
            TaskAction(card = card, accent = accent, onAction = onAction)
        }
    }
}

@Composable
private fun TaskAction(card: TodoCardUi, accent: Color, onAction: () -> Unit) {
    val checked = card.isDone || card.checkedToday
    val bg = if (checked) accent.copy(alpha = 0.12f) else accent
    // 浅色任务色上白字会糊，按亮度切到深色前景
    val fg = if (checked) accent else if (accent.luminance() > 0.62f) Color(0xFF3A2A24) else Color.White
    val icon = when {
        card.isDone -> Icons.Default.Check
        card.isCountBased -> if (card.checkedToday) Icons.Default.Check else Icons.Default.Add
        else -> Icons.Default.PlayArrow
    }
    val description = when {
        card.isDone -> "重新打开"
        card.checkedToday -> "撤销打卡"
        card.isCountBased -> "打卡"
        else -> "开始专注"
    }
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onAction,
            shape = CircleShape,
            color = bg,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = description, tint = fg, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private val PaletteFallback = Color(0xFFE0553F)
