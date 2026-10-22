package com.u707t.tomatosprout.ui.focus

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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
import com.u707t.tomatosprout.R
import com.u707t.tomatosprout.domain.SessionAttribution
import com.u707t.tomatosprout.domain.SystemAppClock
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.domain.TimerEngine
import com.u707t.tomatosprout.domain.TimerMode
import com.u707t.tomatosprout.domain.TimerPhase
import com.u707t.tomatosprout.ui.components.ConfirmDialog
import com.u707t.tomatosprout.ui.components.rememberHaptics
import kotlin.math.max

private val quotes = listOf(
    "If your happiness depends on what somebody else does, you have a problem.",
    "把注意力放在当下，时间会替你积累答案。",
    "慢一点没关系，只要今天还在向前。",
    "真正的自律，是把想做的事变得更容易开始。",
    "休息也是节奏的一部分。",
)

private val FocusTimeStyle = TextStyle(
    fontWeight = FontWeight.Light,
    fontSize = 72.sp,
    lineHeight = 80.sp,
    letterSpacing = (-1.5).sp,
    fontFeatureSettings = "tnum",
)

private val FocusTimeCompactStyle = TextStyle(
    fontWeight = FontWeight.Light,
    fontSize = 56.sp,
    lineHeight = 64.sp,
    letterSpacing = (-1).sp,
    fontFeatureSettings = "tnum",
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
    val haptics = rememberHaptics()

    // 未完成（中断 / 丢弃 / 不足 30 秒）结算后不再停在计时页：
    // Toast 说明这次没有计入打卡，然后直接回任务页。
    LaunchedEffect(Unit) {
        viewModel.exit.collect { exit ->
            Toast.makeText(context, exit.message, Toast.LENGTH_SHORT).show()
            onExit()
        }
    }

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
    // 专注页背景始终是照片，状态栏/导航栏用白色图标
    LaunchedEffect(Unit) {
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(320),
        label = "focusFade",
    )

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val start = {
        haptics.confirm()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // 选中任务时尊重任务自己的计时方式；自由专注才回落到全局设置
        viewModel.startFocus(ui.selectedTask?.timerMode)
    }

    val timer = ui.timer
    val now = ui.nowMono
    val task = ui.selectedTask
    val base = task?.let { runCatching { Color(it.color.toInt()) }.getOrNull() } ?: Color(0xFFE0553F)
    val totalSeconds = timer?.plannedSeconds ?: (ui.focusMinutes * 60)
    val displaySeconds = when {
        timer == null -> (ui.focusMinutes * 60).toLong()
        timer.mode == TimerMode.COUNTDOWN -> timer.remainingMillis(now) / 1000
        else -> timer.elapsedMillis(now) / 1000
    }
    val elapsedSeconds = when {
        timer == null -> 0L
        timer.mode == TimerMode.COUNTDOWN -> (totalSeconds - displaySeconds).coerceAtLeast(0)
        else -> displaySeconds
    }
    val progress = when {
        timer == null -> 0f
        timer.mode == TimerMode.COUNTDOWN -> 1f - (displaySeconds.toFloat() / max(1, totalSeconds))
        else -> (displaySeconds.toFloat() / max(1, totalSeconds)).coerceIn(0f, 1f)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(480),
        label = "focusProgress",
    )
    val crossDay = timer?.let {
        SessionAttribution.isShifted(it.startedAtEpoch, System.currentTimeMillis(), SystemAppClock.zone())
    } == true
    val effectiveCountUp = when (task?.timerMode) {
        "countup" -> true
        "countdown" -> false
        else -> ui.settings.countUpMode
    }
    val quote = quotes[(task?.id?.hashCode() ?: 0).mod(quotes.size)]
    var audioPanel by remember { mutableStateOf(false) }
    var brightnessPanel by remember { mutableStateOf(false) }
    var resetConfirm by remember { mutableStateOf(false) }
    var taskMenu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(Color(0xFF33565C)),
    ) {
        Image(
            painter = painterResource(R.drawable.bg_focus_dawn),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )

        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().displayCutoutPadding()) {
        val compact = maxHeight < 560.dp
        Column(Modifier.fillMaxSize()) {
            FocusTopBar(
                immersive = ui.immersive,
                keepScreenOn = ui.keepScreenOn,
                onBack = onExit,
                onToggleImmersive = viewModel::toggleImmersive,
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

            if (compact) {
                Spacer(Modifier.height(4.dp))
            } else {
                QuoteBlock(quote)
            }

            Spacer(Modifier.weight(1f))

            CenterBlock(
                timerActive = timer != null,
                seconds = displaySeconds,
                focusMinutes = ui.focusMinutes,
                countUp = effectiveCountUp,
                isBreak = timer?.isBreak == true,
                paused = timer?.phase == TimerPhase.PAUSED,
                strict = timer?.strict == true,
                pomoIndex = (task?.completedPomos ?: 0) + 1,
                crossDay = crossDay,
                taskTitle = task?.title,
                taskIcon = task?.icon,
                taskMenu = taskMenu,
                onTaskMenuChange = { taskMenu = it },
                onSelectTask = { id ->
                    taskMenu = false
                    viewModel.selectTask(id)
                },
                taskList = ui.tasks.filter { it.timerMode != "none" && it.status != TaskStatus.DONE },
                onStart = start,
                onMinus = { viewModel.setFocusMinutes(ui.focusMinutes - 5) },
                onPlus = { viewModel.setFocusMinutes(ui.focusMinutes + 5) },
                interaction = interaction,
                compact = compact,
            )

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = audioPanel,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120)),
            ) {
                FocusAudioPanel(
                    current = ui.audioTrack,
                    playing = ui.audioPlaying,
                    onSelect = {
                        viewModel.selectAudio(it)
                        audioPanel = false
                    },
                )
            }
            AnimatedVisibility(
                visible = brightnessPanel,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120)),
            ) {
                FocusBrightnessPanel(
                    value = if (ui.brightness < 0f) 0.6f else ui.brightness,
                    onChange = viewModel::setBrightness,
                )
            }

            if (timer != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 4.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(2.dp)
                            .background(Color.White.copy(alpha = 0.85f)),
                    )
                }
            }

            FocusBottomControls(
                timerActive = timer != null,
                paused = timer?.phase == TimerPhase.PAUSED,
                canPause = timer == null || !(timer.strict && timer.phase == TimerPhase.RUNNING),
                canStop = timer != null,
                onToggleBrightness = {
                    haptics.tap()
                    audioPanel = false
                    brightnessPanel = !brightnessPanel
                },
                onToggleAudio = {
                    haptics.tap()
                    brightnessPanel = false
                    audioPanel = !audioPanel
                },
                onStartOrPause = {
                    haptics.tap()
                    if (timer == null) start() else viewModel.togglePause()
                },
                onReset = {
                    haptics.tap()
                    if (elapsedSeconds > 0) resetConfirm = true else viewModel.resetTimer()
                },
                onStop = {
                    haptics.tap()
                    viewModel.requestStop()
                },
            )
        }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp),
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
            onReason = viewModel::setStopReason,
        )
    }

    ui.summary?.let {
        SessionSummaryDialog(
            summary = it,
            breakMinutes = ui.breakMinutes,
            longBreakMinutes = ui.settings.longBreakMinutes,
            onDismiss = viewModel::dismissSummary,
            onStartBreak = { long ->
                viewModel.dismissSummary()
                viewModel.startBreak(long)
            },
            onStartAgain = {
                viewModel.dismissSummary()
                start()
            },
        )
    }

    if (resetConfirm) {
        ConfirmDialog(
            title = "放弃当前计时？",
            message = "重置会丢弃当前计时，不写入任何记录；需要保留请改用「停止」。",
            confirmLabel = "放弃并重置",
            destructive = true,
            onConfirm = {
                resetConfirm = false
                viewModel.resetTimer()
            },
            onDismiss = { resetConfirm = false },
        )
    }
}

