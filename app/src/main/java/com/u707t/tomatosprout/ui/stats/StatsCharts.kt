package com.u707t.tomatosprout.ui.stats

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.u707t.tomatosprout.domain.StatsData
import com.u707t.tomatosprout.ui.components.SoftCard
import com.u707t.tomatosprout.ui.components.StatTile
import com.u707t.tomatosprout.ui.theme.Tomato
import java.time.format.DateTimeFormatter
import java.util.Locale

val chartPalette = listOf(
    Tomato,
    Color(0xFFF3B58A),
    Color(0xFF8FAE83),
    Color(0xFF8C9ED6),
    Color(0xFFC59ACF),
    Color(0xFF7FB5B5),
    Color(0xFFB0A8A0),
)

fun formatChartDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    return when {
        h > 0 -> "${h}小时${m}分"
        m > 0 -> "${m}分钟"
        else -> "${safe}秒"
    }
}

@Composable
fun CumulativeOverviewCard(data: StatsData, dashboard: StatsDashboard) {
    SoftCard {
        Text("累计专注", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("次数", "${data.sessions.size}", Modifier.weight(1f), "次")
            StatTile("总时长", formatChartDuration(data.totalFocusSeconds), Modifier.weight(1f))
            StatTile("日均", formatChartDuration(dashboard.avgDailySeconds), Modifier.weight(1f))
        }
    }
}

@Composable
fun DayOverviewCard(
    dashboard: StatsDashboard,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("当日", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onPrevious) { Text("‹") }
            Text(
                dashboard.selectedDate.format(DateTimeFormatter.ofPattern("M 月 d 日 EEE", Locale.CHINA)),
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = onNext) { Text("›") }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("专注次数", "${dashboard.dailyFocusCount}", Modifier.weight(1f), "次")
            StatTile("专注时长", formatChartDuration(dashboard.dailyFocusSeconds), Modifier.weight(1f))
            StatTile("打卡", "${dashboard.dailyCheckIns}", Modifier.weight(1f), "次")
        }
    }
}

