package com.u707t.tomatosprout.ui.tasks

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
import com.u707t.tomatosprout.ui.components.AppBottomSheet
import com.u707t.tomatosprout.ui.components.FilterPill
import com.u707t.tomatosprout.ui.components.IllustrationEmptyState
import com.u707t.tomatosprout.ui.components.LargeHeader
import com.u707t.tomatosprout.ui.components.Metric
import com.u707t.tomatosprout.ui.components.ProgressRing
import com.u707t.tomatosprout.ui.components.ThinProgress
import com.u707t.tomatosprout.ui.components.greetingFor
import com.u707t.tomatosprout.ui.feedback.LocalFeedback
import com.u707t.tomatosprout.ui.components.showUndo
import com.u707t.tomatosprout.ui.theme.Motion
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Sizes
import com.u707t.tomatosprout.ui.theme.Space
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
private val reminderPresets = listOf("08:00", "12:00", "18:00", "20:00", "22:00")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoScreen(
    onStartTask: (Task) -> Unit,
    onEditTask: (String) -> Unit,
    onAdvancedAdd: (QuickAddUiState) -> Unit,
    onOpenHistory: (taskId: String?) -> Unit,
    onOpenStats: (taskId: String?) -> Unit,
    simpleMode: Boolean = false,
    onSimpleModeChange: (Boolean) -> Unit = {},
    viewModel: TodoViewModel = hiltViewModel(),
    quickAddViewModel: QuickAddViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val addState by quickAddViewModel.state.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val fabInteraction = remember { MutableInteractionSource() }
    val fabPressed by fabInteraction.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "fabScale",
    )

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSort by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }
    var pendingUncheck by remember { mutableStateOf<Task?>(null) }
    // 滑动删除的「撤销窗口」：期间先把卡片藏起来，等提示条结束再真正写库
    var pendingUndo by remember { mutableStateOf<Task?>(null) }
    var deleteJob by remember { mutableStateOf<Job?>(null) }
    var reminderTask by remember { mutableStateOf<Task?>(null) }
    var reminderTime by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(reminderTask?.id) {
        reminderTime = reminderTask?.let { viewModel.reminderTime(it.id) }
    }

    LaunchedEffect(Unit) {
        quickAddViewModel.created.collect { task ->
            showAdd = false
            feedback?.confirm()
            snackbar.showSnackbar("已创建「${task.title}」")
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    val today = ui.today
    val cards = ui.cards
    val filter = TaskFilter.entries[filterIndex.coerceIn(0, TaskFilter.entries.lastIndex)]
    val visible = remember(cards, filter, sort, pendingUndo?.id) {
        val filtered = when (filter) {
            TaskFilter.ALL -> cards
            TaskFilter.HABIT -> cards.filter { it.task.type == TaskType.HABIT }
            TaskFilter.TODO -> cards.filter { it.task.type == TaskType.TODO }
            TaskFilter.GOAL -> cards.filter { it.task.type == TaskType.GOAL }
            TaskFilter.POMODORO -> cards.filter { it.task.type == TaskType.POMODORO }
        }
        sortCards(filtered, sort).filterNot { it.task.id == pendingUndo?.id }
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
                feedback?.tap()
                viewModel.toggleCheckIn(card.task)
                scope.launch {
                    // 这里撤销的是「撤销动作」本身，按钮文案说「恢复」才不会被误解成再撤销一次
                    if (snackbar.showUndo("已撤销「${card.task.title}」的打卡", actionLabel = "恢复")) {
                        viewModel.toggleCheckIn(card.task)
                    }
                }
            }
            return
        }
        feedback?.confirm()
        viewModel.toggleCheckIn(card.task)
    }

    fun handleAction(card: TodoCardUi) {
        when {
            card.isDone -> {
                feedback?.tap()
                viewModel.markDone(card.task, false)
            }
            card.isCountBased -> handleCheck(card)
            else -> {
                feedback?.confirm()
                onStartTask(card.task)
            }
        }
    }

    /**
     * 滑动删除：先隐藏 + 给 10 秒撤销窗口，提示条结束才真正写库。
     * 这样「删错了」不需要从墓碑里恢复，也不会出现删了又弹一条「已删除」的双重提示。
     */
    fun deleteWithUndo(task: Task) {
        feedback?.reject()
        pendingUndo?.takeIf { it.id != task.id }?.let { viewModel.delete(it, announce = false) }
        deleteJob?.cancel()
        pendingUndo = task
        deleteJob = scope.launch {
            val undone = snackbar.showUndo("已删除「${task.title}」", actionLabel = "撤销")
            if (undone) {
                feedback?.tap()
            } else {
                viewModel.delete(task, announce = false)
            }
            if (pendingUndo?.id == task.id) pendingUndo = null
        }
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
                    IconButton(onClick = {
                        feedback?.tap()
                        showSort = true
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "排序与视图",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        feedback?.tap()
                        onOpenHistory(null)
                    }) {
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
                onClick = {
                    feedback?.tap()
                    openAdd()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(Radii.md),
                interactionSource = fabInteraction,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = Sizes.bottomBar + Space.xs)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    },
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
                            if (filter != TaskFilter.ALL) {
                                Spacer(Modifier.height(Space.md))
                                Surface(
                                    onClick = {
                                        feedback?.tap()
                                        filterIndex = TaskFilter.ALL.ordinal
                                    },
                                    shape = RoundedCornerShape(Radii.sm),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        "看全部任务",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(visible, key = { it.task.id }) { card ->
                        SwipeableTaskCard(
                            card = card,
                            simple = simpleMode,
                            modifier = Modifier.animateItem(),
                            onOpen = { onEditTask(card.task.id) },
                            onLongPress = { viewModel.showQuickActions(card.task.id) },
                            onAction = { handleAction(card) },
                            onSwipeCheck = {
                                // 右滑只做「打卡 / 完成 / 重新打开」，绝不在滑动里启动计时
                                feedback?.confirm()
                                if (card.isDone) viewModel.markDone(card.task, false) else handleCheck(card)
                            },
                            onSwipeDelete = { deleteWithUndo(card.task) },
                        )
                    }
                }
            }
        }
    }

    if (showSort) {
        AppBottomSheet(onDismiss = { showSort = false }) {
            Text("视图与排序", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.xxs))
            Text(
                "排序只改展示顺序，不会打乱手动调好的位置",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))
            TaskSort.entries.forEach { mode ->
                val selected = mode == sort
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.sm))
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                feedback?.tap()
                                viewModel.setSort(mode)
                            },
                        )
                        .padding(horizontal = Space.sm, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            mode.hint,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "当前排序",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.sm))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = Space.md)) {
                    Text("简约卡片", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "只留标题、连续天数与完成状态",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = simpleMode,
                    onCheckedChange = {
                        feedback?.tap()
                        onSimpleModeChange(it)
                    },
                )
            }
            Text(
                "小提示：卡片右滑可直接打卡 / 完成，左滑删除并能在提示条里撤销。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))
        }
    }

    reminderTask?.let { task ->
        AppBottomSheet(onDismiss = { reminderTask = null }) {
            Text("提醒", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.xxs))
            Text(
                task.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.lg))
            Text(
                "每天在这个时间提醒",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                reminderPresets.forEach { time ->
                    FilterPill(
                        text = time,
                        selected = reminderTime == time,
                        onClick = {
                            viewModel.setReminder(task, time)
                            reminderTask = null
                        },
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
            Surface(
                onClick = {
                    feedback?.tap()
                    pickReminderTime(context, reminderTime ?: "20:00") { picked ->
                        viewModel.setReminder(task, picked)
                        reminderTask = null
                    }
                },
                shape = RoundedCornerShape(Radii.sm),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = Space.lg, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("自定义时间", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        reminderTime ?: "未设置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (reminderTime != null) {
                Spacer(Modifier.height(Space.sm))
                TextButton(
                    onClick = {
                        feedback?.reject()
                        viewModel.setReminder(task, null)
                        reminderTask = null
                    },
                ) {
                    Text("关闭提醒", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(Space.md))
            Text(
                "提醒受「免打扰时段」约束，系统也可能推迟几分钟送达。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))
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
                feedback?.tap()
                ui.cards.firstOrNull { it.task.id == quick.taskId }?.task?.let { task ->
                    viewModel.markDone(task, !quick.isDone)
                }
            },
            onReminder = {
                viewModel.dismissQuickActions()
                // 等快捷面板的退场动画走完再开提醒弹层，避免两层遮罩叠在一起闪一下
                val task = ui.cards.firstOrNull { it.task.id == quick.taskId }?.task
                if (task != null) {
                    scope.launch {
                        delay(180)
                        reminderTask = task
                    }
                }
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

/**
 * 滑动外壳：右滑打卡 / 完成，左滑删除。
 *
 * `confirmValueChange` 一律返回 false —— 动作已经在回调里触发，卡片自己弹回原位，
 * 不会留下「滑走一半卡住」或需要手动 reset 的状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskCard(
    card: TodoCardUi,
    simple: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onAction: () -> Unit,
    onSwipeCheck: () -> Unit,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 计时任务右滑不启动专注：起计时必须是一次明确的点击
    val checkEnabled = card.isCountBased || card.isDone
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> if (checkEnabled) onSwipeCheck()
                SwipeToDismissBoxValue.EndToStart -> onSwipeDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
        positionalThreshold = { distance -> distance * 0.32f },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = checkEnabled,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            SwipeActionBackground(card = card, direction = state.dismissDirection)
        },
    ) {
        TaskCard(
            card = card,
            simple = simple,
            onOpen = onOpen,
            onLongPress = onLongPress,
            onAction = onAction,
        )
    }
}

/** 滑动时露出的底层：左打卡、右删除，颜色只用容器色，不抢卡片本身的注意力。 */
@Composable
private fun SwipeActionBackground(card: TodoCardUi, direction: SwipeToDismissBoxValue) {
    val checkAlpha by animateFloatAsState(
        targetValue = if (direction == SwipeToDismissBoxValue.StartToEnd) 1f else 0f,
        animationSpec = tween(Motion.fast),
        label = "swipeCheck",
    )
    val deleteAlpha by animateFloatAsState(
        targetValue = if (direction == SwipeToDismissBoxValue.EndToStart) 1f else 0f,
        animationSpec = tween(Motion.fast),
        label = "swipeDelete",
    )
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = checkAlpha }
                .clip(RoundedCornerShape(Radii.md))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                if (card.isDone) "重新打开" else "完成 / 打卡",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = deleteAlpha }
                .clip(RoundedCornerShape(Radii.md))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = Space.lg),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "删除",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(Space.sm))
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TaskCard(
    card: TodoCardUi,
    simple: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onAction: () -> Unit,
) {
    val task = card.task
    val feedback = LocalFeedback.current
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
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .combinedClickable(
                onClick = {
                    feedback?.tap()
                    onOpen()
                },
                onLongClick = {
                    feedback?.tap()
                    onLongPress()
                },
            ),
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
                Crossfade(
                    targetState = icon,
                    animationSpec = tween(180),
                    label = "taskActionIcon",
                ) { current ->
                    Icon(
                        current,
                        contentDescription = description,
                        tint = fg,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private val PaletteFallback = Color(0xFFE0553F)

/** 提醒时间的系统选择器；默认值按 24 小时制。 */
private fun pickReminderTime(context: Context, initial: String, onPick: (String) -> Unit) {
    val parts = initial.split(":")
    TimePickerDialog(
        context,
        { _, hour, minute -> onPick("%02d:%02d".format(hour, minute)) },
        parts.getOrNull(0)?.toIntOrNull() ?: 20,
        parts.getOrNull(1)?.toIntOrNull() ?: 0,
        true,
    ).show()
}
