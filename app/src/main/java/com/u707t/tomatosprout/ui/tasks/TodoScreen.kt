package com.u707t.tomatosprout.ui.tasks

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.u707t.tomatosprout.ui.components.EmptyState
import com.u707t.tomatosprout.ui.components.FilterPill
import com.u707t.tomatosprout.ui.components.IllustrationEmptyState
import com.u707t.tomatosprout.ui.components.LargeHeader
import com.u707t.tomatosprout.ui.components.Metric
import com.u707t.tomatosprout.ui.components.ProgressRing
import com.u707t.tomatosprout.ui.components.ThinProgress
import com.u707t.tomatosprout.ui.components.greetingFor
import com.u707t.tomatosprout.ui.feedback.LocalFeedback
import com.u707t.tomatosprout.ui.theme.Motion
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Sizes
import com.u707t.tomatosprout.ui.theme.Space
import com.u707t.tomatosprout.ui.theme.readableContentColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val reminderPresets = listOf("08:00", "12:00", "18:00", "20:00", "22:00")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoScreen(
    onStartTask: (Task) -> Unit,
    onStartFreeFocus: () -> Unit,
    onEditTask: (String) -> Unit,
    onAdvancedAdd: (QuickAddUiState) -> Unit,
    onOpenHistory: (taskId: String?) -> Unit,
    onOpenStats: (taskId: String?) -> Unit,
    simpleMode: Boolean = false,
    onSimpleModeChange: (Boolean) -> Unit = {},
    focusMinutes: Int = 25,
    viewModel: TodoViewModel = hiltViewModel(),
    quickAddViewModel: QuickAddViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val addState by quickAddViewModel.state.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val hideDone by viewModel.hideDone.collectAsStateWithLifecycle()
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
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var pendingUncheck by remember { mutableStateOf<Task?>(null) }
    // 撤销条同一时间只留一条：新的进来先把上一条结算掉（删除落地），避免两条删除互相覆盖
    var undoBar by remember { mutableStateOf<UndoBarState?>(null) }
    var undoJob by remember { mutableStateOf<Job?>(null) }
    // 撤销窗口内先把卡片藏起来，等窗口结束再真正写库
    var hiddenTaskId by remember { mutableStateOf<String?>(null) }
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
    val visible = remember(cards, filter, sort, hideDone, hiddenTaskId, query) {
        val keyword = query.trim()
        sortCards(filterCards(cards, filter, hideDone), sort)
            .filterNot { it.task.id == hiddenTaskId }
            .filter { card ->
                keyword.isEmpty() ||
                    card.task.title.contains(keyword, ignoreCase = true) ||
                    card.task.note?.contains(keyword, ignoreCase = true) == true
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

    /**
     * 撤销条：同一时间只留一条，新的进来先把上一条结算掉（删除落地）。
     * 用独立组件而不是 Snackbar，避免「结算消息」把撤销窗口顶掉。
     */
    fun pushUndo(bar: UndoBarState) {
        undoJob?.cancel()
        undoBar?.let { previous -> previous.expire() }
        undoBar = bar
        undoJob = scope.launch {
            delay(UNDO_WINDOW_MS)
            if (undoBar === bar) {
                bar.expire()
                undoBar = null
            }
        }
    }

    fun performUndo() {
        val bar = undoBar ?: return
        undoJob?.cancel()
        undoBar = null
        bar.action()
    }

    fun handleCheck(card: TodoCardUi) {
        if (card.checkedToday) {
            if (card.todayCheckInCount > 1) {
                pendingUncheck = card.task
            } else {
                feedback?.tap()
                // 精确撤销，不 toggle：撤销后 10 秒内又打了卡也不会互相打架
                viewModel.undoCheckIn(card.task)
                pushUndo(
                    UndoBarState(
                        message = "已撤销「${card.task.title}」的打卡",
                        actionLabel = "恢复",
                        onAction = {
                            feedback?.confirm()
                            viewModel.toggleCheckIn(card.task)
                        },
                        onExpire = {},
                        taskId = card.task.id,
                    ),
                )
            }
            return
        }
        // 用户又手动打卡了：先清掉同一任务的撤销条，避免「恢复」把这次新打卡又 toggle 掉
        if (undoBar?.taskId == card.task.id) {
            undoJob?.cancel()
            undoBar = null
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
     * 删除：先隐藏 + 10 秒撤销窗口，窗口结束才真正写库。
     * 长按菜单与左滑共用这一条路径，首页不再有「确认框 / 撤销条」两套模型。
     */
    fun deleteWithUndo(task: Task) {
        feedback?.reject()
        pushUndo(
            UndoBarState(
                message = "已删除「${task.title}」",
                actionLabel = "撤销",
                onAction = {
                    feedback?.tap()
                    hiddenTaskId = null
                },
                onExpire = {
                    viewModel.delete(task, announce = false)
                    hiddenTaskId = null
                },
                taskId = task.id,
            ),
        )
        hiddenTaskId = task.id
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
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = if (searchOpen) "关闭搜索" else "搜索任务",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
        snackbarHost = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                undoBar?.let { bar ->
                    UndoBar(bar = bar, onAction = { performUndo() })
                }
                SnackbarHost(snackbar)
            }
        },
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
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Default.Refresh,
                    title = "任务列表没加载出来",
                    message = ui.error.orEmpty(),
                    actionLabel = "重试",
                    onAction = { viewModel.refresh() },
                )
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
                if (searchOpen) {
                    item(key = "search") {
                        SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            onClose = {
                                searchOpen = false
                                query = ""
                            },
                        )
                    }
                }
                item(key = "overview") {
                    OverviewCard(
                        pomos = totalPomos,
                        minutes = totalFocusMinutes,
                        done = doneCount,
                        total = cards.size,
                        completion = completion,
                        focusMinutes = focusMinutes,
                        onOpenStats = { onOpenStats(null) },
                        onStartFocus = {
                            feedback?.tap()
                            onStartFreeFocus()
                        },
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
                                when {
                                    query.isNotBlank() -> "没有找到匹配的任务"
                                    hideDone && cards.any { it.isDone } -> "已完成的任务先收起来了"
                                    else -> "这个分类下还没有任务"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (query.isNotBlank()) {
                                Spacer(Modifier.height(Space.md))
                                Surface(
                                    onClick = {
                                        feedback?.tap()
                                        query = ""
                                    },
                                    shape = RoundedCornerShape(Radii.sm),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        "清除搜索",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                                    )
                                }
                            } else if (filter != TaskFilter.ALL) {
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
                            } else if (hideDone) {
                                Spacer(Modifier.height(Space.md))
                                Surface(
                                    onClick = {
                                        feedback?.tap()
                                        viewModel.setHideDone(false)
                                    },
                                    shape = RoundedCornerShape(Radii.sm),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        "显示已完成",
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
                            today = today,
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
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = Space.md)) {
                    Text("隐藏已完成", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "已完成的任务不再占首页位置，随时可以关掉",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = hideDone,
                    onCheckedChange = {
                        feedback?.tap()
                        viewModel.setHideDone(it)
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
                // 和左滑共用同一条「延迟删除 + 撤销条」路径，首页不再弹确认框
                ui.cards.firstOrNull { it.task.id == quick.taskId }?.task?.let { deleteWithUndo(it) }
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
    focusMinutes: Int,
    onOpenStats: () -> Unit,
    onStartFocus: () -> Unit,
) {
    Surface(
        onClick = onOpenStats,
        shape = RoundedCornerShape(Radii.md),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
    ) {
        Column {
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
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Space.lg, end = Space.sm, top = Space.xxs, bottom = Space.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    "自由专注 · $focusMinutes 分钟",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onStartFocus) {
                    Text(
                        "开始",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** 首页内联搜索：只过滤标题与备注，不碰排序与筛选。 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // LazyColumn 首次组合时焦点节点可能还没挂上，等一帧再请求，失败也不该崩
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }
    Surface(
        shape = RoundedCornerShape(Radii.sm),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.md, end = Space.xs, top = Space.xs, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Space.sm))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            "搜索标题或备注",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

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
