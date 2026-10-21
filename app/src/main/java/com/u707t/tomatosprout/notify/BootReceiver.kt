package com.u707t.tomatosprout.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 开机 / 应用更新后重排提醒；不使用任何后台常驻服务。 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var channels: NotificationChannels

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                channels.ensureCreated()
                // 重启后单调时钟归零，上一轮实时活动通知已失效
                NotificationManagerCompat.from(context).cancel(NotificationIds.FOCUS_LIVE)
                scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
