package com.u707t.tomatosprout

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.u707t.tomatosprout.di.ApplicationScope
import com.u707t.tomatosprout.domain.BadgeUseCase
import com.u707t.tomatosprout.notify.NotificationChannels
import com.u707t.tomatosprout.notify.ReminderScheduler
import com.u707t.tomatosprout.ui.feedback.FeedbackController
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SproutApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var channels: NotificationChannels
    @Inject lateinit var badges: BadgeUseCase
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var feedback: FeedbackController

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        channels.ensureCreated()
        feedback.warmUp()
        appScope.launch {
            runCatching { badges.ensureSeeded() }
            // 开机 / 应用更新由 BootReceiver 重排；这里再兜底一次，防止 OEM 清理 Alarm
            runCatching { scheduler.rescheduleAll() }
        }
    }
}
