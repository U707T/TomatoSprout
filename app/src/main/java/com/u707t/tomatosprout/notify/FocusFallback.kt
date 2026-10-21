package com.u707t.tomatosprout.notify

import android.app.AlarmManager
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.u707t.tomatosprout.R
import com.u707t.tomatosprout.domain.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 计时兜底：计划结束 + 1 分钟发一条高优先级通知。
 * 使用 setAndAllowWhileIdle（无 SCHEDULE_EXACT_ALARM 权限），可能被系统小幅推迟。
 */
@Singleton
class FocusFallbackScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(taskTitle: String, triggerAtMillis: Long, isBreak: Boolean) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        val pending = pendingIntent(taskTitle, isBreak)
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    fun cancel() {
        runCatching { alarmManager.cancel(pendingIntent("", false)) }
    }

    private fun pendingIntent(taskTitle: String, isBreak: Boolean): PendingIntent {
        val intent = Intent(context, FocusFallbackReceiver::class.java).apply {
            action = FocusFallbackReceiver.ACTION_FALLBACK
            putExtra(FocusFallbackReceiver.EXTRA_TITLE, taskTitle)
            putExtra(FocusFallbackReceiver.EXTRA_BREAK, isBreak)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_CODE = 3001
    }
}

@AndroidEntryPoint
class FocusFallbackReceiver : BroadcastReceiver() {
    @Inject lateinit var settings: SettingsRepository

    @SuppressLint("MissingPermission") // 已通过 NotificationPermission.canPost 显式校验
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val isBreak = intent.getBooleanExtra(EXTRA_BREAK, false)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val config = settings.current()
                if (!config.notificationsEnabled || !NotificationPermission.canPost(context)) return@launch
                if (DndPolicy.isMuted(config)) return@launch
                val text = if (isBreak) "休息结束，准备下一段专注吧" else "计划时长已到，回到 App 结算本次专注"
                val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_FOCUS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(
                        if (title.isBlank()) {
                            if (isBreak) "休息结束" else "专注时间到了"
                        } else {
                            (if (isBreak) "休息结束 · " else "专注时间到了 · ") + title
                        },
                    )
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(ContentIntents.openApp(context, "focus"))
                    .build()
                NotificationManagerCompat.from(context).notify(NotificationIds.FOCUS_FALLBACK, notification)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FALLBACK = "com.u707t.tomatosprout.FOCUS_FALLBACK"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BREAK = "is_break"
    }
}
