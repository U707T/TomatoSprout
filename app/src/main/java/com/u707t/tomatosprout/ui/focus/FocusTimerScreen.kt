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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    viewModel: FocusViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.findActivity()
    val window = activity?.window

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val originalBrightness = remember { window?.attributes?.screenBrightness ?: -1f }
    DisposableEffect(Unit) {
        onDispose {
            if (window != null) {
                val attrs = window.attributes
                attrs.screenBrightness = originalBrightness
                window.attributes = attrs
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(window, view).apply {
                    show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }
    DisposableEffect(ui.keepScreenOn) {
        if (ui.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars = false
            } else {
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

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
    val task = ui.tasks.firstOrNull { it.id == ui.selectedTaskId }
    val base = task?.let { runCatching { Color(it.color.toInt()) }.getOrNull() } ?: Tomato
    val totalSeconds = timer?.plannedSeconds ?: (ui.focusMinutes * 60)
    val elapsed = timer?.elapsedSeconds(now) ?: 0
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(base, Color.Black, 0.42f),
                        lerp(base, Color.Black, 0.70f),
                        Color(0xFF131111),
                    ),
                ),
            ),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                        subtitle = if (timer == null) "准备开始 · ${ui.focusMinutes} 分钟" else if (timer.isBreak) "休息一下" else "专注进行中",
                        accent = base,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "\u201C$quote\u201D",
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )

            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 4.dp.toPx()
                        drawArc(
                            color = Color.White.copy(alpha = 0.15f),
                            startAngle = -90f, sweepAngle = 360f, useCenter = false,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = Color.White.copy(alpha = 0.82f),
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
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { start() },
                            )
                            Text(
                                "${ui.focusMinutes} 分钟 · ${if (ui.settings.countUpMode) "正向计时" else "倒计时"}",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        } else {
                            Text(
                                TimerEngine.formatClock(displaySeconds),
                                color = Color.White,
                                fontSize = 58.sp,
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

            Spacer(Modifier.height(8.dp))
            Text(
                task?.title ?: "自由专注",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                String.format("%02d", (task?.completedPomos ?: 0) + 1) + " 进行中" +
                    if (timer?.strict == true) " · 严格模式" else "",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            if (timer == null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { viewModel.setFocusMinutes(ui.focusMinutes - 5) }) {
                        Text("−", color = Color.White, fontSize = 24.sp)
                    }
                    Text(
                        "专注 ${ui.focusMinutes} 分钟",
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
                            taskMenu = false; viewModel.selectTask(null)
                        })
                        ui.tasks.forEach { item ->
                            DropdownMenuItem(text = { Text((item.icon ?: "🌱") + " " + item.title) }, onClick = {
                                taskMenu = false; viewModel.selectTask(item.id)
                            })
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // 亮度
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
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { audioMenu = false; viewModel.selectAudio(option) },
                            )
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
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
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
                    modifier = Modifier.size(80.dp),
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
                ) { viewModel.stop() }
            }
        }
    }

    ui.shortPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::discardShortFocus,
            title = { Text("这次专注不足 30 秒") },
            text = { Text("正向计时只有 ${prompt.actualSeconds} 秒，默认不记录。要保留为一条专注记录吗？") },
            confirmButton = { TextButton(onClick = viewModel::recordShortFocus) { Text("仍然记录") } },
            dismissButton = { TextButton(onClick = viewModel::discardShortFocus) { Text("不记录") } },
        )
    }

    ui.summary?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSummary,
            title = { Text(if (summary.strictDiscarded) "本次专注未记录" else "完成专注 🍅") },
            text = {
                Column {
                    if (summary.strictDiscarded) {
                        Text("严格模式下提前结束，本次番茄按规则作废。")
                    } else {
                        Text(
                            (summary.taskTitle ?: "自由专注") + " · " +
                                if (summary.actualSeconds in 1..59) "${summary.actualSeconds} 秒" else "${summary.minutes} 分钟",
                        )
                        if (summary.newBadges.isNotEmpty()) {
                            Text(
                                "解锁徽章：" + summary.newBadges.joinToString("、") { it.name },
                                color = Tomato,
                                modifier = Modifier.padding(top = 6.dp),
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
        modifier = Modifier.size(if (square) 58.dp else 64.dp),
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
