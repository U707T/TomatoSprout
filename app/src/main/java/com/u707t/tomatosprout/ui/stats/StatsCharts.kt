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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "${h}小时${m}分"
        m > 0 -> "${m}分钟"
        else -> "${seconds}秒"
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
    onCustom: () -> Unit,
    onViewRecords: () -> Unit,
) {
    SoftCard {
        Text("时长分布", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DistributionRange.entries.forEach { range ->
                FilterChip(
                    selected = dashboard.range == range,
                    onClick = { if (range == DistributionRange.CUSTOM) onCustom() else onRange(range) },
                    label = { Text(range.label) },
                )
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
        if (dashboard.slices.isEmpty()) {
            Text(
                "这个区间还没有专注记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        } else {
            dashboard.slices.forEach { slice ->
                val percent = if (dashboard.totalSeconds <= 0) 0 else (slice.seconds * 100 / dashboard.totalSeconds).toInt()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Box(
                        Modifier.size(9.dp).background(
                            chartPalette[slice.colorIndex % chartPalette.size],
                            CircleShape,
                        ),
                    )
                    Text(
                        slice.label,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
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
        TextButton(onClick = onViewRecords, modifier = Modifier.fillMaxWidth()) {
            Text("查看专注记录")
        }
    }
}

@Composable
fun DonutChart(slices: List<ChartSlice>, totalSeconds: Long) {
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        val track = MaterialTheme.colorScheme.surfaceVariant
        Canvas(Modifier.matchParentSize()) {
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
                if (totalSeconds <= 0 || slice.seconds <= 0) return@forEach
                val sweep = 360f * slice.seconds.toFloat() / totalSeconds
                drawArc(
                    color = chartPalette[slice.colorIndex % chartPalette.size],
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
            Text(
                formatChartDuration(totalSeconds),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("总时长", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(dashboard.month, dashboard.bars) {
                    detectTapGestures { offset ->
                        val left = 6.dp.toPx()
                        val usable = size.width - left * 2
                        val slot = usable / dashboard.bars.size
                        val index = ((offset.x - left) / slot).toInt()
                        dashboard.bars.getOrNull(index)?.let { onBarClick(it.day) }
                    }
                },
        ) {
            val left = 6.dp.toPx()
            val top = 18.dp.toPx()
            val bottom = size.height - 22.dp.toPx()
            val usableHeight = bottom - top
            val slot = (size.width - left * 2) / dashboard.bars.size
            val barWidth = (slot * 0.58f).coerceAtLeast(2.dp.toPx())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = with(density) { 7.sp.toPx() }
                textAlign = Paint.Align.CENTER
                color = textColor.toArgb()
            }
            drawLine(gridColor, Offset(left, bottom), Offset(size.width - left, bottom), 1f)
            dashboard.bars.forEachIndexed { index, bar ->
                val ratio = bar.seconds.toFloat() / dashboard.barMaxSeconds
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
