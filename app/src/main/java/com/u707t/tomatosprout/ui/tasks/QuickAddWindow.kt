package com.u707t.tomatosprout.ui.tasks

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.u707t.tomatosprout.ui.components.SectionCaption
import com.u707t.tomatosprout.ui.feedback.LocalFeedback
import com.u707t.tomatosprout.ui.components.SegmentedTabs
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Space
import kotlinx.coroutines.delay

private val typeOptions = listOf(
    QuickTaskType.POMODORO,
    QuickTaskType.HABIT,
    QuickTaskType.TODO,
    QuickTaskType.GOAL,
)
private val typeLabels = listOf("番茄", "习惯", "待办", "目标")

private val timerOptions = listOf(
    QuickTimerMode.COUNTDOWN,
    QuickTimerMode.COUNTUP,
    QuickTimerMode.NONE,
)
private val timerLabels = listOf("倒计时", "正计时", "按次")

/**
 * 新建任务：居中浮层窗口。
 *
 * 标题栏固定、内容可滚动、底部按钮固定，键盘弹出时窗口整体上移并限制在可视区域内；
 * 小屏或大字体下不会把「创建」按钮顶出屏幕。
 */
@Composable
fun QuickAddWindow(
    state: QuickAddUiState,
    onTitle: (String) -> Unit,
    onType: (QuickTaskType) -> Unit,
    onTimer: (QuickTimerMode) -> Unit,
    onDuration: (Int) -> Unit,
    onAdvanced: () -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val feedback = LocalFeedback.current
    val canCreate = state.title.isNotBlank() && !state.saving
    var shown by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "addWindowScale",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(140),
        label = "addWindowAlpha",
    )

    LaunchedEffect(Unit) {
        shown = true
        delay(140)
        runCatching { focusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // safeDrawing 已包含状态栏、导航栏与输入法；不要再叠加 imePadding，否则会缩两次
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Space.xl, vertical = Space.xxl),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(Radii.lg),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                        alpha = cardAlpha
                    },
            ) {
                Column {
                    // 标题栏：窗口语义靠这一行和关闭按钮建立
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = Space.xl, end = Space.sm, top = Space.lg, bottom = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("新建任务", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "写清楚要做什么，回车即可创建",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = {
                                feedback?.tap()
                                keyboard?.hide()
                                onDismiss()
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Space.xl),
                    ) {
                        TextField(
                            value = state.title,
                            onValueChange = onTitle,
                            placeholder = {
                                Text(
                                    "想做什么？",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                )
                            },
                            textStyle = MaterialTheme.typography.titleLarge,
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (canCreate) {
                                        keyboard?.hide()
                                        onCreate()
                                    }
                                },
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )

                        Spacer(Modifier.height(Space.lg))
                        SectionCaption("类型")
                        Spacer(Modifier.height(Space.sm))
                        SegmentedTabs(
                            options = typeLabels,
                            selectedIndex = typeOptions.indexOf(state.type).coerceAtLeast(0),
                            onSelect = { onType(typeOptions[it]) },
                        )

                        Spacer(Modifier.height(Space.lg))
                        SectionCaption("计时方式")
                        Spacer(Modifier.height(Space.sm))
                        SegmentedTabs(
                            options = timerLabels,
                            selectedIndex = timerOptions.indexOf(state.timer).coerceAtLeast(0),
                            onSelect = { onTimer(timerOptions[it]) },
                        )

                        if (state.timer != QuickTimerMode.NONE) {
                            Spacer(Modifier.height(Space.lg))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionCaption("专注时长", Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        feedback?.tap()
                                        onDuration(state.durationMinutes - 5)
                                    },
                                    enabled = state.durationMinutes > 5,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Text("−", style = MaterialTheme.typography.titleLarge)
                                }
                                Text(
                                    "${state.durationMinutes} 分钟",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.width(78.dp),
                                )
                                IconButton(
                                    onClick = {
                                        feedback?.tap()
                                        onDuration(state.durationMinutes + 5)
                                    },
                                    enabled = state.durationMinutes < 180,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "增加 5 分钟",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Text(
                                "倒计时结束会自动打卡；正计时需要手动结束确认。",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Spacer(Modifier.height(Space.md))
                            Text(
                                "按次任务在首页点一下就能打卡，适合喝水、吃药这类小事。",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (state.error != null) {
                            Spacer(Modifier.height(Space.sm))
                            Text(
                                state.error,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(Space.sm))
                    }

                    // 底部按钮固定，内容再长也够得到
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        TextButton(onClick = {
                            feedback?.tap()
                            onAdvanced()
                        }) {
                            Text(
                                "更多设置",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                feedback?.tap()
                                keyboard?.hide()
                                onCreate()
                            },
                            enabled = canCreate,
                            shape = RoundedCornerShape(Radii.sm),
                            modifier = Modifier.height(46.dp).width(120.dp),
                        ) {
                            if (state.saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("创建", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
