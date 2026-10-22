package com.u707t.tomatosprout.data.reset

import android.content.Context
import androidx.room.withTransaction
import com.u707t.tomatosprout.data.db.AppDatabase
import com.u707t.tomatosprout.data.db.BadgeDao
import com.u707t.tomatosprout.data.db.CheckInDao
import com.u707t.tomatosprout.data.db.FocusDao
import com.u707t.tomatosprout.data.db.ReminderDao
import com.u707t.tomatosprout.data.db.SubTaskDao
import com.u707t.tomatosprout.data.db.SyncDao
import com.u707t.tomatosprout.data.db.TaskDao
import com.u707t.tomatosprout.data.db.WishDao
import com.u707t.tomatosprout.data.focus.FocusTimerStore
import com.u707t.tomatosprout.domain.AppClock
import com.u707t.tomatosprout.notify.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户主动「清除所有数据」。
 * 打卡与专注仍然只做软删除（保留审计痕迹），不会物理删除，符合数据层约束。
 */
@Singleton
class DataResetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tasks: TaskDao,
    private val subTasks: SubTaskDao,
    private val checkIns: CheckInDao,
    private val focus: FocusDao,
    private val wishes: WishDao,
    private val badges: BadgeDao,
    private val reminders: ReminderDao,
    private val sync: SyncDao,
    private val reminderScheduler: ReminderScheduler,
    private val db: AppDatabase,
    private val clock: AppClock,
) {
    suspend fun resetAll() {
        val now = clock.nowMillis()
        // 先取消已排期的 Alarm，再删规则；否则规则没了，闹钟还会白唤醒一次
        runCatching { reminderScheduler.cancelAll() }
        FocusTimerStore.clear(context)
        // 数据库侧整体事务：中途失败不会留下「任务删了、规则还在」的半清理状态
        db.withTransaction {
            tasks.softDeleteAll(now)
            subTasks.deleteAll()
            checkIns.softDeleteAll(now)
            focus.softDeleteAll(now)
            wishes.softDeleteAll(now)
            reminders.deleteAll()
            badges.resetUnlocks()
            sync.clearLogs()
            // 专注被软删除后，实际番茄数也要归零
            tasks.recountCompletedPomos()
        }
    }
}
