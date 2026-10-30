package com.u707t.tomatosprout.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.u707t.tomatosprout.domain.AppSettings
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.domain.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tomatosprout_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secrets: SecretStore,
) : SettingsRepository {
    private val store = context.settingsDataStore

    override val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    override suspend fun current(): AppSettings {
        var value = settings.first()
        if (value.deviceId.isBlank()) {
            val id = UUID.randomUUID().toString()
            update { it.copy(deviceId = id) }
            value = value.copy(deviceId = id)
        }
        return value
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs.write(next)
        }
    }

    override suspend fun saveWebDavPassword(password: String) = secrets.put(KEY_PASSWORD, password)
    override suspend fun webDavPassword(): String? = secrets.get(KEY_PASSWORD)
    override suspend fun hasWebDavPassword(): Boolean = secrets.has(KEY_PASSWORD)

    private fun Preferences.toSettings() = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(this[K_THEME] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM),
        accent = this[K_ACCENT] ?: "tomato",
        taskSort = this[K_TASK_SORT] ?: "manual",
        hideDone = this[K_HIDE_DONE] ?: false,
        simpleMode = this[K_SIMPLE] ?: false,
        notificationsEnabled = this[K_NOTIFICATIONS] ?: true,
        liveActivityEnabled = this[K_LIVE_ACTIVITY] ?: true,
        forceFocusNotification = this[K_FORCE_FOCUS] ?: false,
        dndEnabled = this[K_DND] ?: false,
        dndStart = this[K_DND_START] ?: "22:00",
        dndEnd = this[K_DND_END] ?: "07:00",
        defaultFocusMinutes = this[K_FOCUS] ?: 25,
        defaultBreakMinutes = this[K_BREAK] ?: 5,
        longBreakMinutes = this[K_LONG_BREAK] ?: 15,
        ignoreShortFocus = this[K_IGNORE_SHORT] ?: true,
        strictMode = this[K_STRICT] ?: false,
        countUpMode = this[K_COUNTUP] ?: false,
        midnightShift = this[K_MIDNIGHT] ?: true,
        wifiOnly = this[K_WIFI_ONLY] ?: false,
        webDavServer = this[K_SERVER] ?: "",
        webDavUsername = this[K_USER] ?: "",
        webDavPath = this[K_PATH] ?: "/tomatosprout",
        deviceId = this[K_DEVICE] ?: "",
        lastSyncedAt = this[K_LAST_SYNC],
    )

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: AppSettings) {
        this[K_THEME] = s.themeMode.name
        this[K_ACCENT] = s.accent
        this[K_TASK_SORT] = s.taskSort
        this[K_HIDE_DONE] = s.hideDone
        this[K_SIMPLE] = s.simpleMode
        this[K_NOTIFICATIONS] = s.notificationsEnabled
        this[K_LIVE_ACTIVITY] = s.liveActivityEnabled
        this[K_FORCE_FOCUS] = s.forceFocusNotification
        this[K_DND] = s.dndEnabled
        this[K_DND_START] = s.dndStart
        this[K_DND_END] = s.dndEnd
        this[K_FOCUS] = s.defaultFocusMinutes
        this[K_BREAK] = s.defaultBreakMinutes
        this[K_LONG_BREAK] = s.longBreakMinutes
        this[K_IGNORE_SHORT] = s.ignoreShortFocus
        this[K_STRICT] = s.strictMode
        this[K_COUNTUP] = s.countUpMode
        this[K_MIDNIGHT] = s.midnightShift
        this[K_WIFI_ONLY] = s.wifiOnly
        this[K_SERVER] = s.webDavServer
        this[K_USER] = s.webDavUsername
        this[K_PATH] = s.webDavPath
        this[K_DEVICE] = s.deviceId
        s.lastSyncedAt?.let { this[K_LAST_SYNC] = it }
    }

    private companion object {
        val K_THEME = stringPreferencesKey("theme")
        val K_ACCENT = stringPreferencesKey("accent")
        val K_TASK_SORT = stringPreferencesKey("task_sort")
        val K_HIDE_DONE = booleanPreferencesKey("hide_done")
        val K_SIMPLE = booleanPreferencesKey("simple_mode")
        val K_NOTIFICATIONS = booleanPreferencesKey("notifications")
        val K_LIVE_ACTIVITY = booleanPreferencesKey("live_activity")
        val K_FORCE_FOCUS = booleanPreferencesKey("force_focus_notification")
        val K_DND = booleanPreferencesKey("dnd_enabled")
        val K_DND_START = stringPreferencesKey("dnd_start")
        val K_DND_END = stringPreferencesKey("dnd_end")
        val K_FOCUS = intPreferencesKey("default_focus")
        val K_BREAK = intPreferencesKey("default_break")
        val K_LONG_BREAK = intPreferencesKey("long_break")
        val K_IGNORE_SHORT = booleanPreferencesKey("ignore_short_focus")
        val K_STRICT = booleanPreferencesKey("strict_mode")
        val K_COUNTUP = booleanPreferencesKey("count_up")
        val K_MIDNIGHT = booleanPreferencesKey("midnight_shift")
        val K_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val K_SERVER = stringPreferencesKey("webdav_server")
        val K_USER = stringPreferencesKey("webdav_username")
        val K_PATH = stringPreferencesKey("webdav_path")
        val K_DEVICE = stringPreferencesKey("device_id")
        val K_LAST_SYNC = longPreferencesKey("last_sync")
        const val KEY_PASSWORD = "webdav_password"
    }
}
