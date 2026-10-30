package com.u707t.tomatosprout.ui.tasks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Space

/** 撤销窗口：够看完一句话再决定，又不至于一直挂着。 */
internal const val UNDO_WINDOW_MS = 10_000L

/**
 * 撤销条状态。用独立组件而不是 Snackbar：
 * Snackbar 会被后来的消息顶掉，撤销窗口就没了。
 */
internal class UndoBarState(
    val message: String,
    val actionLabel: String,
    private val onAction: () -> Unit,
    private val onExpire: () -> Unit,
    /** 关联的任务；用户对同一任务又有新动作时，用它来撤掉过期的撤销条。 */
    val taskId: String? = null,
) {
    private var consumed = false

    /** 点「撤销/恢复」。与到期回调互斥，谁先到算谁。 */
    fun action() {
        if (consumed) return
        consumed = true
        onAction()
    }

    /** 窗口结束（或被下一条替换）。与点击互斥。 */
    fun expire() {
        if (consumed) return
        consumed = true
        onExpire()
    }
}


/**
 * 底部撤销条：一条消息 + 一个动作 + 10 秒倒计时。
 * 和 Snackbar 分开渲染，避免结算消息把「撤销」顶掉。
 */
@Composable
internal fun UndoBar(bar: UndoBarState, onAction: () -> Unit) {
    val progress = remember(bar) { Animatable(1f) }
    LaunchedEffect(bar) {
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(UNDO_WINDOW_MS.toInt(), easing = LinearEasing),
        )
    }
    Surface(
        shape = RoundedCornerShape(Radii.sm),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.padding(start = Space.lg, end = Space.sm, top = Space.sm, bottom = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    bar.message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAction) {
                    Text(
                        bar.actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.inversePrimary,
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.18f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.value)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.inversePrimary),
                )
            }
        }
    }
}

