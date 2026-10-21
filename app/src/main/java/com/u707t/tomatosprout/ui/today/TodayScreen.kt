package com.u707t.tomatosprout.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.domain.TodayItem
import com.u707t.tomatosprout.ui.components.CheckCircle
import com.u707t.tomatosprout.ui.components.EmptyState
import com.u707t.tomatosprout.ui.components.ProgressRing
import com.u707t.tomatosprout.ui.components.SoftCard
import com.u707t.tomatosprout.ui.components.dateFmt
import com.u707t.tomatosprout.ui.components.greetingFor
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onAddHabit: () -> Unit,
    onEditTask: (String) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenCalendar: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            greetingFor(LocalTime.now()) + "，今天也慢慢来",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            (state as? TodayUiState.Content)?.data?.today?.format(
                                DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA),
                            ) ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenCalendar) {
                        Icon(Icons.Default.DateRange, contentDescription = "日历")
                    }
                    IconButton(onClick = onOpenTasks) {
                        Icon(Icons.Default.List, contentDescription = "任务卡片")
                    }
                    val simple = (state as? TodayUiState.Content)?.simpleMode == true
                    TextButton(onClick = { viewModel.setSimpleMode(!simple) }) {
                        Text(if (simple) "标准" else "简约")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddHabit,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = 64.dp),
            ) { Icon(Icons.Default.Add, contentDescription = "新建习惯") }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val s = state) {
            TodayUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is TodayUiState.Empty -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                EmptyState(
                    emoji = "🌱",
                    title = "从一个小习惯开始",
                    message = "每天读 10 分钟、喝一杯水，任何微小的坚持都会留下痕迹。",
                    actionLabel = "创建第一个习惯",
                    onAction = onAddHabit,
                )
            }
            is TodayUiState.Error -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("出了点问题：${s.message}", color = MaterialTheme.colorScheme.error)
            }
            is TodayUiState.Content -> TodayContent(
                data = s.data,
                simpleMode = s.simpleMode,
                padding = padding,
                onToggle = viewModel::toggle,
                onUndo = viewModel::undo,
                onEdit = onEditTask,
                onSaveMeta = viewModel::saveMeta,
            )
        }
    }
}

@Composable
private fun TodayContent(
    data: com.u707t.tomatosprout.domain.TodayData,
    simpleMode: Boolean,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onToggle: (TodayItem) -> Unit,
    onUndo: (TodayItem) -> Unit,
    onEdit: (String) -> Unit,
    onSaveMeta: (String, String, Int?) -> Unit,
) {
    var metaItem by remember { mutableStateOf<TodayItem?>(null) }
    val habits = data.items.filter { it.task.type == TaskType.HABIT }
    val todos = data.items.filter { it.task.type == TaskType.TODO }
    val progress = if (data.habitTotal == 0) 0f else data.habitDone.toFloat() / data.habitTotal

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(
                        progress = progress,
                        size = 92.dp,
                        stroke = 9.dp,
                        label = "${data.habitDone}/${data.habitTotal}",
                        subLabel = "今日",
                    )
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (data.allDone) "今天全部完成，真棒" else "今日还差 ${data.habitTotal - data.habitDone} 个",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "🔥 连续 ${data.overallStreak} 天",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "🍅 今日专注 ${data.focusSeconds / 60} 分钟",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (habits.isNotEmpty()) {
            item { Text("习惯", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(habits, key = { it.task.id }) { item ->
                HabitItemCard(
                    item = item,
                    simpleMode = simpleMode,
                    onToggle = { onToggle(item) },
                    onUndo = { onUndo(item) },
                    onEdit = { onEdit(item.task.id) },
                    onMeta = { metaItem = item },
                )
            }
        }

        if (todos.isNotEmpty()) {
            item { Text("待办", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(todos, key = { it.task.id }) { item ->
                HabitItemCard(
                    item = item,
                    simpleMode = simpleMode,
                    onToggle = { onToggle(item) },
                    onUndo = { onUndo(item) },
                    onEdit = { onEdit(item.task.id) },
                    onMeta = { metaItem = item },
                )
            }
        }

        if (habits.isEmpty() && todos.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🍃",
                    title = "今天没有排期的习惯",
                    message = "休息也是一种节奏。想加点什么，随时点右下角。",
                )
            }
        }
    }

    metaItem?.let { item ->
        MetaDialog(
            item = item,
            onDismiss = { metaItem = null },
            onSave = { note, mood ->
                item.checkedId?.let { onSaveMeta(it, note, mood) }
                metaItem = null
            },
        )
    }
}

@Composable
private fun HabitItemCard(
    item: TodayItem,
    simpleMode: Boolean,
    onToggle: () -> Unit,
    onUndo: () -> Unit,
    onEdit: () -> Unit,
    onMeta: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val color = runCatching { Color(item.task.color.toInt()) }.getOrDefault(MaterialTheme.colorScheme.primary)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(color.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(item.task.icon ?: "🌱", fontSize = 20.sp) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.habit != null) {
                    Text(
                        "🔥 ${item.streak} 天" + if (item.target > 1) " · ${item.count}/${item.target}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("待办", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!simpleMode && !item.task.note.isNullOrBlank()) {
                    Text(
                        item.task.note.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; onEdit() })
                    if (item.checkedId != null) {
                        DropdownMenuItem(text = { Text("心情 / 备注") }, onClick = { menu = false; onMeta() })
                    }
                    if (item.count > 0) {
                        DropdownMenuItem(text = { Text("撤销打卡") }, onClick = { menu = false; onUndo() })
                    }
                }
            }
            CheckCircle(
                checked = item.checked,
                onClick = onToggle,
                description = if (item.checked) "撤销 ${item.task.title} 的打卡" else "为 ${item.task.title} 打卡",
            )
        }
    }
}

@Composable
private fun MetaDialog(
    item: TodayItem,
    onDismiss: () -> Unit,
    onSave: (String, Int?) -> Unit,
) {
    var note by remember(item.task.id) { mutableStateOf(item.note.orEmpty()) }
    var mood by remember(item.task.id) { mutableStateOf(item.mood) }
    val moods = listOf("😌" to 1, "🙂" to 2, "😐" to 3, "😮‍💨" to 4, "😣" to 5)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("给「${item.task.title}」留句话") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    moods.forEach { (emoji, value) ->
                        Surface(
                            shape = CircleShape,
                            color = if (mood == value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(44.dp).then(
                                Modifier.padding(2.dp),
                            ),
                            onClick = { mood = value },
                        ) { Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) } }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note, mood) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
