package com.u707t.tomatosprout.ui.edit

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.domain.Frequency
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.ui.components.SectionHeader
import com.u707t.tomatosprout.ui.components.SoftCard
import com.u707t.tomatosprout.ui.theme.Tomato
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val iconChoices = listOf("🌱", "📚", "💧", "🏃", "🧘", "🍅", "✏️", "🎸", "🌙", "🧹", "💊", "🥗")
private val colorChoices = listOf(0xFFFF6B6B, 0xFFF3B58A, 0xFFF2CE68, 0xFF8FAE83, 0xFF7FB5B5, 0xFF8C9ED6, 0xFFC59ACF, 0xFF8C7A70)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditScreen(
    onBack: () -> Unit,
    viewModel: HabitEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var subtaskText by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }
    val reminderPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) { viewModel.saved.collect { onBack() } }

    fun pickDate(initial: LocalDate, onPick: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, month, day -> onPick(LocalDate.of(year, month + 1, day)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth,
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "新建" else "编辑") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除任务",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = !state.loading && !state.saving) {
                        Text(if (state.saving) "保存中…" else "保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            SoftCard {
                Text("类型", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(
                        TaskType.HABIT to "习惯",
                        TaskType.TODO to "待办",
                        TaskType.GOAL to "目标",
                        TaskType.POMODORO to "番茄任务",
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { viewModel.setType(type) },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    label = { Text("备注（可选）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionHeader("计时方式")
            SoftCard {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(
                        "countdown" to "倒计时",
                        "countup" to "正向计时",
                        "none" to "不计时",
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = state.timerMode == mode,
                            onClick = { viewModel.setTimerMode(mode) },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                if (state.timerMode == "countdown") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("单次时长", modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.setFocusMinutes(state.focusMinutes - 5) }) { Text("−") }
                        Text("${state.focusMinutes} 分钟")
                        IconButton(onClick = { viewModel.setFocusMinutes(state.focusMinutes + 5) }) { Text("+") }
                    }
                }
            }

            SectionHeader("外观")
            SoftCard {
                Text("图标", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    iconChoices.forEach { icon ->
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(
                                    if (state.icon == icon) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    CircleShape,
                                )
                                .clickable { viewModel.setIcon(icon) },
                            contentAlignment = Alignment.Center,
                        ) { Text(icon, fontSize = 22.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("颜色", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    colorChoices.forEach { color ->
                        Box(
                            Modifier
                                .padding(4.dp)
                                .size(36.dp)
                                .background(Color(color.toInt()), CircleShape)
                                .clickable { viewModel.setColor(color) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.color == color) Text("✓", color = Color.White)
                        }
                    }
                }
            }

            SectionHeader("优先级（四象限）")
            SoftCard {
                val labels = listOf("紧急且重要", "重要不紧急", "紧急不重要", "都不紧急")
                Column {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        labels.forEachIndexed { index, label ->
                            FilterChip(
                                selected = state.priority == index,
                                onClick = { viewModel.setPriority(index) },
                                label = { Text(label) },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                }
            }

            if (state.type == TaskType.HABIT || state.type == TaskType.GOAL) {
                SectionHeader("节奏")
                SoftCard {
                    if (state.type == TaskType.HABIT) {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            listOf(
                                Frequency.DAILY to "每天",
                                Frequency.WEEKDAYS to "工作日",
                                Frequency.WEEKLY to "每周几",
                                Frequency.CUSTOM to "自定义间隔",
                            ).forEach { (freq, label) ->
                                FilterChip(
                                    selected = state.frequency == freq,
                                    onClick = { viewModel.setFrequency(freq) },
                                    label = { Text(label) },
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            }
                        }
                        if (state.frequency == Frequency.WEEKLY) {
                            Spacer(Modifier.height(8.dp))
                            Row {
                                listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                                    val bit = 1 shl index
                                    FilterChip(
                                        selected = state.weekdaysMask and bit != 0,
                                        onClick = {
                                            viewModel.setWeekdaysMask(state.weekdaysMask xor bit)
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.padding(end = 2.dp),
                                    )
                                }
                            }
                        }
                        if (state.frequency == Frequency.CUSTOM) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("每")
                                OutlinedTextField(
                                    value = state.intervalDays.toString(),
                                    onValueChange = { viewModel.setIntervalDays(it.filter(Char::isDigit).toIntOrNull() ?: 1) },
                                    modifier = Modifier.width(84.dp).padding(horizontal = 6.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                Text("天一次")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("每周期目标次数", modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.setTargetCount(state.targetCount - 1) }) { Text("−") }
                            Text("${state.targetCount}")
                            IconButton(onClick = { viewModel.setTargetCount(state.targetCount + 1) }) { Text("+") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("开始日期", modifier = Modifier.weight(1f))
                        TextButton(onClick = { pickDate(state.startDate, viewModel::setStartDate) }) {
                            Text(state.startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                        }
                    }
                    if (state.type == TaskType.GOAL) {
                        OutlinedTextField(
                            value = state.goalTarget,
                            onValueChange = viewModel::setGoalTarget,
                            label = { Text("累计目标次数（留空为不限）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("截止日期", modifier = Modifier.weight(1f))
                            TextButton(onClick = { pickDate(state.goalEndDate, viewModel::setGoalEndDate) }) {
                                Text(state.goalEndDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                            }
                        }
                    }
                }
            }

            if (state.type == TaskType.TODO || state.type == TaskType.POMODORO) {
                SectionHeader("截止时间")
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.dueAt?.let {
                                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                            } ?: "未设置",
                            modifier = Modifier.weight(1f),
                        )
                        if (state.dueAt != null) {
                            TextButton(onClick = { viewModel.setDueAt(null) }) { Text("清除") }
                        }
                        TextButton(onClick = {
                            pickDate(
                                state.dueAt?.let {
                                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                                } ?: LocalDate.now(),
                            ) { date ->
                                viewModel.setDueAt(date.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                            }
                        }) { Text("选择") }
                    }
                }
            }

            run {
                SectionHeader("本地提醒")
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("定时提醒")
                            Text(
                                "不申请精确闹钟权限，系统可能延迟几分钟",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.reminderEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setReminderEnabled(enabled)
                                if (enabled && Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    reminderPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                        )
                    }
                    if (state.reminderEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("提醒时间", modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                val parts = state.reminderTime.split(":")
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        viewModel.setReminderTime("%02d:%02d".format(hour, minute))
                                    },
                                    parts.getOrNull(0)?.toIntOrNull() ?: 20,
                                    parts.getOrNull(1)?.toIntOrNull() ?: 0,
                                    true,
                                ).show()
                            }) { Text(state.reminderTime) }
                        }
                    }
                }
            }

            if (state.type == TaskType.TODO && state.subtasks.isEmpty()) {
                SectionHeader("子任务")
                SoftCard {
                    Text(
                        "把一个待办拆成更小的步骤，全部完成时父任务自动完成。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.type == TaskType.TODO && state.subtasks.isNotEmpty()) {
                SectionHeader("子任务")
                SoftCard {
                    state.subtasks.forEach { child ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = child.done,
                                onCheckedChange = { viewModel.toggleSubtask(child) },
                            )
                            Text(child.title, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.deleteSubtask(child) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除子任务", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (state.type == TaskType.TODO) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = subtaskText,
                        onValueChange = { subtaskText = it.take(60) },
                        placeholder = { Text("添加子任务") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    TextButton(onClick = { viewModel.addSubtask(subtaskText); subtaskText = "" }) { Text("添加") }
                }
            }

            if (!state.error.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (state.isNew) "创建" else "保存修改") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除这件事？") },
            text = {
                Text(
                    "任务会从首页移除，无法恢复；已有的打卡与专注记录会保留，" +
                        "在历史页显示为「[已删除] 原任务名」。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        viewModel.delete()
                    },
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
        )
    }
}