@Composable
private fun QuoteBlock(quote: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 44.dp, top = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "\u201C",
            fontFamily = FontFamily.Serif,
            fontSize = 46.sp,
            lineHeight = 36.sp,
            color = Color.White.copy(alpha = 0.92f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            quote,
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp,
            lineHeight = 27.sp,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CenterBlock(
    timerActive: Boolean,
    seconds: Long,
    focusMinutes: Int,
    countUp: Boolean,
    isBreak: Boolean,
    paused: Boolean,
    strict: Boolean,
    pomoIndex: Int,
    crossDay: Boolean,
    taskTitle: String?,
    taskIcon: String?,
    taskMenu: Boolean,
    onTaskMenuChange: (Boolean) -> Unit,
    onSelectTask: (String?) -> Unit,
    taskList: List<Task>,
    onStart: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    interaction: MutableInteractionSource,
    compact: Boolean,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.25f)),
    ) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!timerActive) {
            Text(
                "开始",
                fontSize = if (compact) 44.sp else 54.sp,
                lineHeight = if (compact) 52.sp else 62.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .clickable(interactionSource = interaction, indication = null) { onStart() }
                    .padding(horizontal = 36.dp, vertical = 10.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                StepperText("−", onMinus)
                Text(
                    "$focusMinutes 分钟",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.width(92.dp),
                    textAlign = TextAlign.Center,
                )
                StepperText("+", onPlus)
            }
            Text(
                if (countUp) "正向计时 · 手动结束" else "倒计时 · 结束自动打卡",
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 10.sp,
                letterSpacing = 1.sp,
            )
        } else {
            Text(
                TimerEngine.formatClock(seconds),
                style = when {
                    seconds >= 3600 -> FocusTimeCompactStyle
                    compact -> FocusTimeCompactStyle.copy(fontSize = 48.sp, lineHeight = 56.sp)
                    else -> FocusTimeStyle
                },
                color = Color.White,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(if (compact) 12.dp else 22.dp))
            Text(
                if (isBreak) "休息" else pomoIndex.toString().padStart(2, '0'),
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 13.sp,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                buildString {
                    append(
                        when {
                            isBreak -> "休息中"
                            paused -> "已暂停"
                            else -> "进行中"
                        },
                    )
                    if (strict) append(" · 严格模式")
                },
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
            )
        }

        Spacer(Modifier.height(20.dp))
        Box {
            Row(
                Modifier
                    .then(
                        if (!timerActive) {
                            Modifier.clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) { onTaskMenuChange(true) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (taskIcon != null) {
                    Text(taskIcon, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    taskTitle ?: "自由专注",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!timerActive) {
                    Spacer(Modifier.width(4.dp))
                    FocusGlyphIcon(
                        FocusGlyph.CHEVRON_DOWN,
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = taskMenu,
                onDismissRequest = { onTaskMenuChange(false) },
                containerColor = Color(0xFF24201D),
            ) {
                DropdownMenuItem(
                    text = { Text("自由专注", color = Color(0xFFF1E9E2)) },
                    onClick = { onSelectTask(null) },
                )
                taskList.forEach { item ->
                    key(item.id) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (item.icon ?: "🌱") + " " + item.title,
                                    color = Color(0xFFF1E9E2),
                                )
                            },
                            onClick = { onSelectTask(item.id) },
                        )
                    }
                }
            }
        }

        if (timerActive && !isBreak && crossDay) {
            Text(
                "已跨天 · 按设置归属",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 10.sp,
                letterSpacing = 1.sp,
            )
        }
    }
    }
}

@Composable
private fun StepperText(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(48.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White.copy(alpha = 0.72f), fontSize = 20.sp)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
