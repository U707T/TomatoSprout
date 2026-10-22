package com.u707t.tomatosprout.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.u707t.tomatosprout.domain.AppClock
import com.u707t.tomatosprout.domain.ReminderRepository
import com.u707t.tomatosprout.domain.ReminderRule
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.domain.TaskRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 使用 AlarmManager.setAndAllowWhileIdle（不需要 SCHEDULE_EXACT_ALARM 权限）。
 * 代价：系统可能推迟几分钟送达；开机 / 应用更新后由 BootReceiver 重排。
 * 这是「权限最小化」下的有意取舍，UI 中有说明。
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AppClock,
    private val reminders: ReminderRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() {
        reminders.all().filter { it.enabled }.forEach { schedule(it) }
    }

    /** 清除所有已排期提醒（清空数据 / 重置时用）。 */
    suspend fun cancelAll() {
        reminders.all().forEach { cancel(it.id) }
    }

    fun schedule(rule: ReminderRule) {
        if (!rule.enabled) {
            cancel(rule.id)
            return
        }
        val now = ZonedDateTime.now(clock.zone())
        val trigger = nextTrigger(rule, now) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            putExtra(ReminderReceiver.EXTRA_RULE_ID, rule.id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            rule.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(), pending)
        }
    }

    fun cancel(ruleId: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            putExtra(ReminderReceiver.EXTRA_RULE_ID, ruleId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            ruleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
    }

    fun nextTrigger(rule: ReminderRule, now: ZonedDateTime): ZonedDateTime? {
        val time = runCatching { LocalTime.parse(rule.timeOfDay) }.getOrNull() ?: return null
        for (offset in 0..7) {
            val date: LocalDate = now.toLocalDate().plusDays(offset.toLong())
            if (((rule.weekdaysMask shr (date.dayOfWeek.value - 1)) and 1) != 1 && rule.weekdaysMask != 0) continue
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (candidate.toInstant().toEpochMilli() > now.toInstant().toEpochMilli() + 5_000) return candidate
        }
        return null
    }
}

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var reminders: ReminderRepository
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifier: ReminderNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val rule = reminders.all().firstOrNull { it.id == ruleId } ?: return@launch
                if (!rule.enabled) return@launch
                val task = tasks.task(rule.taskId)
                val config = settings.current()
                if (
                    config.notificationsEnabled && !DndPolicy.isMuted(config) &&
                    task != null && task.deletedAt == null
                ) {
                    notifier.notifyReminder(
                        ruleId = rule.id,
                        title = "该打卡啦：${task.title}",
                        body = "完成今天的小目标，连续记录又向前一步。",
                    )
                }
                // 规划下一次（跨天/跨周）
                scheduler.schedule(rule)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.u707t.tomatosprout.REMIND"
        const val EXTRA_RULE_ID = "rule_id"
    }
}
