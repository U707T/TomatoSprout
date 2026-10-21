package com.u707t.tomatosprout.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.ui.theme.Tomato
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTimelineScreen(onBack: () -> Unit, viewModel: CalendarViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var noteRecord by remember { mutableStateOf<CalendarRecordUi?>(null) }
    var noteText by remember { mutableStateOf("") }
    var showMakeup by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(ui.error) { ui.error?.let { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日历") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    TextButton(onClick = { viewModel.selectDate(LocalDate.now()) }) { Text("今天") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            MonthCalendar(
                month = ui.month,
                selected = ui.selectedDate,
                today = ui.today,
                recordDays = ui.recordDays,
                onSelect = viewModel::selectDate,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )

            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = ui.selectedDate,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "calendarTimeline",
            ) { date ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                date.format(DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA)),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            val focusMinutes = ui.dayFocusSeconds[date] ?: 0L
                            Text(
                                "专注 ${focusMinutes / 60} 分钟 · 打卡 ${ui.dayCheckIns[date] ?: 0} 次",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (ui.missingHabits.isNotEmpty()) {
                            TextButton(onClick = { showMakeup = true }) { Text("补卡") }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (ui.records.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "这一天没有记录。留白也是节奏。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        ui.records.forEachIndexed { index, record ->
                            TimelineRow(
                                record = record,
                                isLast = index == ui.records.lastIndex,
                                onClickNote = {
                                    noteRecord = record
                                    noteText = record.note.orEmpty()
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (ui.missingHabits.isNotEmpty()) {
                        Text(
                            "最近 7 天可补卡：${ui.missingHabits.joinToString("、") { it.title }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
    }

    noteRecord?.let { record ->
        ModalBottomSheet(
            onDismissRequest = { noteRecord = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    "给「${record.title}」写一句心得",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(300) },
                    label = { Text("此刻的感受 / 收获（可选）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.saveNote(record, noteText)
                        noteRecord = null
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("保存心得") }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showMakeup) {
        AlertDialog(
            onDismissRequest = { showMakeup = false },
            title = { Text("补卡（最近 7 天）") },
            text = {
                Column {
                    Text(
                        "选择需要补的习惯，补卡会在时间轴中标记为「补卡」。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    ui.missingHabits.forEach { habit ->
                        TextButton(
                            onClick = {
                                viewModel.makeup(habit.taskId)
                                showMakeup = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text((habit.icon ?: "🌱") + " " + habit.title, modifier = Modifier.fillMaxWidth())
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
        listOf("一", "二", "三", "四", "五", "六", "日").forEach {
            Text(
                it,
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    val offset = month.atDay(1).dayOfWeek.value - 1
    val total = month.lengthOfMonth()
    repeat(6) { row ->
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { col ->
                val index = row * 7 + col - offset
                Box(
                    Modifier.weight(1f).aspectRatio(0.83f)
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
                                            selectedDay -> Tomato
                                            date == today -> MaterialTheme.colorScheme.primaryContainer
                                            else -> Color.Transparent
                                        },
                                    )
                                    .clickable { onSelect(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${date.dayOfMonth}",
                                    color = if (selectedDay) Color.White else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Box(
                                Modifier
                                    .padding(top = 2.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(if (hasRecord) Tomato else Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(record: CalendarRecordUi, isLast: Boolean, onClickNote: () -> Unit) {
    val accent = runCatching { Color(record.color.toInt()) }.getOrDefault(Tomato)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            Modifier.width(52.dp).padding(top = 10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(record.startLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            record.endLabel?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
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
        Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .clickable(onClick = onClickNote)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        record.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                }
                if (record.durationSeconds > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (record.plannedSeconds <= 0) 0f
                            else (record.durationSeconds.toFloat() / record.plannedSeconds).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = accent,
                        trackColor = accent.copy(alpha = 0.15f),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    if (record.note.isNullOrBlank()) {
                        "点击填写心得…"
                    } else {
                        (record.mood?.let { moodEmoji(it) + " " } ?: "") + record.note
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
                        "时长 ${record.durationSeconds / 60} 分 ${record.durationSeconds % 60} 秒",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!isLast) Spacer(Modifier.height(2.dp))
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
