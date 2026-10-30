package com.u707t.tomatosprout.ui.tasks

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.ui.components.ThinProgress
import com.u707t.tomatosprout.ui.feedback.LocalFeedback
import com.u707t.tomatosprout.ui.theme.Motion
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Space
import com.u707t.tomatosprout.ui.theme.readableContentColor
import java.time.LocalDate

/**
 * 滑动外壳：右滑打卡 / 完成，左滑删除。
 *
 * `confirmValueChange` 一律返回 false —— 动作已经在回调里触发，卡片自己弹回原位，
 * 不会留下「滑走一半卡住」或需要手动 reset 的状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableTaskCard(
    card: TodoCardUi,
    simple: Boolean,
    today: LocalDate,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onAction: () -> Unit,
    onSwipeCheck: () -> Unit,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 计时任务右滑不启动专注：起计时必须是一次明确的点击
    val checkEnabled = card.isCountBased || card.isDone
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> if (checkEnabled) onSwipeCheck()
                SwipeToDismissBoxValue.EndToStart -> onSwipeDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
        positionalThreshold = { distance -> distance * 0.32f },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = checkEnabled,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            SwipeActionBackground(card = card, direction = state.dismissDirection)
        },
    ) {
        TaskCard(
            card = card,
            simple = simple,
            today = today,
            onOpen = onOpen,
            onLongPress = onLongPress,
            onAction = onAction,
        )
    }
}

/** 滑动时露出的底层：左打卡、右删除，颜色只用容器色，不抢卡片本身的注意力。 */
@Composable
private fun SwipeActionBackground(card: TodoCardUi, direction: SwipeToDismissBoxValue) {
    val checkAlpha by animateFloatAsState(
        targetValue = if (direction == SwipeToDismissBoxValue.StartToEnd) 1f else 0f,
        animationSpec = tween(Motion.fast),
        label = "swipeCheck",
    )
    val deleteAlpha by animateFloatAsState(
        targetValue = if (direction == SwipeToDismissBoxValue.EndToStart) 1f else 0f,
        animationSpec = tween(Motion.fast),
        label = "swipeDelete",
    )
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = checkAlpha }
                .clip(RoundedCornerShape(Radii.md))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                if (card.isDone) "重新打开" else "完成 / 打卡",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = deleteAlpha }
                .clip(RoundedCornerShape(Radii.md))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = Space.lg),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "删除",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(Space.sm))
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun TaskCard(
    card: TodoCardUi,
    simple: Boolean,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onAction: () -> Unit,
) {
    val task = card.task
    val due = remember(task.dueAt, today) { dueInfo(task.dueAt, today) }
    val feedback = LocalFeedback.current
    val accent = remember(task.color) {
        runCatching { Color(task.color.toInt()) }.getOrDefault(PaletteFallback)
    }
    val done = card.isDone
    val dim = done || card.checkedToday
    val container = MaterialTheme.colorScheme.surface
    val meta = if (simple) {
        when {
            done -> "已完成"
            card.isHabit && card.streak > 0 -> "连续 ${card.streak} 天"
            card.checkedToday -> "今日已打卡"
            card.isCountBased -> "按次打卡"
            else -> "${task.focusMinutes} 分钟"
        }
    } else {
        buildList {
            add(
                when (task.type) {
                    TaskType.HABIT -> "习惯"
                    TaskType.TODO -> "待办"
                    TaskType.GOAL -> "目标"
                    TaskType.POMODORO -> "番茄"
                },
            )
            when (task.timerMode) {
                "none" -> Unit
                "countup" -> add("正向计时")
                else -> add("${task.focusMinutes} 分钟")
            }
            if (card.isHabit && card.streak > 0) add("连续 ${card.streak} 天")
            if (card.isTimed && card.todayFocusCount > 0) add("今日 ${card.todayFocusCount} 个番茄")
            if (done) add("已完成")
        }.joinToString(" · ")
    }

    val goal = card.goal
    val goalLabel: String? = goal?.takeIf { (it.target ?: 0) > 0 }?.let { g ->
        val target = g.target ?: 0
        if (g.unit == "小时") "目标 ${g.used / 3600}/${target / 3600} 小时"
        else "目标 ${g.used}/$target 次"
    }
    val progressLabel: String? = when {
        task.timerMode == "none" && card.habitTarget > 1 -> "今日 ${card.todayCheckInCount}/${card.habitTarget}"
        card.isHabit && card.habitTarget > 1 -> "今日 ${card.todayCheckInCount}/${card.habitTarget}"
        goalLabel != null -> goalLabel
        card.subtaskTotal > 0 -> "子任务 ${card.subtaskDone}/${card.subtaskTotal}"
        task.estimatedPomos > 0 -> "番茄 ${task.completedPomos}/${task.estimatedPomos}"
        else -> null
    }
    val progress: Float? = when {
        task.timerMode == "none" && card.habitTarget > 1 ->
            card.todayCheckInCount.toFloat() / card.habitTarget
        goal?.ratio != null -> goal.ratio
        card.subtaskTotal > 0 -> card.subtaskDone.toFloat() / card.subtaskTotal
        task.estimatedPomos > 0 -> task.completedPomos.toFloat() / task.estimatedPomos
        else -> null
    }

    Surface(
        shape = RoundedCornerShape(Radii.md),
        color = container,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (card.checkedToday || done) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .combinedClickable(
                onClick = {
                    feedback?.tap()
                    onOpen()
                },
                onLongClick = {
                    feedback?.tap()
                    onLongPress()
                },
            ),
    ) {
        Row(
            Modifier.padding(horizontal = Space.lg, vertical = Space.md).heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(Radii.sm))
                    .background(accent.copy(alpha = if (dim) 0.08f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(task.icon ?: "🍅", fontSize = 19.sp)
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.priority == 0) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .semantics { contentDescription = "紧急且重要" },
                        )
                        Spacer(Modifier.width(Space.xs))
                    }
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (due != null) {
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            due.text,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (due.overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (!simple && progress != null && progressLabel != null) {
                    Spacer(Modifier.height(Space.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThinProgress(
                            progress = progress,
                            modifier = Modifier.weight(1f),
                            color = if (dim) accent.copy(alpha = 0.5f) else accent,
                            height = 3.dp,
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.width(Space.sm))
            TaskAction(card = card, accent = accent, onAction = onAction)
        }
    }
}

@Composable
private fun TaskAction(card: TodoCardUi, accent: Color, onAction: () -> Unit) {
    val checked = card.isDone || card.checkedToday
    val bg = if (checked) accent.copy(alpha = 0.12f) else accent
    // 浅色任务色上白字会糊，按亮度切到深色前景
    val fg = if (checked) accent else accent.readableContentColor()
    val icon = when {
        card.isDone -> Icons.Default.Check
        card.isCountBased -> if (card.checkedToday) Icons.Default.Check else Icons.Default.Add
        else -> Icons.Default.PlayArrow
    }
    val description = when {
        card.isDone -> "重新打开"
        card.checkedToday -> "撤销打卡"
        card.isCountBased -> "打卡"
        else -> "开始专注"
    }
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onAction,
            shape = CircleShape,
            color = bg,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Crossfade(
                    targetState = icon,
                    animationSpec = tween(180),
                    label = "taskActionIcon",
                ) { current ->
                    Icon(
                        current,
                        contentDescription = description,
                        tint = fg,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private val PaletteFallback = Color(0xFFE0553F)
