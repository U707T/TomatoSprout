package com.u707t.tomatosprout.data.repo

import com.u707t.tomatosprout.data.db.ReminderDao
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

    override suspend fun restoreAll(items: List<ReminderRule>): MergeReport {
        val rows = dao.insertAllIgnore(items.map { it.toEntity() })
        val inserted = rows.count { it >= 0 }
        return MergeReport(inserted = inserted, skipped = items.size - inserted)
    }
}
