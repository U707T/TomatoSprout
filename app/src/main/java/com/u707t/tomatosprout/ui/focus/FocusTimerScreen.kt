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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
            FocusTopBar(
                immersive = ui.immersive,
                audioPlaying = ui.audioPlaying,
                keepScreenOn = ui.keepScreenOn,
                onBack = onExit,
                onToggleImmersive = viewModel::toggleImmersive,
                onToggleAudio = viewModel::toggleAudio,
                onToggleKeepScreenOn = { viewModel.setKeepScreenOn(!ui.keepScreenOn) },
                onShare = {
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
                },
            )

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
                    task?.type == TaskType.HABIT ->
                        "完成后自动打卡 · 第 ${(task.completedPomos + 1).coerceAtLeast(1)} 个番茄"
                    task != null && timer.mode == TimerMode.COUNTDOWN ->
                        "第 ${(task.completedPomos + 1).coerceAtLeast(1)} 个番茄"
                    task != null -> "正向计时 · 完成后确认记录"
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

            FocusBottomControls(
                ui = ui,
                base = base,
                audioMenuExpanded = audioMenu,
                onAudioMenuChange = { audioMenu = it },
                onSelectAudio = viewModel::selectAudio,
                onBrightness = viewModel::setBrightness,
                onReset = viewModel::resetTimer,
                onStartOrPause = { if (timer == null) start() else viewModel.togglePause() },
                onStop = viewModel::requestStop,
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }

    ui.recordPrompt?.let {
        RecordPromptDialog(
            prompt = it,
            onConfirm = viewModel::confirmRecord,
            onDiscard = viewModel::discardRecord,
        )
    }

    ui.stopConfirm?.let {
        StopConfirmDialog(
            confirm = it,
            onCancel = viewModel::cancelStop,
            onConfirm = viewModel::confirmStop,
        )
    }

    ui.summary?.let {
        SessionSummaryDialog(
            summary = it,
            breakMinutes = ui.breakMinutes,
            onDismiss = viewModel::dismissSummary,
            onStartBreak = {
                viewModel.dismissSummary()
                viewModel.startBreak()
            },
            onStartAgain = {
                viewModel.dismissSummary()
                start()
            },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
