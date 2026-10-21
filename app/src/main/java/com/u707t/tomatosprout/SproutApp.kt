package com.u707t.tomatosprout

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.u707t.tomatosprout.domain.BadgeUseCase
import com.u707t.tomatosprout.notify.NotificationChannels
import com.u707t.tomatosprout.notify.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SproutApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var channels: NotificationChannels
    @Inject lateinit var badges: BadgeUseCase
    @Inject lateinit var scheduler: ReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        channels.ensureCreated()
        appScope.launch {
            runCatching { badges.ensureSeeded() }
            // 重启后排期会丢（未申请 BOOT_COMPLETED 权限），每次打开 App 重建
            runCatching { scheduler.rescheduleAll() }
        }
    }
}
