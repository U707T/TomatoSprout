package com.u707t.tomatosprout.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.domain.CheckInLedger
import com.u707t.tomatosprout.domain.DayAggregate
import com.u707t.tomatosprout.domain.HabitSchedule
import com.u707t.tomatosprout.domain.HeatmapMetric
import com.u707t.tomatosprout.domain.StatsData
import com.u707t.tomatosprout.ui.components.ConfirmDialog
import com.u707t.tomatosprout.ui.components.EmptyState
import com.u707t.tomatosprout.ui.components.GroupDivider
import com.u707t.tomatosprout.ui.components.HabitProgressRow
import com.u707t.tomatosprout.ui.components.LargeHeader
import com.u707t.tomatosprout.ui.components.Metric
import com.u707t.tomatosprout.ui.components.MonthHeatmap
import com.u707t.tomatosprout.ui.components.ProgressRing
import com.u707t.tomatosprout.ui.components.SectionCaption
import com.u707t.tomatosprout.ui.components.SegmentedTabs
import com.u707t.tomatosprout.ui.components.SettingRow
import com.u707t.tomatosprout.ui.components.SoftCard
import com.u707t.tomatosprout.ui.components.WeekOverview
import com.u707t.tomatosprout.ui.components.YearHeatmap
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Space
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StatsScreen(
    onAddHabit: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenHistory: (date: LocalDate?, taskId: String?) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LargeHeader(
                title = "统计",
                eyebrow = "只保存在本机",
                trailing = {
                    IconButton(onClick = { onOpenHistory(null, viewModel.initialTaskFilter) }) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "历史时间轴",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val s = state) {
            StatsUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center,
            ) { CircularProgressIndicator() }

            is StatsUiState.Empty -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center,
            ) {
                EmptyState(
                    emoji = "📊",
                    title = "还没有可以回顾的记录",
                    message = "先创建习惯或完成一次专注，统计会自动汇成趋势、分布与时间轴。",
                    actionLabel = "创建习惯",
                    onAction = onAddHabit,
                )
            }

            is StatsUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center,
            ) {
                Text(s.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }

            is StatsUiState.Content -> StatsContent(
                data = s.data,
                dashboard = s.dashboard,
                padding = padding,
                viewModel = viewModel,
                onOpenBadges = onOpenBadges,
                onOpenHistory = onOpenHistory,
            )
        }
    }
}

