package com.u707t.tomatosprout.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.backup.BackupManager
import com.u707t.tomatosprout.data.reset.DataResetManager
import com.u707t.tomatosprout.domain.AppSettings
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.domain.ThemeMode
import com.u707t.tomatosprout.notify.FocusLiveController
import com.u707t.tomatosprout.notify.LiveActivityDiagnostics
import com.u707t.tomatosprout.notify.ReminderScheduler
import com.u707t.tomatosprout.ui.feedback.FeedbackController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val backup: BackupManager,
    private val reset: DataResetManager,
    private val scheduler: ReminderScheduler,
    private val feedback: FeedbackController,
    private val live: FocusLiveController,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val hapticsEnabled = feedback.hapticsEnabled
    val soundEnabled = feedback.soundEnabled

    fun setHaptics(enabled: Boolean) = feedback.setHapticsEnabled(enabled)
    fun setSound(enabled: Boolean) = feedback.setSoundEnabled(enabled)

    fun setTheme(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setSimple(enabled: Boolean) = update { it.copy(simpleMode = enabled) }
    fun setNotifications(enabled: Boolean) = update { it.copy(notificationsEnabled = enabled) }

    fun diagnosticsReport(): String =
        LiveActivityDiagnostics.report(context, settings.value.liveActivityEnabled)

    fun liveActivitySettingsIntent(): android.content.Intent? =
        LiveActivityDiagnostics.settingsIntent(context)

    /** 发一条 60 秒的假专注，用来当场验证系统是否会把它放进岛。 */
    fun sendTestIsland() {
        if (!com.u707t.tomatosprout.notify.NotificationPermission.canPost(context)) {
            messages.tryEmit("系统还没有允许通知，先在通知设置里打开")
            return
        }
        val nowMono = android.os.SystemClock.elapsedRealtime()
        val state = com.u707t.tomatosprout.domain.TimerEngine.start(
            mode = com.u707t.tomatosprout.domain.TimerMode.COUNTDOWN,
            plannedSeconds = 120,
            nowMono = nowMono,
            nowEpoch = System.currentTimeMillis(),
        )
        live.update(state, nowMono, "番茄芽 · 岛测试")
        messages.tryEmit("已发送测试岛，60 秒后自动消失")
        viewModelScope.launch {
            kotlinx.coroutines.delay(60_000)
            live.cancel()
        }
    }
    fun setLiveActivity(enabled: Boolean) = update { it.copy(liveActivityEnabled = enabled) }
    fun setDnd(enabled: Boolean) = update { it.copy(dndEnabled = enabled) }
    fun setDndStart(value: String) = update { it.copy(dndStart = value) }
    fun setDndEnd(value: String) = update { it.copy(dndEnd = value) }
    fun setFocusMinutes(value: Int) = update { it.copy(defaultFocusMinutes = value.coerceIn(5, 180)) }
    fun setBreakMinutes(value: Int) = update { it.copy(defaultBreakMinutes = value.coerceIn(5, 30)) }
    fun setLongBreakMinutes(value: Int) = update { it.copy(longBreakMinutes = value.coerceIn(10, 60)) }
    fun setIgnoreShortFocus(enabled: Boolean) = update { it.copy(ignoreShortFocus = enabled) }
    fun setStrict(enabled: Boolean) = update { it.copy(strictMode = enabled) }
    fun setCountUp(enabled: Boolean) = update { it.copy(countUpMode = enabled) }
    fun setMidnight(enabled: Boolean) = update { it.copy(midnightShift = enabled) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    fun exportJson(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = backup.exportJson()
                withContext(Dispatchers.IO) { write(uri, text) }
            }.onSuccess { messages.emit("已导出 JSON 备份") }
                .onFailure { messages.emit("导出失败：${it.message}") }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = backup.exportCsv()
                withContext(Dispatchers.IO) { write(uri, "\uFEFF$text") }
            }.onSuccess { messages.emit("已导出 CSV（可用 Excel 打开）") }
                .onFailure { messages.emit("导出失败：${it.message}") }
        }
    }

    fun importJson(uri: Uri) {
        viewModelScope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("无法读取文件")
                }
            }.getOrElse { messages.emit("读取失败：${it.message}"); return@launch }
            // 导入逻辑里会做字段校验；这里再兜一层，避免非法日期等在 toDomain 阶段抛异常后没有提示
            val result = runCatching { backup.importJson(text) }.getOrElse {
                messages.emit("导入失败：${it.message ?: "文件内容不合法"}")
                return@launch
            }
            if (result.ok) runCatching { scheduler.rescheduleAll() }
            messages.emit(
                when {
                    !result.ok -> "导入失败：${result.error}"
                    else -> "导入完成：导入 ${result.imported} 条（新增/更新），冲突保留 ${result.conflicts} 条，跳过 ${result.skipped} 条"
                },
            )
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            runCatching { reset.resetAll() }
                .onSuccess { messages.emit("已清除本地记录（记录以软删除方式保留，不再显示）") }
                .onFailure { messages.emit("清除失败：${it.message ?: "请稍后重试"}") }
        }
    }

    private fun write(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("无法写入所选位置")
    }
}