@Composable
fun DistributionCard(
    dashboard: StatsDashboard,
    onRange: (DistributionRange) -> Unit,
    onViewRecords: () -> Unit,
) {
    SoftCard {
        Text("时长分布", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(DistributionRange.DAY, DistributionRange.WEEK, DistributionRange.MONTH).forEach { range ->
                key(range) {
                    FilterChip(
                        selected = dashboard.range == range,
                        onClick = { onRange(range) },
                        label = { Text(range.label) },
                    )
                }
            }
        }
        if (dashboard.range == DistributionRange.CUSTOM) {
            Text(
                "${dashboard.customStart} – ${dashboard.customEnd}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(dashboard.slices, dashboard.totalSeconds)
        }
        Spacer(Modifier.height(8.dp))
        if (dashboard.slices.isEmpty() || dashboard.totalSeconds <= 0) {
            Text(
                "暂无专注数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        } else {
            dashboard.slices.forEach { slice ->
                key("${slice.colorIndex}-${slice.label}") {
                    val percent = if (dashboard.totalSeconds <= 0) {
                        0
                    } else {
                        (slice.seconds * 100 / dashboard.totalSeconds).toInt()
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp),
                    ) {
                        Box(
                            Modifier.size(9.dp).background(
                                chartPalette[slice.colorIndex.mod(chartPalette.size)],
                                CircleShape,
                            ),
                        )
                        Text(
                            slice.label,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (slice.taskDeleted) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                        )
                        Text(
                            "$percent% · ${formatChartDuration(slice.seconds)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        TextButton(onClick = onViewRecords, modifier = Modifier.fillMaxWidth()) {
            Text("查看专注记录")
        }
    }
}

@Composable
fun DonutChart(slices: List<ChartSlice>, totalSeconds: Long) {
    val hasData = slices.isNotEmpty() && totalSeconds > 0
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        val track = MaterialTheme.colorScheme.surfaceVariant
        Canvas(Modifier.matchParentSize()) {
            // 空数据直接返回，绝不执行除 0 / 非法弧度运算
            if (!hasData) return@Canvas
            val stroke = 26.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = track,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Butt),
            )
            var angle = -90f
            slices.forEach { slice ->
                if (slice.seconds <= 0) return@forEach
                val sweep = 360f * slice.seconds.toFloat() / totalSeconds.toFloat()
                drawArc(
                    color = chartPalette[slice.colorIndex.mod(chartPalette.size)],
                    startAngle = angle + 1.5f,
                    sweepAngle = (sweep - 3f).coerceAtLeast(1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                angle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (hasData) {
                Text(
                    formatChartDuration(totalSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "总时长",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "暂无\n专注数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun MonthlyBarChart(dashboard: StatsDashboard, onBarClick: (Int) -> Unit) {
    val density = LocalDensity.current
    val barColor = Tomato
    val selectedColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${dashboard.month.year} 年 ${dashboard.month.monthValue} 月 · 每日专注",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text("点击柱子查看当天", style = MaterialTheme.typography.labelSmall, color = textColor)
        }
        Spacer(Modifier.height(6.dp))
        if (dashboard.bars.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(190.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无专注数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        val maxVal = dashboard.bars.maxOfOrNull { it.seconds }?.coerceAtLeast(1L) ?: 1L
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(dashboard.month, dashboard.bars) {
                    detectTapGestures { offset ->
                        val bars = dashboard.bars
                        if (bars.isEmpty()) return@detectTapGestures
                        val left = 6.dp.toPx()
                        val usable = size.width - left * 2
                        if (usable <= 0f) return@detectTapGestures
                        val slot = usable / bars.size
                        val index = ((offset.x - left) / slot).toInt()
                        bars.getOrNull(index)?.let { onBarClick(it.day) }
                    }
                },
        ) {
            val bars = dashboard.bars
            // Canvas 内再判空，防止重组间隙传入空列表
            if (bars.isEmpty()) return@Canvas
            val left = 6.dp.toPx()
            val top = 18.dp.toPx()
            val bottom = size.height - 22.dp.toPx()
            val usableHeight = (bottom - top).coerceAtLeast(1f)
            val slot = (size.width - left * 2) / bars.size
            if (slot <= 0f) return@Canvas
            val safeMax = maxVal.coerceAtLeast(1L)
            val barWidth = (slot * 0.58f).coerceAtLeast(2.dp.toPx())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = with(density) { 7.sp.toPx() }
                textAlign = Paint.Align.CENTER
                color = textColor.toArgb()
            }
            drawLine(gridColor, Offset(left, bottom), Offset(size.width - left, bottom), 1f)
            bars.forEachIndexed { index, bar ->
                val ratio = (bar.seconds.coerceAtLeast(0L).toFloat() / safeMax.toFloat()).coerceIn(0f, 1f)
                val h = (usableHeight * ratio).coerceAtLeast(if (bar.seconds > 0) 3.dp.toPx() else 0f)
                val x = left + slot * index + (slot - barWidth) / 2
                drawRoundRect(
                    color = if (bar.selected) selectedColor else barColor.copy(alpha = 0.55f),
                    topLeft = Offset(x, bottom - h),
                    size = Size(barWidth, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2),
                )
                if (bar.seconds > 0 && (bar.selected || index % 5 == 0)) {
                    drawContext.canvas.nativeCanvas.drawText(
                        (bar.seconds / 60).toString(),
                        x + barWidth / 2,
                        bottom - h - 4.dp.toPx(),
                        paint,
                    )
                }
                drawContext.canvas.nativeCanvas.drawText(
                    (index + 1).toString(),
                    x + barWidth / 2,
                    size.height - 4.dp.toPx(),
                    paint,
                )
            }
        }
    }
}

/**
 * 高效时段：24 小时专注分布，对齐番茄ToDo官网的「高效时间区间」。
 * 纯 Compose 柱形，不依赖 Canvas，空数据直接走空态。
 */
@Composable
fun HourlyFocusChart(hours: List<HourBucket>, bestHour: Int?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "高效时段",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (bestHour != null) {
                Text(
                    String.format(java.util.Locale.US, "%02d:00–%02d:59 最专注", bestHour, bestHour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (hours.isEmpty() || hours.all { it.seconds <= 0 }) {
            Box(
                Modifier.fillMaxWidth().height(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无专注数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        val maxVal = hours.maxOfOrNull { it.seconds }?.coerceAtLeast(1L) ?: 1L
        Row(
            Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hours.forEach { bucket ->
                key(bucket.hour) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        val ratio = (bucket.seconds.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(
                                    if (bucket.seconds > 0) ratio.coerceAtLeast(0.05f) else 0f,
                                )
                                .background(
                                    if (bucket.hour == bestHour) {
                                        Tomato
                                    } else {
                                        Tomato.copy(alpha = 0.34f)
                                    },
                                    androidx.compose.foundation.shape.RoundedCornerShape(
                                        topStart = 3.dp,
                                        topEnd = 3.dp,
                                    ),
                                ),
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("0", "6", "12", "18", "23").forEach { label ->
                Text(
                    "${label}时",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 打断原因分布：对齐番茄ToDo 的「打断原因」统计。
 * 只统计选中区间 + 当前任务筛选内 outcome=interrupted 的专注。
 */
@Composable
fun InterruptReasonsCard(reasons: List<InterruptReasonCount>) {
    Column {
        Text(
            "打断原因",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        if (reasons.isEmpty()) {
            Text(
                "暂无中断记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val max = reasons.maxOf { it.count }.coerceAtLeast(1)
        reasons.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.label,
                    modifier = Modifier.width(76.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(row.count.toFloat() / max)
                            .fillMaxHeight()
                            .background(Tomato.copy(alpha = 0.72f), RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    "${row.count} 次",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
