package com.u707t.tomatosprout.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.u707t.tomatosprout.MainActivity
import com.u707t.tomatosprout.R
import com.u707t.tomatosprout.domain.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

object NotificationIds {
    const val FOCUS_LIVE = 1001
    const val REMINDER_BASE = 2000
}

@Singleton
class NotificationChannels @Inject constructor(@ApplicationContext private val context: Context) {
    fun ensureCreated() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            "习惯提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "到点提醒你完成今天的习惯" }
        val focus = NotificationChannel(
            CHANNEL_FOCUS,
            "专注实时活动",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "专注计时胶囊卡片，可随时关闭"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannels(listOf(reminder, focus))
    }

    companion object {
        const val CHANNEL_REMINDER = "habit_reminder"
        const val CHANNEL_FOCUS = "focus_live"
    }
}

object NotificationPermission {
    fun canPost(context: Context): Boolean {
        val runtime = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return runtime && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

object DndPolicy {
    fun isMuted(settings: AppSettings, now: LocalTime = LocalTime.now()): Boolean {
        if (!settings.dndEnabled) return false
        val start = parseTime(settings.dndStart) ?: return false
        val end = parseTime(settings.dndEnd) ?: return false
        return if (start <= end) {
            now >= start && now < end
        } else {
            now >= start || now < end
        }
    }

    private fun parseTime(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()
}

object ContentIntents {
    fun openApp(context: Context, navigate: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            navigate?.let { putExtra(MainActivity.EXTRA_NAVIGATE, it) }
        }
        return PendingIntent.getActivity(
            context,
            navigate?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

@Singleton
class ReminderNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    fun notifyReminder(ruleId: String, title: String, body: String) {
        if (!NotificationPermission.canPost(context)) return
        val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(ContentIntents.openApp(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(context)
            .notify(NotificationIds.REMINDER_BASE + (ruleId.hashCode() and 0xFF), builder.build())
    }
}
