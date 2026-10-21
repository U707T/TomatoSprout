package com.u707t.tomatosprout.ui.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.domain.TimerEngine
import com.u707t.tomatosprout.domain.TimerMode
import com.u707t.tomatosprout.domain.TimerPhase
import com.u707t.tomatosprout.ui.components.SoftCard
import com.u707t.tomatosprout.ui.theme.Tomato

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(viewModel: FocusViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val startFocus = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.startFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("专注") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val timer = ui.timer
            val now = ui.nowMono
            val active = timer?.isActive == true
            val totalSeconds = if (timer == null) ui.focusMinutes * 60 else timer.plannedSeconds
            val displaySeconds = when {
                timer == null -> ui.focusMinutes * 60L
                timer.mode == TimerMode.COUNTDOWN -> timer.remainingMillis(now) / 1000
                else -> timer.elapsedMillis(now) / 1000
            }
            val progress = when {
                timer == null -> 0f
                timer.mode == TimerMode.COUNTDOWN -> 1f - (displaySeconds.toFloat() / totalSeconds)
                else -> (displaySeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    timer == null -> "准备开始"
                    timer.isBreak -> "休息中"
                    timer.phase == TimerPhase.PAUSED -> "已暂停"
                    timer.phase == TimerPhase.FINISHED -> "已完成"
                    else -> "专注中"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val breakColor = MaterialTheme.colorScheme.secondary
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val inset = stroke / 2
                    drawArc(
                        color = Tomato.copy(alpha = 0.14f),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = if (timer?.isBreak == true) breakColor else Tomato,
                        startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        TimerEngine.formatClock(displaySeconds),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        when {
                            timer == null -> "${ui.focusMinutes} 分钟"
                            timer.isBreak -> "休息 ${timer.plannedSeconds / 60} 分钟"
                            timer.mode == TimerMode.COUNTUP -> "正计时"
                            else -> "倒计时"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (timer?.strict == true) {
                        Text("严格模式", style = MaterialTheme.typography.labelMedium, color = Tomato)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TaskSelector(
                tasks = ui.tasks,
                selectedId = ui.selectedTaskId,
                enabled = timer?.isActive != true,
                onSelect = viewModel::selectTask,
            )

            if (!active) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.setFocusMinutes(ui.focusMinutes - 5) }) {
                        Text("−", fontSize = 24.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
                        Text("专注时长", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${ui.focusMinutes} 分钟", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { viewModel.setFocusMinutes(ui.focusMinutes + 5) }) {
                        Text("+", fontSize = 24.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = !ui.settings.countUpMode,
                        onClick = { viewModel.setMode(TimerMode.COUNTDOWN) },
                        label = { Text("倒计时") },
                    )
                    FilterChip(
                        selected = ui.settings.countUpMode,
                        onClick = { viewModel.setMode(TimerMode.COUNTUP) },
                        label = { Text("正计时") },
                    )
                }
                SoftCard(Modifier.padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("严格模式", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "本次专注不可暂停；手动提前结束将作废",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = ui.settings.strictMode,
                            onCheckedChange = viewModel::setStrict,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("午夜归属", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "00:00–03:59 开始的专注记到前一天",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            if (ui.settings.midnightShift) "已开启" else "已关闭",
                            style = MaterialTheme.typography.labelLarge,
                            color = Tomato,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            if (timer == null) {
                Button(
                    onClick = startFocus,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("开始专注", style = MaterialTheme.typography.titleMedium) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = viewModel::togglePause,
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = timer.strict.not() || timer.phase == TimerPhase.RUNNING,
                    ) {
                        Text(
                            when (timer.phase) {
                                TimerPhase.PAUSED -> "继续"
                                TimerPhase.RUNNING -> if (timer.strict) "严格模式" else "暂停"
                                else -> "已完成"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::stop,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(if (timer.isBreak) "结束休息" else "结束本次") }
                }
            }

            Spacer(Modifier.height(20.dp))
            SoftCard {
                Text("关于实时活动", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "计时会以高优先级通知 + 自定义 RemoteViews 显示为胶囊卡片。它不申请悬浮窗、不使用系统覆盖窗口；通知被拒绝时自动静默，计时照常。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Tomato, modifier = Modifier.size(16.dp))
                    Text(
                        "切到后台再回来会按真实流逝时间校正。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    ui.summary?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSummary,
            title = {
                Text(
                    when {
                        summary.strictDiscarded -> "本次专注未记录"
                        else -> "完成专注 🍅"
                    },
                )
            },
            text = {
                Column {
                    if (summary.strictDiscarded) {
                        Text("严格模式下提前结束，本次番茄按规则作废。休息一下，下次再来。")
                    } else {
                        Text("${summary.taskTitle ?: "自由专注"} · ${summary.minutes} 分钟")
                        if (summary.newBadges.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "解锁徽章：" + summary.newBadges.joinToString("、") { it.name },
                                color = Tomato,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!summary.strictDiscarded) {
                    TextButton(onClick = { viewModel.dismissSummary(); viewModel.startBreak() }) {
                        Text("开始休息 ${ui.breakMinutes} 分钟")
                    }
                } else {
                    TextButton(onClick = viewModel::dismissSummary) { Text("好") }
                }
            },
            dismissButton = {
                if (!summary.strictDiscarded) {
                    TextButton(onClick = viewModel::dismissSummary) { Text("再来一个") }
                }
            },
        )
    }
}

@Composable
private fun TaskSelector(
    tasks: List<com.u707t.tomatosprout.domain.Task>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = tasks.firstOrNull { it.id == selectedId }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = { if (enabled) expanded = true }) {
            Text(
                (selected?.icon ?: "🎯") + " " + (selected?.title ?: "自由专注") + " ▾",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("🎯 自由专注") },
                onClick = { expanded = false; onSelect(null) },
            )
            tasks.forEach { task ->
                DropdownMenuItem(
                    text = { Text((task.icon ?: "🌱") + " " + task.title) },
                    onClick = { expanded = false; onSelect(task.id) },
                )
            }
        }
    }
}
