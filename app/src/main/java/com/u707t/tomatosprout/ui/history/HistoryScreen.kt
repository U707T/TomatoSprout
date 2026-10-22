package com.u707t.tomatosprout.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.ui.components.AppBottomSheet
import com.u707t.tomatosprout.ui.components.BackBar
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Space
import com.u707t.tomatosprout.ui.theme.Tomato
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var noteRecord by remember { mutableStateOf<HistoryRecordUi?>(null) }
    var noteText by remember { mutableStateOf("") }
    var showMakeup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(ui.error) { ui.error?.let { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            BackBar("历史", onBack, actions = {
                TextButton(onClick = viewModel::goToday) { Text("今天") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "calendar") {
                MonthCalendar(
                    month = ui.month,
                    selected = ui.selectedDate,
                    today = ui.today,
                    recordDays = ui.recordDays,
                    onSelect = viewModel::selectDate,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
            }

            item(key = "dayHeader") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            ui.selectedDate.format(
                                DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA),
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "专注 ${ui.dayFocusSeconds[ui.selectedDate]?.div(60) ?: 0} 分钟 · " +
                                "打卡 ${ui.dayCheckIns[ui.selectedDate] ?: 0} 次",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (ui.canMakeup) {
                        TextButton(onClick = { showMakeup = true }) { Text("补卡") }
                    }
                }
            }

            if (ui.records.isEmpty()) {
                item(key = "empty") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "这一天没有记录。留白也是节奏。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                items(ui.records, key = { "${it.kind}-${it.id}" }) { record ->
                    TimelineRecordCard(
                        record = record,
                        onClick = {
                            if (!record.taskDeleted) {
                                noteRecord = record
                                noteText = record.note.orEmpty()
                            }
                        },
                    )
                }
            }

            if (ui.missingHabits.isNotEmpty()) {
                item(key = "makeupHint") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("最近 7 天可补卡", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                ui.missingHabits.joinToString("、") { it.title },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { showMakeup = true }) { Text("去补卡") }
                        }
                    }
                }
            }
        }
    }

    noteRecord?.let { record ->
        AppBottomSheet(onDismiss = { noteRecord = null }) {
            Text(
                "给「${record.title}」写一句心得",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(Space.md))
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it.take(300) },
                label = { Text("此刻的感受 / 收获（可选）") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.md))
            Button(
                onClick = {
                    viewModel.saveNote(record, noteText)
                    noteRecord = null
                },
                shape = RoundedCornerShape(Radii.sm),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("保存心得") }
            Spacer(Modifier.height(Space.sm))
        }
    }

    if (showMakeup) {
        AlertDialog(
            onDismissRequest = { showMakeup = false },
            title = { Text("补卡（最近 7 天）") },
            text = {
                Column {
                    Text(
                        "补卡会在时间轴中标记为「补卡」，并计入连续天数。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (ui.missingHabits.isEmpty()) {
                        Text("这一天没有可补的习惯。")
                    } else {
                        ui.missingHabits.forEach { habit ->
                            key(habit.taskId) {
                                TextButton(
                                    onClick = {
                                        viewModel.makeup(habit.taskId)
                                        showMakeup = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        (habit.icon ?: "🌱") + " " + habit.title,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Start,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMakeup = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    recordDays: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radii.md),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrevious) { Text("‹") }
                Text(
                    "${month.year} 年 ${month.monthValue} 月",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onNext) { Text("›") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    key(label) {
                        Text(
                            label,
                            Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val offset = month.atDay(1).dayOfWeek.value - 1
            val total = month.lengthOfMonth()
            repeat(6) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val index = row * 7 + col - offset
                        key(index) {
                            Box(
                                Modifier.weight(1f).aspectRatio(0.86f)
                                    .clickable(enabled = index in 0 until total) {
                                        if (index in 0 until total) onSelect(month.atDay(index + 1))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (index in 0 until total) {
                                    val date = month.atDay(index + 1)
                                    val selectedDay = date == selected
                                    val hasRecord = date in recordDays
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        selectedDay -> MaterialTheme.colorScheme.primary
                                                        date == today -> MaterialTheme.colorScheme.primaryContainer
                                                        else -> Color.Transparent
                                                    },
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "${date.dayOfMonth}",
                                                color = if (selectedDay) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                        Box(
                                            Modifier
                                                .padding(top = 2.dp)
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(if (hasRecord) MaterialTheme.colorScheme.primary else Color.Transparent),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRecordCard(record: HistoryRecordUi, onClick: () -> Unit) {
    val accent = if (record.taskDeleted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        runCatching { Color(record.color.toInt()) }.getOrDefault(Tomato)
    }
    val contentColor = if (record.taskDeleted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            Modifier.width(48.dp).padding(top = 10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                record.startLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            record.endLabel?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.width(22.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .padding(top = 14.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Box(
                Modifier.padding(top = 12.dp).size(9.dp).clip(CircleShape).background(accent),
            )
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp)
                .then(if (record.taskDeleted) Modifier else Modifier.clickable(onClick = onClick)),
            shape = RoundedCornerShape(Radii.sm),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(width = 3.dp, height = 16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accent),
                    )
                    Text(if (record.kind == "focus") "🍅" else "✓", fontSize = 14.sp)
                    Text(
                        record.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 6.dp),
                    )
                    Text(record.statusLabel, style = MaterialTheme.typography.labelSmall, color = accent)
                }
                if (record.taskDeleted) {
                    Text(
                        "任务已删除 · 记录保留",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (record.durationSeconds > 0) {
                    Spacer(Modifier.height(6.dp))
                    val progress = if (record.plannedSeconds <= 0) {
                        1f
                    } else {
                        (record.durationSeconds.toFloat() / record.plannedSeconds).coerceIn(0f, 1f)
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = accent,
                        trackColor = accent.copy(alpha = 0.15f),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    when {
                        record.note.isNullOrBlank() && record.taskDeleted -> "历史记录只读"
                        record.note.isNullOrBlank() -> "点击填写心得…"
                        else -> (record.mood?.let { moodEmoji(it) + " " } ?: "") + record.note
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (record.note.isNullOrBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (record.durationSeconds > 0) {
                    Text(
                        if (record.interrupted) {
                            "中断于 ${record.durationSeconds / 60} 分 ${record.durationSeconds % 60} 秒"
                        } else {
                            "时长 ${record.durationSeconds / 60} 分 ${record.durationSeconds % 60} 秒"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (record.isMakeup) {
                    Text(
                        "补卡记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                }
            }
        }
    }
}

private fun moodEmoji(mood: Int): String = when (mood) {
    1 -> "😌"
    2 -> "🙂"
    3 -> "😐"
    4 -> "😮‍💨"
    5 -> "😣"
    else -> ""
}
