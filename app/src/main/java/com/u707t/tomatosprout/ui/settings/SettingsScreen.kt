package com.u707t.tomatosprout.ui.settings

import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.BuildConfig
import com.u707t.tomatosprout.domain.ThemeMode
import com.u707t.tomatosprout.notify.LiveActivityStatus
import com.u707t.tomatosprout.ui.components.ConfirmDialog
import com.u707t.tomatosprout.ui.components.GroupDivider
import com.u707t.tomatosprout.ui.components.LargeHeader
import com.u707t.tomatosprout.ui.components.SegmentedTabs
import com.u707t.tomatosprout.ui.components.SettingGroup
import com.u707t.tomatosprout.ui.components.SettingRow
import com.u707t.tomatosprout.ui.feedback.LocalFeedback
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    onOpenWebDav: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenWishlist: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val jsonExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::exportJson)
    }
    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        it?.let(viewModel::exportCsv)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::importJson)
    }

    Scaffold(
        topBar = { LargeHeader(title = "我的", eyebrow = "番茄芽 · v${BuildConfig.VERSION_NAME}") },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Space.lg,
                end = Space.lg,
                top = Space.xs,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item(key = "profile") { ProfileCard() }

            item(key = "appearance") {
                SettingGroup(title = "外观") {
                    Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
                        Text("主题", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "深色模式使用暖炭灰，避免纯黑与白字的硬边",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Space.sm))
                        val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                        SegmentedTabs(
                            options = listOf("跟随系统", "浅色", "深色"),
                            selectedIndex = modes.indexOf(settings.themeMode).coerceAtLeast(0),
                            onSelect = { viewModel.setTheme(modes[it]) },
                        )
                    }
                    GroupDivider()
                    SwitchRow(
                        title = "打卡卡片简约模式",
                        subtitle = "只保留标题、连续天数和完成状态",
                        checked = settings.simpleMode,
                        onChange = viewModel::setSimple,
                    )
                }
            }

            item(key = "feedback") {
                SettingGroup(title = "反馈") {
                    SwitchRow(
                        title = "触感反馈",
                        subtitle = "打卡、完成、删除使用不同强度的振动",
                        checked = hapticsEnabled,
                        onChange = viewModel::setHaptics,
                    )
                    GroupDivider()
                    SwitchRow(
                        title = "操作音效",
                        subtitle = "点击、完成与错误提示音；关闭后完全静音",
                        checked = soundEnabled,
                        onChange = viewModel::setSound,
                    )
                }
            }

            item(key = "focus") {
                SettingGroup(title = "专注") {
                    NumberRow("专注时长", settings.defaultFocusMinutes, 5, 180, viewModel::setFocusMinutes)
                    GroupDivider()
                    NumberRow("休息时长", settings.defaultBreakMinutes, 5, 30, viewModel::setBreakMinutes)
                    GroupDivider()
                    NumberRow("长休息", settings.longBreakMinutes, 10, 60, viewModel::setLongBreakMinutes)
                    GroupDivider()
                    SwitchRow(
                        "忽略 30 秒内的专注",
                        "正计时或倒计时不足 30 秒时直接丢弃，不留中断记录",
                        settings.ignoreShortFocus,
                        viewModel::setIgnoreShortFocus,
                    )
                    GroupDivider()
                    SwitchRow("默认严格模式", "不可暂停，提前结束会彻底作废", settings.strictMode, viewModel::setStrict)
                    GroupDivider()
                    SwitchRow("默认正计时", "从 0 开始累计，手动结束", settings.countUpMode, viewModel::setCountUp)
                    GroupDivider()
                    SwitchRow("午夜模式", "跨天专注强制记在开始日", settings.midnightShift, viewModel::setMidnight)
                }
            }

            item(key = "reminder") {
                SettingGroup(title = "提醒") {
                    SwitchRow(
                        "允许通知",
                        "关闭后打卡与计时不受影响，只是不再提醒",
                        settings.notificationsEnabled,
                        viewModel::setNotifications,
                    )
                    GroupDivider()
                    // 从系统设置页回来时要重新读一次授权状态，所以挂在 resume 上
                    var liveActivity by remember {
                        mutableStateOf(LiveActivityStatus.describe(context, settings.liveActivityEnabled))
                    }
                    LifecycleResumeEffect(settings.liveActivityEnabled) {
                        liveActivity = LiveActivityStatus.describe(context, settings.liveActivityEnabled)
                        onPauseOrDispose { }
                    }
                    SwitchRow(
                        "实时活动 / 超级岛",
                        "专注时在状态栏、锁屏与澎湃 OS 超级岛上显示倒计时",
                        settings.liveActivityEnabled,
                        viewModel::setLiveActivity,
                    )
                    GroupDivider()
                    SettingRow(
                        title = "系统实时活动",
                        subtitle = liveActivity.summary,
                        onClick = liveActivity.settingsIntent?.let { intent ->
                            { runCatching { context.startActivity(intent) } }
                        },
                    )
                    GroupDivider()
                    SwitchRow("免打扰时段", "该时段内不发送提醒", settings.dndEnabled, viewModel::setDnd)
                    if (settings.dndEnabled) {
                        GroupDivider()
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("从", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                pickTime(context, settings.dndStart, viewModel::setDndStart)
                            }) { Text(settings.dndStart) }
                            Text("到", style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = {
                                pickTime(context, settings.dndEnd, viewModel::setDndEnd)
                            }) { Text(settings.dndEnd) }
                        }
                    }
                }
            }

            item(key = "data") {
                SettingGroup(title = "数据") {
                    SettingRow(
                        title = "WebDAV 同步",
                        subtitle = if (settings.webDavServer.isBlank()) "未配置 · 全程不会发起网络请求"
                        else "上次同步：" + (settings.lastSyncedAt?.let { formatTime(it) } ?: "从未"),
                        onClick = onOpenWebDav,
                    )
                    GroupDivider()
                    SettingRow(
                        title = "导出 JSON 备份",
                        subtitle = "任务、打卡、专注、徽章与设置",
                        onClick = { jsonExport.launch("tomatosprout-backup-${LocalDate.now()}.json") },
                    )
                    GroupDivider()
                    SettingRow(
                        title = "导出 CSV",
                        subtitle = "打卡记录，可用 Excel 查看",
                        onClick = { csvExport.launch("tomatosprout-checkins-${LocalDate.now()}.csv") },
                    )
                    GroupDivider()
                    SettingRow(
                        title = "导入备份",
                        subtitle = "字段校验后再写入，冲突保留本地版本",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                    GroupDivider()
                    SettingRow(
                        title = "清除所有数据",
                        subtitle = "不可撤销，建议先导出备份",
                        danger = true,
                        onClick = { confirmClear = true },
                    )
                }
            }

            item(key = "more") {
                SettingGroup(title = "更多") {
                    SettingRow(title = "成就徽章", subtitle = "把坚持过的事点亮", onClick = onOpenBadges)
                    GroupDivider()
                    SettingRow(title = "历史时间轴", subtitle = "月历 + 当日记录，可写心得与补卡", onClick = onOpenHistory)
                    GroupDivider()
                    SettingRow(title = "愿望清单", subtitle = "只属于自己的小愿望", onClick = onOpenWishlist)
                    GroupDivider()
                    SettingRow(
                        title = "关于番茄芽",
                        subtitle = "版本 ${BuildConfig.VERSION_NAME} · 隐私说明",
                        onClick = onOpenAbout,
                    )
                }
            }

            item(key = "privacy") {
                Text(
                    "番茄芽不收集任何用户数据，没有广告、没有账号体系。WebDAV 数据只存在你自己的服务器上。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.sm),
                )
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "确定清除所有数据？",
            message = "习惯、打卡、专注记录和愿望都会从界面消失。此操作不可撤销；如需保留，请先导出 JSON 备份。",
            confirmLabel = "清除",
            destructive = true,
            onConfirm = {
                confirmClear = false
                viewModel.clearAll()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun ProfileCard() {
    Surface(
        shape = RoundedCornerShape(Radii.md),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Radii.sm)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🍅", fontSize = 26.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                Text("番茄芽", style = MaterialTheme.typography.titleLarge)
                Text(
                    "不索权 · 不打扰 · 不空洞",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val feedback = LocalFeedback.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Space.md)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                feedback?.tap()
                onChange(it)
            },
        )
    }
}

@Composable
private fun NumberRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val feedback = LocalFeedback.current
    Row(
        Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.sm, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                "$value 分钟",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = {
                feedback?.tap()
                onChange((value - 5).coerceAtLeast(min))
            },
            enabled = value > min,
        ) { Text("−", fontSize = 20.sp) }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(36.dp),
        )
        TextButton(
            onClick = {
                feedback?.tap()
                onChange((value + 5).coerceAtMost(max))
            },
            enabled = value < max,
        ) { Text("+", fontSize = 20.sp) }
    }
}

private fun pickTime(context: android.content.Context, initial: String, onPick: (String) -> Unit) {
    val parts = initial.split(":")
    TimePickerDialog(
        context,
        { _, hour, minute -> onPick("%02d:%02d".format(hour, minute)) },
        parts.getOrNull(0)?.toIntOrNull() ?: 22,
        parts.getOrNull(1)?.toIntOrNull() ?: 0,
        true,
    ).show()
}

private fun formatTime(epoch: Long): String =
    Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M 月 d 日 HH:mm"))
