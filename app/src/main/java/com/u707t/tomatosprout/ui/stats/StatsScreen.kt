package com.u707t.tomatosprout.ui.stats

import android.app.DatePickerDialog
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.domain.DayAggregate
import com.u707t.tomatosprout.domain.HabitSchedule
import com.u707t.tomatosprout.domain.HeatmapMetric
import com.u707t.tomatosprout.domain.StatsData
import com.u707t.tomatosprout.domain.StreakCalculator
import com.u707t.tomatosprout.domain.TimelineItem
import com.u707t.tomatosprout.ui.components.EmptyState
import com.u707t.tomatosprout.ui.components.HabitProgressRow
import com.u707t.tomatosprout.ui.components.MonthHeatmap
import com.u707t.tomatosprout.ui.components.SectionHeader
import com.u707t.tomatosprout.ui.components.SoftCard
import com.u707t.tomatosprout.ui.components.StatTile
import com.u707t.tomatosprout.ui.components.WeekOverview
import com.u707t.tomatosprout.ui.components.YearHeatmap
import com.u707t.tomatosprout.ui.components.dateFmt
import com.u707t.tomatosprout.ui.theme.Tomato
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onAddHabit: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenCalendar: (LocalDate, String?) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回顾") },
                actions = {
                    TextButton(onClick = { onOpenCalendar(LocalDate.now(), null) }) { Text("日历") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val s = state) {
            StatsUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            is StatsUiState.Empty -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                EmptyState(
                    emoji = "📊",
                    title = "还没有可以回顾的记录",
                    message = "先创建习惯，打卡和专注会自动汇成热力图与时间轴。",
                    actionLabel = "创建习惯",
                    onAction = onAddHabit,
                )
            }
            is StatsUiState.Error -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is StatsUiState.Content -> StatsContent(
                data = s.data,
                dashboard = s.dashboard,
                padding = padding,
                viewModel = viewModel,
                onOpenBadges = onOpenBadges,
                onOpenCalendar = onOpenCalendar,
                onMakeup = viewModel::makeup,
            )
        }
    }
}

