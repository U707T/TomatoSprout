package com.u707t.tomatosprout.data.repo

import com.u707t.tomatosprout.data.db.ReminderDao
import com.u707t.tomatosprout.data.db.ReminderRuleEntity
import com.u707t.tomatosprout.data.db.toDomain
import com.u707t.tomatosprout.data.db.toEntity
import com.u707t.tomatosprout.domain.MergeReport
import com.u707t.tomatosprout.domain.ReminderRepository
import com.u707t.tomatosprout.domain.ReminderRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl @Inject constructor(private val dao: ReminderDao) : ReminderRepository {
    override fun observeAll(): Flow<List<ReminderRule>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun all(): List<ReminderRule> = dao.all().map { it.toDomain() }
    override suspend fun forTask(taskId: String): ReminderRule? = dao.forTask(taskId)?.toDomain()
    override suspend fun upsert(rule: ReminderRule) = dao.upsert(rule.toEntity())
    override suspend fun deleteForTask(taskId: String) = dao.deleteForTask(taskId)

    /**
     * 记录级合并。reminder_rules 对 taskId 有唯一索引：
     * 两台设备给同一个任务各建一条提醒会得到不同 id，直接 upsert 会撞唯一索引。
     * 因此先按 id，再按 taskId 匹配；同任务不同 id 时保留本地 id。
     */
    override suspend fun mergeAll(items: List<ReminderRule>): MergeReport {
        if (items.isEmpty()) return MergeReport()
        val localRows = dao.all()
        val byId = localRows.associateBy { it.id }
        val byTask = localRows.associateBy { it.taskId }
        val toUpsert = ArrayList<ReminderRuleEntity>(items.size)
        val pendingTasks = HashSet<String>()
        var inserted = 0
        var updated = 0

        items.forEach { remote ->
            val entity = remote.toEntity()
            val local = byId[entity.id]
            if (local != null) {
                if (entity.updatedAt > local.updatedAt) {
                    toUpsert += entity
                    updated++
                }
                return@forEach
            }
            val sameTask = byTask[entity.taskId]
            if (sameTask != null) {
                if (entity.updatedAt > sameTask.updatedAt) {
                    toUpsert += entity.copy(id = sameTask.id)
                    updated++
                }
            } else if (pendingTasks.add(entity.taskId)) {
                // 远端文件若自带同一任务的两条提醒，只保留第一条，避免撞 taskId 唯一索引
                toUpsert += entity
                inserted++
            }
        }
        dao.upsertAll(toUpsert.associateBy { it.id }.values.toList())
        return MergeReport(
            inserted = inserted,
            updated = updated,
            skipped = items.size - inserted - updated,
        )
    }
}
