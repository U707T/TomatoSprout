package com.u707t.tomatosprout.ui.tasks

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.u707t.tomatosprout.ui.theme.Tomato
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 独立快捷面板：不污染卡片列表状态。毛玻璃在 API 31+ 使用 blur，
 * 低版本自动降级为高透明度纯色面板。
 */
@Composable
fun QuickActionSheet(
    state: QuickActionUiState,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onReminder: () -> Unit,
    onChangeColor: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.86f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "quickScale",
    )
    val panelColor = MaterialTheme.colorScheme.surface.copy(alpha = if (Build.VERSION.SDK_INT >= 31) 0.86f else 0.97f)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.46f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable { }, // 拦截点击，避免穿透遮罩导致面板误关闭
            ) {
                // 只模糊背景层，面板文字保持清晰；低版本退化为半透明纯色
                if (Build.VERSION.SDK_INT >= 31) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .blur(16.dp)
                            .background(panelColor, RoundedCornerShape(24.dp)),
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = panelColor,
                    tonalElevation = 6.dp,
                    shadowElevation = 18.dp,
                ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(Color(state.color.toInt()).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(state.icon ?: "🍅", fontSize = 20.sp) }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(state.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                if (state.countBased) {
                                    "按次打卡 · 长按面板"
                                } else {
                                    "计时任务 · 从专注页完成打卡"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onReminder) {
                            Text("⏰", fontSize = 20.sp, textAlign = TextAlign.Center)
                        }
                        IconButton(onClick = onChangeColor) {
                            Text("🎨", fontSize = 20.sp, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionTile("✏️", "编辑", Modifier.weight(1f), onEdit)
                        ActionTile("📚", "历史", Modifier.weight(1f), onHistory)
                        ActionTile("📊", "统计", Modifier.weight(1f), onStats)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "排序移动",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(72.dp),
                        )
                        ActionTile("⬆️", "上移", Modifier.weight(1f), onMoveUp, enabled = state.canMoveUp)
                        ActionTile("⬇️", "下移", Modifier.weight(1f), onMoveDown, enabled = state.canMoveDown)
                        ActionTile("🗑", "删除", Modifier.weight(1f), onDelete, danger = true)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("本周专注", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val labels = listOf("一", "二", "三", "四", "五", "六", "日")
                        val max = state.weekMinutes.maxOrNull()?.coerceAtLeast(1) ?: 1
                        state.weekMinutes.forEachIndexed { index, minutes ->
                            key(index) {
                                val level = if (minutes <= 0) 0f else (0.28f + 0.72f * (minutes.toFloat() / max))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier.size(22.dp).clip(CircleShape)
                                            .background(
                                                if (level == 0f) {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                } else {
                                                    Tomato.copy(alpha = level)
                                                },
                                            ),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(labels.getOrElse(index) { "" }, style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        if (minutes > 0) "${minutes}分" else "·",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("累计专注", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatDuration(state.totalFocusSeconds), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("专注次数", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${state.totalFocusCount} 次", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("今日", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${state.todayFocusCount} 次", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    Surface(
        modifier = modifier.height(56.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (danger) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(emoji, fontSize = 16.sp, modifier = Modifier.padding(end = 4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (danger) MaterialTheme.colorScheme.error else if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                maxLines = 1,
            )
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

fun weekStart(today: LocalDate = LocalDate.now()): LocalDate = today.minusDays((today.dayOfWeek.value - 1).toLong())

fun percentLabel(used: Long, target: Long?): String {
    if (target == null || target <= 0) return "$used"
    return "${used}/${target} (${((used.toFloat() / target) * 100).roundToInt()}%)"
}