@Composable
private fun StatsContent(
    data: StatsData,
    dashboard: StatsDashboard,
    padding: PaddingValues,
    viewModel: StatsViewModel,
    onOpenBadges: () -> Unit,
    onOpenHistory: (date: LocalDate?, taskId: String?) -> Unit,
) {
    var month by remember(dashboard.selectedDate) { mutableStateOf(YearMonth.from(dashboard.selectedDate)) }
    var year by remember(dashboard.selectedDate) { mutableIntStateOf(dashboard.selectedDate.year) }
    var yearView by remember { mutableStateOf(false) }
    var heatmapMetric by remember { mutableStateOf(HeatmapMetric.CHECK_INS) }
    val checkLedger = remember(data.checks) { CheckInLedger(data.checks) }

    val week = (6 downTo 0).map { offset ->
        val date = data.today.minusDays(offset.toLong())
        data.days[date] ?: DayAggregate(date)
    }

    fun aggregateRate(days: Int): Float {
        var scheduled = 0
        var met = 0
        data.habits.forEach { habit ->
            val dates = HabitSchedule.datesBetween(habit.rule, data.today.minusDays(days.toLong() - 1), data.today)
            scheduled += dates.size
            met += dates.count { checkLedger.isMet(habit.rule, it) }
        }
        return if (scheduled == 0) 0f else met.toFloat() / scheduled
    }

    val rate30 = aggregateRate(30)

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.xs, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item(key = "hero") {
            HeroCard(
                data = data,
                dashboard = dashboard,
                rate30 = rate30,
                onClick = { onOpenHistory(dashboard.selectedDate, viewModel.initialTaskFilter) },
            )
        }
        item(key = "day") {
            SoftCard {
                DayOverviewCard(
                    dashboard = dashboard,
                    onPrevious = { viewModel.shiftDate(-1) },
                    onNext = { viewModel.shiftDate(1) },
                )
            }
        }
        item(key = "week") {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本周节奏", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "共 ${week.sumOf { it.focusSeconds } / 60} 分钟",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Space.sm))
                WeekOverview(days = week, today = data.today)
            }
        }
        item(key = "distribution") {
            SoftCard {
                DistributionCard(
                    dashboard = dashboard,
                    onRange = viewModel::setRange,
                    onViewRecords = { onOpenHistory(dashboard.selectedDate, viewModel.initialTaskFilter) },
                )
            }
        }
        item(key = "bars") {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionCaption("月度趋势", Modifier.weight(1f))
                    TextButton(onClick = viewModel::previousMonth) { Text("‹") }
                    Text(
                        "${dashboard.month.year}.${dashboard.month.monthValue}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(onClick = viewModel::nextMonth) { Text("›") }
                }
                MonthlyBarChart(
                    dashboard = dashboard,
                    onBarClick = { day -> onOpenHistory(dashboard.month.atDay(day), viewModel.initialTaskFilter) },
                )
            }
        }
        item(key = "hourly") {
            SoftCard { HourlyFocusChart(dashboard.hours, dashboard.bestHour) }
        }
        item(key = "interrupts") {
            SoftCard { InterruptReasonsCard(dashboard.interruptReasons) }
        }
        item(key = "record") {
            SoftCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    Metric("${data.currentStreak}", "当前连续", Modifier.weight(1f), unit = "天")
                    Metric("${data.longestStreak}", "最长连续", Modifier.weight(1f), unit = "天")
                    Metric("${data.perfectDays}", "圆满日", Modifier.weight(1f), unit = "天")
                }
                Spacer(Modifier.height(Space.md))
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Spacer(Modifier.height(Space.md))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    Metric("${data.totalCheckIns}", "累计打卡", Modifier.weight(1f), unit = "次")
                    Metric("${data.totalFocusSeconds / 3600}", "累计专注", Modifier.weight(1f), unit = "小时")
                    Metric("${(rate30 * 100).toInt()}", "近 30 天", Modifier.weight(1f), unit = "%")
                }
                Text(
                    "共 ${data.checkInDays} 个打卡日 · ${data.totalPomos} 个番茄",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md),
                )
            }
        }
        item(key = "heatmap") {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("日历热力图", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    SegmentedTabs(
                        options = listOf("月", "年"),
                        selectedIndex = if (yearView) 1 else 0,
                        onSelect = { yearView = it == 1 },
                        modifier = Modifier.width(112.dp),
                    )
                }
                Spacer(Modifier.height(Space.md))
                SegmentedTabs(
                    options = listOf("打卡次数", "专注时长"),
                    selectedIndex = if (heatmapMetric == HeatmapMetric.CHECK_INS) 0 else 1,
                    onSelect = { heatmapMetric = if (it == 0) HeatmapMetric.CHECK_INS else HeatmapMetric.FOCUS_DURATION },
                )
                Spacer(Modifier.height(Space.md))
                val heatmapDays = data.days.mapValues { (_, day) ->
                    if (heatmapMetric == HeatmapMetric.CHECK_INS) {
                        DayAggregate(day.date, checkIns = day.checkIns)
                    } else {
                        DayAggregate(day.date, focusSeconds = day.focusSeconds)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        if (yearView) year -= 1 else month = month.minusMonths(1)
                    }) { Text("‹") }
                    Text(
                        if (yearView) "$year 年" else "${month.year} 年 ${month.monthValue} 月",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = {
                        if (yearView) year += 1 else month = month.plusMonths(1)
                    }) { Text("›") }
                }
                if (yearView) {
                    YearHeatmap(year = year, days = heatmapDays)
                } else {
                    MonthHeatmap(
                        month = month,
                        days = heatmapDays,
                        onDayClick = { onOpenHistory(it, viewModel.initialTaskFilter) },
                    )
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "颜色越深，代表当天的记录越多。点击某一天进入历史时间轴。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (data.habits.isNotEmpty()) {
            item(key = "habits") {
                SoftCard {
                    Text("习惯完成率", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Space.xs))
                    data.habits.forEachIndexed { index, habit ->
                        key(habit.task.id) {
                            HabitProgressRow(habit)
                            if (index != data.habits.lastIndex) GroupDivider()
                        }
                    }
                }
            }
        }
        item(key = "entries") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.md))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                SettingRow(
                    title = "历史时间轴",
                    subtitle = "月历 + 当日记录 · 可写心得、补卡",
                    onClick = { onOpenHistory(dashboard.selectedDate, viewModel.initialTaskFilter) },
                )
                GroupDivider()
                SettingRow(
                    title = "成就徽章",
                    subtitle = "已解锁 ${data.unlockedBadges} / ${data.badges.size}",
                    onClick = onOpenBadges,
                )
            }
        }
        item(key = "footer") {
            Text(
                "统计只保存在本机。删除任务不会删除历史打卡与专注记录。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.sm),
            )
        }
    }
}

@Composable
private fun HeroCard(data: StatsData, dashboard: StatsDashboard, rate30: Float, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionCaption(
                    if (dashboard.selectedDate == data.today) "今天 · ${dashboard.selectedDate}"
                    else "${dashboard.selectedDate}",
                )
                Spacer(Modifier.height(Space.xs))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${dashboard.dailyFocusSeconds / 60}",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "分钟",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.xs, bottom = 6.dp),
                    )
                }
                Text(
                    "专注 ${dashboard.dailyFocusCount} 次 · 打卡 ${dashboard.dailyCheckIns} 次",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ProgressRing(
                progress = rate30,
                size = 78.dp,
                stroke = 7.dp,
                label = "${(rate30 * 100).toInt()}%",
                subLabel = "30 天",
            )
        }
        Spacer(Modifier.height(Space.md))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.sm))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(horizontal = Space.lg, vertical = Space.md),
        ) {
            Metric("${data.currentStreak}", "连续天数", Modifier.weight(1f), unit = "天")
            Metric("${data.totalFocusSeconds / 3600}", "累计专注", Modifier.weight(1f), unit = "小时")
            Metric("${data.totalPomos}", "番茄总数", Modifier.weight(1f), unit = "个")
        }
    }
}
