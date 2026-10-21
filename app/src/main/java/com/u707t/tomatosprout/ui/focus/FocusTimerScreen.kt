package com.u707t.tomatosprout.ui.focus

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.domain.SessionAttribution
import com.u707t.tomatosprout.domain.SystemAppClock
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.domain.TimerEngine
import com.u707t.tomatosprout.domain.TimerMode
import com.u707t.tomatosprout.domain.TimerPhase
import com.u707t.tomatosprout.ui.theme.Tomato
import kotlin.math.max

private val quotes = listOf(
    "If your happiness depends on what somebody else does, you have a problem.",
    "把注意力放在当下，时间会替你积累答案。",
    "慢一点没关系，只要今天还在向前。",
    "真正的自律，是把想做的事变得更容易开始。",
    "休息也是节奏的一部分。",
)

@Composable
fun FocusTimerScreen(
    onExit: () -> Unit,
    viewModel: FocusTimerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.findActivity()
    val window = activity?.window
    val snackbar = remember { SnackbarHostState() }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // 退出页面 / 进程回收时恢复系统亮度，并释放屏幕常亮标记
    val originalBrightness = remember { window?.attributes?.screenBrightness ?: -1f }
    DisposableEffect(Unit) {
        onDispose {
            if (window != null) {
                val attrs = window.attributes
                attrs.screenBrightness = originalBrightness
                window.attributes = attrs
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    DisposableEffect(ui.keepScreenOn) {
        if (ui.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(ui.brightness) {
        if (window != null && ui.brightness > 0f) {
            val attrs = window.attributes
            attrs.screenBrightness = ui.brightness
            window.attributes = attrs
        }
    }
    LaunchedEffect(ui.immersive) {
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (ui.immersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars = false
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // 进入全屏时淡入，避免导航切换时闪黑
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(260),
        label = "focusFade",
    )

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val start = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.startFocus()
    }

    val timer = ui.timer
    val now = ui.nowMono
    val task = ui.selectedTask
    val base = task?.let { runCatching { Color(it.color.toInt()) }.getOrNull() } ?: Tomato
    val totalSeconds = timer?.plannedSeconds ?: (ui.focusMinutes * 60)
    val displaySeconds = when {
        timer == null -> (ui.focusMinutes * 60).toLong()
        timer.mode == TimerMode.COUNTDOWN -> timer.remainingMillis(now) / 1000
        else -> timer.elapsedMillis(now) / 1000
    }
    val progress = when {
        timer == null -> 0f
        timer.mode == TimerMode.COUNTDOWN -> 1f - (displaySeconds.toFloat() / max(1, totalSeconds))
        else -> (displaySeconds.toFloat() / max(1, totalSeconds)).coerceIn(0f, 1f)
    }
    val quote = quotes[(task?.id?.hashCode() ?: 0).mod(quotes.size)]
    var audioMenu by remember { mutableStateOf(false) }
    var taskMenu by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(base, Color.Black, 0.28f),
                        lerp(base, Color.Black, 0.62f),
                        Color(0xFF121010),
                    ),
                ),
            ),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        ) {
            // 顶栏固定，不随内容滚动
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "退出专注", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                TopAction("⛶", "沉浸模式", active = ui.immersive) { viewModel.toggleImmersive() }
                TopAction("🎧", "白噪音", active = ui.audioPlaying) { viewModel.toggleAudio() }
                TopAction("☀", "屏幕常亮", active = ui.keepScreenOn) { viewModel.setKeepScreenOn(!ui.keepScreenOn) }
                TopAction("↗", "分享海报", active = false) {
                    SharePosterUtils.share(
                        context = context,
                        title = task?.title ?: "自由专注",
                        timeText = TimerEngine.formatClock(displaySeconds),
                        subtitle = if (timer == null) {
                            "准备开始 · ${ui.focusMinutes} 分钟"
                        } else if (timer.isBreak) {
                            "休息一下"
                        } else {
                            "专注进行中"
                        },
                        accent = base,
                    )
                }
            }

            // 中部可滚动：引语、圆环、任务信息与准备态选择器
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "\u201C$quote\u201D",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(12.dp))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 4.dp.toPx()
                            drawArc(
                                color = Color.White.copy(alpha = 0.14f),
                                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                            drawArc(
                                color = Color.White.copy(alpha = 0.86f),
                                startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (timer == null) {
                                Text(
                                    "开始",
                                    color = Color.White,
                                    fontSize = 52.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { start() },
                                )
                                Text(
                                    "${ui.focusMinutes} 分钟 · ${if (ui.settings.countUpMode) "正向计时" else "倒计时"}",
                                    color = Color.White.copy(alpha = 0.70f),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            } else {
                                Text(
                                    TimerEngine.formatClock(displaySeconds),
                                    color = Color.White,
                                    fontSize = if (displaySeconds >= 3600) 44.sp else 56.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    when {
                                        timer.isBreak -> "休息中"
                                        timer.phase == TimerPhase.PAUSED -> "已暂停"
                                        timer.mode == TimerMode.COUNTUP -> "正向计时"
                                        else -> "倒计时"
                                    },
                                    color = Color.White.copy(alpha = 0.68f),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    (task?.icon ?: "🎯") + " " + (task?.title ?: "自由专注"),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                val statusLine = when {
                    timer == null -> "选好任务与时长，然后开始"
                    timer.isBreak -> "休息也是节奏的一部分"
                    timer.phase == TimerPhase.PAUSED ->
                        if (ui.stopConfirm != null || ui.recordPrompt != null) "等待你的确认" else "已暂停"
                    task?.type == TaskType.POMODORO ->
                        "第 ${(task.completedPomos + 1).coerceAtLeast(1)} 个番茄"
                    task?.type == TaskType.HABIT -> "完成后自动打卡"
                    else -> "专注进行中"
                } + if (timer?.strict == true) " · 严格模式" else ""
                Text(
                    statusLine,
                    color = Color.White.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                if (timer?.isBreak == false) {
                    val crossDayNow = timer?.let {
                        SessionAttribution.isShifted(it.startedAtEpoch, System.currentTimeMillis(), SystemAppClock.zone())
                    } == true
                    if (crossDayNow) {
                        Text(
                            if (ui.settings.midnightShift) "已跨天 · 归入开始日" else "已跨天 · 归入结束日",
                            color = Color.White.copy(alpha = 0.48f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                if (timer == null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { viewModel.setFocusMinutes(ui.focusMinutes - 5) }) {
                            Text("−", color = Color.White, fontSize = 24.sp)
                        }
                        Text(
                            "${ui.focusMinutes} 分钟",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        TextButton(onClick = { viewModel.setFocusMinutes(ui.focusMinutes + 5) }) {
                            Text("+", color = Color.White, fontSize = 24.sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { taskMenu = true }) {
                            Text(
                                (task?.icon ?: "🎯") + " " + (task?.title ?: "自由专注") + " ▾",
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                        DropdownMenu(expanded = taskMenu, onDismissRequest = { taskMenu = false }) {
                            DropdownMenuItem(text = { Text("🎯 自由专注") }, onClick = {
                                taskMenu = false
                                viewModel.selectTask(null)
                            })
                            ui.tasks.filter { it.timerMode != "none" }.forEach { item ->
                                key(item.id) {
                                    DropdownMenuItem(
                                        text = { Text((item.icon ?: "🌱") + " " + item.title) },
                                        onClick = {
                                            taskMenu = false
                                            viewModel.selectTask(item.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 底部固定控制区，避免大屏出现整段空白
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.24f)),
                        ),
                    )
                    .padding(horizontal = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("☀", color = Color.White.copy(alpha = 0.7f))
                    Slider(
                        value = if (ui.brightness < 0f) 0.6f else ui.brightness,
                        onValueChange = viewModel::setBrightness,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White.copy(alpha = 0.7f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                        ),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box {
                        TextButton(onClick = { audioMenu = true }) {
                            Text(
                                "🎵 " + ui.audioTrack.label + if (ui.audioPlaying) " · 播放中" else "",
                                color = Color.White.copy(alpha = 0.82f),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        DropdownMenu(expanded = audioMenu, onDismissRequest = { audioMenu = false }) {
                            FocusAudioOption.entries.forEach { option ->
                                key(option) {
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            audioMenu = false
                                            viewModel.selectAudio(option)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "白噪音为本地音频",
                        color = Color.White.copy(alpha = 0.38f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleControl(
                        label = "重置",
                        enabled = timer != null && !(timer.strict && timer.phase == TimerPhase.RUNNING),
                    ) { viewModel.resetTimer() }
                    Surface(
                        onClick = {
                            if (timer == null) start() else viewModel.togglePause()
                        },
                        enabled = timer == null || !(timer.strict && timer.phase == TimerPhase.RUNNING),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(78.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                when {
                                    timer == null -> "开始"
                                    timer.phase == TimerPhase.PAUSED -> "继续"
                                    else -> "暂停"
                                },
                                color = lerp(base, Color.Black, 0.55f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                            )
                        }
                    }
                    CircleControl(
                        label = "停止",
                        enabled = timer != null,
                        square = true,
                    ) { viewModel.requestStop() }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }

    ui.recordPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::discardRecord,
            title = { Text("记录这次专注？") },
            text = {
                Text(
                    "正向计时 ${prompt.actualSeconds} 秒。记录后会写入一条专注记录，" +
                        "并自动完成今天「${prompt.taskTitle ?: "自由专注"}」的打卡。",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRecord) { Text("记录") } },
            dismissButton = { TextButton(onClick = viewModel::discardRecord) { Text("不记录") } },
        )
    }

    ui.stopConfirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = viewModel::cancelStop,
            title = { Text("结束本次专注？") },
            text = {
                Column {
                    Text("已专注 ${confirm.actualSeconds / 60} 分 ${confirm.actualSeconds % 60} 秒，还差 ${confirm.remainingSeconds / 60} 分钟。")
                    Text(
                        if (confirm.strict) {
                            "严格模式下提前结束，本次专注会彻底作废，不写入任何记录。"
                        } else {
                            "结束后只记录一条「中断」记录，不会触发打卡。"
                        },
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmStop) { Text("结束") } },
            dismissButton = { TextButton(onClick = viewModel::cancelStop) { Text("继续专注") } },
        )
    }

    ui.summary?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSummary,
            title = {
                Text(
                    when {
                        summary.isBreak -> "休息结束 ☕"
                        summary.discarded -> "本次专注未记录"
                        summary.interrupted -> "已记录中断"
                        else -> "完成专注 🍅"
                    },
                )
            },
            text = {
                Column {
                    when {
                        summary.discarded -> {
                            Text(summary.discardReason ?: "本次专注已按规则丢弃。")
                        }
                        summary.isBreak -> {
                            Text("休息了 ${summary.minutes} 分钟，准备好再开始。")
                        }
                        else -> {
                            Text(
                                (summary.taskTitle ?: "自由专注") + " · " +
                                    if (summary.actualSeconds in 1..59) {
                                        "${summary.actualSeconds} 秒"
                                    } else {
                                        "${summary.minutes} 分钟"
                                    },
                            )
                            if (summary.interrupted) {
                                Text(
                                    "未达计划时长，只记录中断状态，不计入打卡。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            } else if (summary.checkInWritten) {
                                Text(
                                    "已自动打卡" + if (summary.streak > 0) " · 连续 ${summary.streak} 天" else "",
                                    color = Tomato,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            } else if (summary.streak > 0) {
                                Text(
                                    "今日已完成打卡 · 连续 ${summary.streak} 天",
                                    color = Tomato,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            if (summary.crossDay) {
                                Text(
                                    "跨天专注 · 归入 " + (summary.attributedDate?.toString() ?: "开始日"),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (summary.newBadges.isNotEmpty()) {
                                Text(
                                    "解锁徽章：" + summary.newBadges.joinToString("、") { it.name },
                                    color = Tomato,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                when {
                    summary.discarded -> TextButton(onClick = viewModel::dismissSummary) { Text("好") }
                    summary.isBreak -> TextButton(onClick = {
                        viewModel.dismissSummary()
                        start()
                    }) { Text("再专注一次") }
                    else -> TextButton(onClick = {
                        viewModel.dismissSummary()
                        viewModel.startBreak()
                    }) { Text("开始休息 ${ui.breakMinutes} 分钟") }
                }
            },
            dismissButton = {
                if (!summary.discarded && !summary.isBreak) {
                    TextButton(onClick = viewModel::dismissSummary) { Text("再来一个") }
                }
            },
        )
    }
}

@Composable
private fun TopAction(label: String, description: String, active: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            label,
            color = if (active) Tomato else Color.White.copy(alpha = 0.78f),
            fontSize = 18.sp,
            modifier = Modifier.padding(4.dp),
        )
    }
}

@Composable
private fun CircleControl(label: String, enabled: Boolean, square: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = if (square) RoundedCornerShape(14.dp) else CircleShape,
        color = Color.White.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
        modifier = Modifier.size(if (square) 58.dp else 64.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (square) "■" else "↺",
                color = Color.White.copy(alpha = if (enabled) 0.86f else 0.3f),
                fontSize = if (square) 20.sp else 24.sp,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
