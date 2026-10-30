package com.u707t.tomatosprout.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.u707t.tomatosprout.domain.DayAggregate
import com.u707t.tomatosprout.domain.HabitSnapshot
import com.u707t.tomatosprout.ui.feedback.LocalFeedback
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Space
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * 纸面卡片：1dp 描边 + 平面，不使用重阴影。
 * 阴影只留给浮层（底部弹层、FAB），列表卡片靠描边区分层级。
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(Radii.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(Space.lg), content = content)
    }
}

@Composable
fun SectionHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.xxl, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.xxl, vertical = Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(Space.lg))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.xl))
            Button(onClick = onAction, shape = RoundedCornerShape(Radii.sm)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 9.dp,
    label: String? = null,
    subLabel: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "ring",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val inset = stroke.toPx() / 2
            drawArc(
                color = color.copy(alpha = 0.14f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke.toPx(), this.size.height - stroke.toPx()),
                style = Stroke(stroke.toPx(), cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - stroke.toPx(), this.size.height - stroke.toPx()),
                    style = Stroke(stroke.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (subLabel != null) {
                Text(
                    subLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null) {
    Column(modifier.padding(vertical = Space.xs)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (unit != null) {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 3.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CheckCircle(checked: Boolean, onClick: () -> Unit, description: String) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "checkScale",
    )
    val background by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(180),
        label = "checkBg",
    )
    Box(
        Modifier
            .size(48.dp)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = background,
            border = if (checked) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.size(30.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        ) {
            if (checked) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(7.dp),
                )
            }
        }
    }
}

/** 月热力图：7 列（周一开头）× 6 行。 */
@Composable
fun MonthHeatmap(
    month: YearMonth,
    days: Map<LocalDate, DayAggregate>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val maxScore = remember(month, days) {
        days.filterKeys { YearMonth.from(it) == month }
            .values.maxOfOrNull { it.checkIns + it.focusSeconds / 1500f } ?: 1f
    }.coerceAtLeast(1f)
    val offset = month.atDay(1).dayOfWeek.value - 1
    val total = month.lengthOfMonth()
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(6) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(7) { col ->
                        val index = row * 7 + col - offset
                        if (index in 0 until total) {
                            val date = month.atDay(index + 1)
                            val aggregate = days[date]
                            val score = (aggregate?.checkIns ?: 0) + (aggregate?.focusSeconds ?: 0) / 1500f
                            val alpha = if (score <= 0f) 0f else (0.18f + 0.82f * (score / maxScore)).coerceIn(0.18f, 1f)
                            val isToday = date == LocalDate.now()
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(
                                        if (alpha == 0f) base else lerp(base, accent, alpha),
                                        RoundedCornerShape(Radii.xs),
                                    )
                                    .then(
                                        if (isToday) {
                                            Modifier.border(
                                                1.5.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                                RoundedCornerShape(Radii.xs),
                                            )
                                        } else Modifier,
                                    )
                                    .clickable { onDayClick(date) }
                                    .semantics {
                                        contentDescription =
                                            "$date，打卡 ${aggregate?.checkIns ?: 0} 次，专注 ${(aggregate?.focusSeconds ?: 0) / 60} 分钟"
                                    },
                            )
                        } else {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

/** 年热力图：GitHub 风格 53 周，可横向滚动。 */
@Composable
fun YearHeatmap(year: Int, days: Map<LocalDate, DayAggregate>, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val start = LocalDate.of(year, 1, 1)
    val end = LocalDate.of(year, 12, 31)
    val gridStart = start.minusDays((start.dayOfWeek.value - 1).toLong())
    val weeks = ((end.toEpochDay() - gridStart.toEpochDay()) / 7 + 1).toInt()
    val maxScore = remember(year, days) {
        days.filterKeys { it.year == year }.values.maxOfOrNull { it.checkIns + it.focusSeconds / 1500f } ?: 1f
    }.coerceAtLeast(1f)
    Column(modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width((weeks * 15).dp).height((7 * 15 + 18).dp)) {
                val cell = 11.dp.toPx()
                val gap = 4.dp.toPx()
                for (w in 0 until weeks) {
                    for (d in 0..6) {
                        val date = gridStart.plusDays((w * 7 + d).toLong())
                        if (date.year != year) continue
                        val a = days[date]
                        val score = (a?.checkIns ?: 0) + (a?.focusSeconds ?: 0) / 1500f
                        val alpha = if (score <= 0f) 0f else (0.18f + 0.82f * (score / maxScore)).coerceIn(0.18f, 1f)
                        drawRoundRect(
                            color = if (alpha == 0f) base else lerp(base, accent, alpha),
                            topLeft = Offset(w * (cell + gap), d * (cell + gap) + 16.dp.toPx()),
                            size = Size(cell, cell),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach {
                Box(
                    Modifier
                        .padding(horizontal = 2.dp, vertical = 4.dp)
                        .size(10.dp)
                        .background(if (it == 0f) base else lerp(base, accent, it), RoundedCornerShape(3.dp)),
                )
            }
            Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun WeekOverview(days: List<DayAggregate>, today: LocalDate, modifier: Modifier = Modifier) {
    val maxValue = remember(days) { max(1f, days.maxOfOrNull { it.checkIns + it.focusSeconds / 1500f } ?: 1f) }
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(modifier.fillMaxWidth().height(124.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            val value = day.checkIns + day.focusSeconds / 1500f
            val ratio = (value / maxValue).coerceIn(0f, 1f)
            val isToday = day.date == today
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    if (day.focusSeconds > 0) "${day.focusSeconds / 60}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .width(16.dp)
                        .height((10 + 70 * ratio).dp)
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                            RoundedCornerShape(6.dp),
                        ),
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    labels[day.date.dayOfWeek.value - 1],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun HabitProgressRow(habit: HabitSnapshot) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(habit.task.icon ?: "🌱", fontSize = 18.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
            Text(
                habit.task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                "近 30 天完成率 ${(habit.completion30d * 100).toInt()}% · 当前 ${habit.currentStreak} 天",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProgressRing(progress = habit.completion30d, size = 38.dp, stroke = 4.dp)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "确定",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val feedback = LocalFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radii.lg),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (destructive) feedback?.reject() else feedback?.confirm()
                onConfirm()
            }) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                feedback?.tap()
                onDismiss()
            }) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}

val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日")
val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 5..10 -> "早上好"
    in 11..13 -> "中午好"
    in 14..17 -> "下午好"
    in 18..22 -> "晚上好"
    else -> "夜深了"
}
