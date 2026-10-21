package com.u707t.tomatosprout.ui.settings

import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.BuildConfig
import com.u707t.tomatosprout.domain.ThemeMode
import com.u707t.tomatosprout.ui.components.SectionHeader
import com.u707t.tomatosprout.ui.components.SoftCard
import com.u707t.tomatosprout.ui.theme.Tomato
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 90.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SoftCard {
                    Text("外观", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.setTheme(ThemeMode.SYSTEM) },
                            label = { Text("跟随系统") },
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.setTheme(ThemeMode.LIGHT) },
                            label = { Text("浅色") },
                        )
                        FilterChip(
                            selected = settings.themeMode == ThemeMode.DARK,
                            onClick = { viewModel.setTheme(ThemeMode.DARK) },
                            label = { Text("深色") },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = "打卡卡片简约模式",
                        subtitle = "只保留标题、连续天数和完成状态",
                        checked = settings.simpleMode,
                        onChange = viewModel::setSimple,
                    )
                }
            }

            item {
                SoftCard {
                    Text("专注默认值", style = MaterialTheme.typography.titleMedium)
                    NumberRow("专注时长", settings.defaultFocusMinutes, 5, 180, viewModel::setFocusMinutes)
                    NumberRow("休息时长", settings.defaultBreakMinutes, 5, 30, viewModel::setBreakMinutes)
                    SwitchRow(
                        "忽略 30 秒内的正向计时",
                        "开启时不足 30 秒直接丢弃；关闭时先询问是否记录",
                        settings.ignoreShortFocus,
                        viewModel::setIgnoreShortFocus,
                    )
                    SwitchRow("默认严格模式", "本次专注不可暂停，提前结束会彻底作废", settings.strictMode, viewModel::setStrict)
                    SwitchRow("默认正计时", "从 0 开始累计，手动结束", settings.countUpMode, viewModel::setCountUp)
                    SwitchRow("午夜模式", "跨天专注默认记在结束日；开启后强制记在开始日", settings.midnightShift, viewModel::setMidnight)
                }
            }

            item {
                SoftCard {
                    Text("提醒", style = MaterialTheme.typography.titleMedium)
                    SwitchRow(
                        "允许通知",
                        "关闭后打卡与计时不受影响，只是不再提醒",
                        settings.notificationsEnabled,
                        viewModel::setNotifications,
                    )
                    SwitchRow("免打扰时段", "该时段内不发送提醒", settings.dndEnabled, viewModel::setDnd)
                    if (settings.dndEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("从", modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                pickTime(context, settings.dndStart, viewModel::setDndStart)
                            }) { Text(settings.dndStart) }
                            Text("到")
                            TextButton(onClick = {
                                pickTime(context, settings.dndEnd, viewModel::setDndEnd)
                            }) { Text(settings.dndEnd) }
                        }
                    }
                }
            }

            item {
                SoftCard {
                    Text("数据与同步", style = MaterialTheme.typography.titleMedium)
                    SettingRow(
                        "WebDAV 同步",
                        if (settings.webDavServer.isBlank()) "未配置 · 全程不会发起网络请求"
                        else "上次同步：" + (settings.lastSyncedAt?.let { formatTime(it) } ?: "从未"),
                        onClick = onOpenWebDav,
                    )
                    SettingRow("导出 JSON 备份", "任务、打卡、专注、徽章与设置") {
                        jsonExport.launch("tomatosprout-backup-${LocalDate.now()}.json")
                    }
                    SettingRow("导出 CSV", "打卡记录，可用 Excel 查看") {
                        csvExport.launch("tomatosprout-checkins-${LocalDate.now()}.csv")
                    }
                    SettingRow("导入备份", "导入前会做字段校验，冲突保留本地版本") {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                    SettingRow(
                        "清除所有数据",
                        "二次确认；打卡与专注以软删除保留，不再显示",
                        dangerous = true,
                    ) { confirmClear = true }
                }
            }

            item {
                SoftCard {
                    Text("更多", style = MaterialTheme.typography.titleMedium)
                    SettingRow("成就徽章", "把坚持过的事点亮") { onOpenBadges() }
                    SettingRow("历史时间轴", "月历 + 当日记录，可写心得与补卡") { onOpenHistory() }
                    SettingRow("愿望清单", "只属于自己的小愿望") { onOpenWishlist() }
                    SettingRow("关于番茄芽", "版本 ${BuildConfig.VERSION_NAME} · 隐私说明") { onOpenAbout() }
                }
            }

            item {
                Text(
                    "番茄芽不收集任何用户数据，没有广告、没有账号体系。WebDAV 数据只存在你自己的服务器上。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("确定清除所有数据？") },
            text = { Text("习惯、打卡、专注记录和愿望都会从界面消失。此操作不可撤销；如需保留，请先导出 JSON 备份。") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clearAll() }) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { onChange((value - 5).coerceAtLeast(min)) }) { Text("−") }
        Text("$value 分", fontWeight = FontWeight.Medium)
        TextButton(onClick = { onChange((value + 5).coerceAtMost(max)) }) { Text("+") }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    dangerous: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (dangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", fontSize = 22.sp, color = if (dangerous) MaterialTheme.colorScheme.error else Tomato)
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
