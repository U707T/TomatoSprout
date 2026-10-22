package com.u707t.tomatosprout.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.u707t.tomatosprout.ui.components.AppBottomSheet
import com.u707t.tomatosprout.ui.components.Metric
import com.u707t.tomatosprout.ui.components.SectionCaption
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Space

/**
 * 长按任务后的操作面板。原来是一个居中弹窗，现在改成底部弹层：
 * 拇指更容易够到，也能把「编辑 / 历史 / 统计」和排序放在同一屏。
 */
@Composable
fun QuickActionSheet(
    state: QuickActionUiState,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleDone: () -> Unit,
    onReminder: () -> Unit,
    onChangeColor: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
) {
    val accent = runCatching { Color(state.color.toInt()) }.getOrDefault(MaterialTheme.colorScheme.primary)

    AppBottomSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(Radii.sm))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.icon ?: "🍅", fontSize = 20.sp)
            }
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
                Text(
                    if (state.countBased) "按次打卡 · 长按打开此面板" else "计时任务 · 完成后自动打卡",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = onToggleDone,
                shape = RoundedCornerShape(Radii.sm),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    if (state.isDone) "重新打开" else "标记完成",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                )
            }
        }

        Spacer(Modifier.height(Space.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            ActionTile("编辑", Modifier.weight(1f), onEdit)
            ActionTile("历史", Modifier.weight(1f), onHistory)
            ActionTile("统计", Modifier.weight(1f), onStats)
        }
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            ActionTile("上移", Modifier.weight(1f), onMoveUp, enabled = state.canMoveUp)
            ActionTile("下移", Modifier.weight(1f), onMoveDown, enabled = state.canMoveDown)
            ActionTile("删除", Modifier.weight(1f), onDelete, danger = true)
        }
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            ActionTile("提醒", Modifier.weight(1f), onReminder)
            ActionTile("换色", Modifier.weight(1f), onChangeColor)
        }

        Spacer(Modifier.height(Space.xl))
        SectionCaption("本周专注")
        Spacer(Modifier.height(Space.sm))
        WeekBars(state.weekMinutes)

        Spacer(Modifier.height(Space.lg))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.sm))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(vertical = Space.md, horizontal = Space.lg),
        ) {
            Metric(formatDuration(state.totalFocusSeconds), "累计专注", Modifier.weight(1f))
            Metric("${state.totalFocusCount}", "专注次数", Modifier.weight(1f), unit = "次")
            Metric("${state.todayFocusCount}", "今日", Modifier.weight(1f), unit = "次")
        }
        Spacer(Modifier.height(Space.sm))
    }
}

@Composable
private fun WeekBars(minutes: List<Int>) {
    val max = (minutes.maxOrNull() ?: 0).coerceAtLeast(1)
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(
        Modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        minutes.forEachIndexed { index, value ->
            key(index) {
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (value > 0) "$value" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((6 + 46 * (value.toFloat() / max)).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (value > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        labels.getOrElse(index) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(Radii.sm),
        color = if (danger) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (danger) MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        }
    }
}

fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        minutes > 0 -> "${minutes}分钟"
        else -> "${totalSeconds}秒"
    }
}
