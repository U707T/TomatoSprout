package com.u707t.tomatosprout.data.repo

import com.u707t.tomatosprout.data.db.CheckInDao
import com.u707t.tomatosprout.data.db.toDomain
import com.u707t.tomatosprout.data.db.toEntity
import com.u707t.tomatosprout.domain.CheckIn
import com.u707t.tomatosprout.domain.CheckInRepository
import com.u707t.tomatosprout.domain.MergeReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckInRepositoryImpl @Inject constructor(
    private val dao: CheckInDao,
) : CheckInRepository {
    override fun observeAll(): Flow<List<CheckIn>> = dao.observeActive().map { list -> list.map { it.toDomain() } }
    override suspend fun all(): List<CheckIn> = dao.all().map { it.toDomain() }
    override suspend fun forTask(taskId: String): List<CheckIn> = dao.forTask(taskId).map { it.toDomain() }

    /**
     * 唯一索引 (taskId, checkDate, isMakeup) 防止重复打卡。
     * 若同一逻辑记录曾被软删除，则复活原记录，而不是插入新行。
     */
    override suspend fun insert(checkIn: CheckIn): Boolean {
        val row = dao.insertIgnore(checkIn.toEntity())
        if (row >= 0) return true
        return dao.reactivate(
            taskId = checkIn.taskId,
            date = checkIn.date.toString(),
            makeup = checkIn.isMakeup,
            at = checkIn.createdAt,
            source = checkIn.source,
        ) > 0
    }

    override suspend fun updateCount(id: String, count: Int) = dao.updateCount(id, count)

    override suspend fun updateMeta(id: String, note: String?, mood: Int?) = dao.updateMeta(id, note, mood)
    override suspend fun softDelete(id: String, at: Long) = dao.softDelete(id, at)

    override suspend fun restoreAll(items: List<CheckIn>): MergeReport {
        val rows = dao.insertAllIgnore(items.map { it.toEntity() })
        val inserted = rows.count { it >= 0 }
        return MergeReport(inserted = inserted, skipped = items.size - inserted)
    }
}