@Composable
private fun StatsContent(
    data: StatsData,
    dashboard: StatsDashboard,
    padding: androidx.compose.foundation.layout.PaddingValues,
    viewModel: StatsViewModel,
    onOpenBadges: () -> Unit,
    onOpenCalendar: (LocalDate, String?) -> Unit,
    onMakeup: (String, LocalDate) -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var yearView by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var filterTask by remember { mutableStateOf(viewModel.initialTaskFilter) }
    var rangeDays by remember { mutableIntStateOf(30) }
    var taskMenu by remember { mutableStateOf(false) }
    var heatmapMetric by remember { mutableStateOf(HeatmapMetric.CHECK_INS) }
    var customStartPicker by remember { mutableStateOf(false) }
    var customEndPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
            met += dates.count { StreakCalculator.isMet(habit.rule, data.checks, it) }
        }
        return if (scheduled == 0) 0f else met.toFloat() / scheduled
    }
    val filterOptions = data.timeline.mapNotNull { item -> item.taskId?.let { it to item.title } }
        .distinctBy { it.first }
    val filteredTimeline = data.timeline.filter { item ->
        (filterTask == null || item.taskId == filterTask) &&
            (rangeDays == 0 || !item.date.isBefore(data.today.minusDays(rangeDays.toLong() - 1)))
    }.take(120)

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 90.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { CumulativeOverviewCard(data, dashboard) }
        item {
            DayOverviewCard(
                dashboard = dashboard,
                onPrevious = { viewModel.shiftDate(-1) },
                onNext = { viewModel.shiftDate(1) },
            )
        }
        item {
            DistributionCard(
                dashboard = dashboard,
                onRange = viewModel::setRange,
                onCustom = { customStartPicker = true },
                onViewRecords = { onOpenCalendar(dashboard.selectedDate, filterTask) },
            )
        }
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = viewModel::previousMonth) { Text("‹") }
                    Text(
                        "${dashboard.month.year} 年 ${dashboard.month.monthValue} 月",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    TextButton(onClick = viewModel::nextMonth) { Text("›") }
                }
                MonthlyBarChart(dashboard = dashboard, onBarClick = viewModel::selectBar)
            }
        }
        item {
            SoftCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("当前连续", "${data.currentStreak}", Modifier.weight(1f), "天")
                    StatTile("最长连续", "${data.longestStreak}", Modifier.weight(1f), "天")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("累计打卡", "${data.totalCheckIns}", Modifier.weight(1f), "次")
                    StatTile("累计专注", "${data.totalFocusSeconds / 3600}", Modifier.weight(1f), "小时")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "共 ${data.checkInDays} 个打卡日 · ${data.totalPomos} 个番茄 · ${data.perfectDays} 个圆满日",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("本周完成率", "${(aggregateRate(7) * 100).toInt()}", Modifier.weight(1f), "%")
                    StatTile("本月完成率", "${(aggregateRate(30) * 100).toInt()}", Modifier.weight(1f), "%")
                }
            }
        }

        item {
            SoftCard {
                Text("本周纵览", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                WeekOverview(days = week, today = data.today)
            }
        }

        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("日历热力图", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = !yearView,
                        onClick = { yearView = false },
                        label = { Text("月") },
                    )
                    Spacer(Modifier.size(4.dp))
                    FilterChip(
                        selected = yearView,
                        onClick = { yearView = true },
                        label = { Text("年") },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = heatmapMetric == HeatmapMetric.CHECK_INS,
                        onClick = { heatmapMetric = HeatmapMetric.CHECK_INS },
                        label = { Text("打卡次数") },
                    )
                    FilterChip(
                        selected = heatmapMetric == HeatmapMetric.FOCUS_DURATION,
                        onClick = { heatmapMetric = HeatmapMetric.FOCUS_DURATION },
                        label = { Text("专注时长") },
                    )
                }
                val heatmapDays = data.days.mapValues { (_, day) ->
                    if (heatmapMetric == HeatmapMetric.CHECK_INS) {
                        DayAggregate(day.date, checkIns = day.checkIns)
                    } else {
                        DayAggregate(day.date, focusSeconds = day.focusSeconds)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (yearView) year -= 1 else month = month.minusMonths(1)
                    }) { Icon(Icons.Default.ArrowBack, contentDescription = "上一页") }
                    Text(
                        if (yearView) "$year 年" else "${month.year} 年 ${month.monthValue} 月",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    TextButton(onClick = {
                        if (yearView) year += 1 else month = month.plusMonths(1)
                    }) { Text("下一页") }
                }
                if (yearView) {
                    YearHeatmap(year = year, days = heatmapDays)
                } else {
                    MonthHeatmap(
                        month = month,
                        days = heatmapDays,
                        onDayClick = { selectedDate = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (heatmapMetric == HeatmapMetric.CHECK_INS) {
                            "颜色越深，代表当天打卡次数越多。点击某一天可查看详情或补卡。"
                        } else {
                            "颜色越深，代表当天专注时长越长。点击某一天可查看详情或补卡。"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (data.habits.isNotEmpty()) {
            item {
                SoftCard {
                    Text("习惯完成率", style = MaterialTheme.typography.titleMedium)
                    data.habits.forEach { HabitProgressRow(it) }
                }
            }
        }

        item {
            SoftCard(onClick = onOpenBadges) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏅", fontSize = 28.sp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("成就徽章", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "已解锁 ${data.unlockedBadges} / ${data.badges.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("查看 ›", color = Tomato, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("历史记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { taskMenu = true }) {
                        Text(filterOptions.firstOrNull { it.first == filterTask }?.second ?: "全部任务")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = taskMenu, onDismissRequest = { taskMenu = false }) {
                        DropdownMenuItem(text = { Text("全部任务") }, onClick = { filterTask = null; taskMenu = false })
                        filterOptions.forEach { (id, title) ->
                            DropdownMenuItem(
                                text = { Text(title) },
                                onClick = { filterTask = id; taskMenu = false },
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "近 7 天", 30 to "近 30 天", 0 to "全部").forEach { (days, label) ->
                    FilterChip(
                        selected = rangeDays == days,
                        onClick = { rangeDays = days },
                        label = { Text(label) },
                    )
                }
            }
        }

        if (filteredTimeline.isEmpty()) {
            item {
                SoftCard {
                    Text(
                        "这个筛选范围内还没有记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(filteredTimeline, key = { it.id }) { item ->
                TimelineRow(item)
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(customStartPicker) {
        if (customStartPicker) {
            customStartPicker = false
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    val start = LocalDate.of(y, m + 1, d)
                    viewModel.setCustomRange(start, dashboard.customEnd)
                    customEndPicker = true
                },
                dashboard.customStart.year, dashboard.customStart.monthValue - 1, dashboard.customStart.dayOfMonth,
            ).show()
        }
    }
    androidx.compose.runtime.LaunchedEffect(customEndPicker) {
        if (customEndPicker) {
            customEndPicker = false
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    viewModel.setCustomRange(dashboard.customStart, LocalDate.of(y, m + 1, d))
                },
                dashboard.customEnd.year, dashboard.customEnd.monthValue - 1, dashboard.customEnd.dayOfMonth,
            ).show()
        }
    }

    selectedDate?.let { date ->
        DayDetailDialog(
            date = date,
            data = data,
            onDismiss = { selectedDate = null },
            onMakeup = { taskId -> onMakeup(taskId, date) },
        )
    }
}

@Composable
private fun TimelineRow(item: TimelineItem) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (item.kind == "focus") "🍅" else "✓", fontSize = 20.sp, color = if (item.kind == "focus") Tomato else MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "${item.date.format(dateFmt)} ${item.timeLabel} · ${item.detail}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayDetailDialog(
    date: LocalDate,
    data: StatsData,
    onDismiss: () -> Unit,
    onMakeup: (String) -> Unit,
) {
    val scheduled = data.habits.filter { HabitSchedule.isScheduled(it.rule, date) }
    val aggregate = data.days[date] ?: DayAggregate(date)
    val canMakeup = date.isBefore(data.today) && !date.isBefore(data.today.minusDays(7))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 EEEE", Locale.CHINA))) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "打卡 ${aggregate.checkIns} 次 · 专注 ${aggregate.focusSeconds / 60} 分钟 · 番茄 ${aggregate.pomos} 个",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                if (scheduled.isEmpty()) {
                    Text("当天没有排期的习惯。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                scheduled.forEach { habit ->
                    val record = data.checks.firstOrNull { it.taskId == habit.task.id && it.date == date && it.deletedAt == null }
                    val done = record != null
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (done) "✅" else "○",
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            habit.task.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!done && canMakeup) {
                            TextButton(onClick = { onMakeup(habit.task.id); onDismiss() }) { Text("补卡") }
                        } else if (done) {
                            Text("已完成", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    record?.note?.takeIf { it.isNotBlank() }?.let { note ->
                        Text(
                            "　" + (record.mood?.let { moodEmoji(it) + " " } ?: "") + note,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!canMakeup && date.isBefore(data.today)) {
                    Text(
                        "补卡仅支持最近 7 天。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun moodEmoji(mood: Int): String = when (mood) {
    1 -> "😌"
    2 -> "🙂"
    3 -> "😐"
    4 -> "😮‍💨"
    5 -> "😣"
    else -> ""
}
