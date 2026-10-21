package com.u707t.tomatosprout.data.reset

import com.u707t.tomatosprout.data.db.BadgeDao
import com.u707t.tomatosprout.data.db.CheckInDao
import com.u707t.tomatosprout.data.db.FocusDao
import com.u707t.tomatosprout.data.db.ReminderDao
import com.u707t.tomatosprout.data.db.SyncDao
import com.u707t.tomatosprout.data.db.TaskDao
import com.u707t.tomatosprout.data.db.WishDao
import com.u707t.tomatosprout.domain.AppClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户主动「清除所有数据」。
 * 打卡与专注仍然只做软删除（保留审计痕迹），不会物理删除，符合数据层约束。
 */
@Singleton
class DataResetManager @Inject constructor(
    private val tasks: TaskDao,
    private val checkIns: CheckInDao,
    private val focus: FocusDao,
    private val wishes: WishDao,
    private val badges: BadgeDao,
    private val reminders: ReminderDao,
    private val sync: SyncDao,
    private val clock: AppClock,
) {
    suspend fun resetAll() {
        val now = clock.nowMillis()
        tasks.softDeleteAll(now)
        checkIns.softDeleteAll(now)
        focus.softDeleteAll(now)
        wishes.softDeleteAll(now)
        reminders.deleteAll()
        badges.resetUnlocks()
        sync.clearLogs()
    }
}
