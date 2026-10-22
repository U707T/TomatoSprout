package com.u707t.tomatosprout.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.data.db.SyncDao
import com.u707t.tomatosprout.data.db.SyncLogEntity
import com.u707t.tomatosprout.domain.AppSettings
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.notify.ReminderScheduler
import com.u707t.tomatosprout.sync.SyncEngine
import com.u707t.tomatosprout.sync.SyncResult
import com.u707t.tomatosprout.sync.SyncRetryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebDavViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val engine: SyncEngine,
    private val syncDao: SyncDao,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val logs: StateFlow<List<SyncLogEntity>> =
        syncDao.observeLogs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    private suspend fun saveFields(server: String, username: String, password: String, path: String, wifiOnly: Boolean) {
        settingsRepo.update {
            it.copy(
                webDavServer = server.trim(),
                webDavUsername = username.trim(),
                webDavPath = path.trim().ifBlank { "/tomatosprout" },
                wifiOnly = wifiOnly,
            )
        }
        if (password.isNotBlank()) settingsRepo.saveWebDavPassword(password)
    }

    fun save(server: String, username: String, password: String, path: String, wifiOnly: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            try {
                runCatching { saveFields(server, username, password, path, wifiOnly) }
                    .onSuccess { messages.emit("WebDAV 配置已保存") }
                    .onFailure { messages.emit("保存失败：${it.message ?: "请稍后重试"}") }
            } finally {
                _busy.value = false
            }
        }
    }

    fun test(server: String, username: String, password: String, path: String, wifiOnly: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val saved = runCatching {
                    saveFields(server, username, password, path, wifiOnly)
                    engine.testConnection()
                }.getOrElse { Result.failure(it) }
                messages.emit(saved.fold({ "连接成功，服务器可以访问" }, { "连接失败：${it.message}" }))
            } finally {
                _busy.value = false
            }
        }
    }

    fun sync(server: String, username: String, password: String, path: String, wifiOnly: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val result = runCatching {
                    saveFields(server, username, password, path, wifiOnly)
                    engine.sync()
                }.getOrElse { SyncResult(false, it.message ?: "同步失败") }
                if (result.success) runCatching { scheduler.rescheduleAll() }
                messages.emit(result.message)
                if (!result.success && result.retryable) {
                    SyncRetryWorker.enqueue(context, wifiOnly)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            syncDao.clearLogs()
            messages.emit("同步日志已清空")
        }
    }
}
