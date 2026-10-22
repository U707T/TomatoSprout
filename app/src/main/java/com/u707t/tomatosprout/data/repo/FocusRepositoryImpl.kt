package com.u707t.tomatosprout.data.repo

import com.u707t.tomatosprout.data.db.FocusDao
import com.u707t.tomatosprout.data.db.toDomain
import com.u707t.tomatosprout.data.db.toEntity
import com.u707t.tomatosprout.domain.FocusRepository
import com.u707t.tomatosprout.domain.FocusSession
import com.u707t.tomatosprout.domain.MergeReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusRepositoryImpl @Inject constructor(private val dao: FocusDao) : FocusRepository {
    override fun observeAll(): Flow<List<FocusSession>> = dao.observeActive().map { list -> list.map { it.toDomain() } }
    override suspend fun all(): List<FocusSession> = dao.all().map { it.toDomain() }

    override suspend fun insert(session: FocusSession) {
        dao.insertIgnore(session.toEntity())
    }

    override suspend fun updateNote(id: String, note: String?, at: Long) = dao.updateNote(id, note, at)

    override suspend fun softDelete(id: String, at: Long) = dao.softDelete(id, at)

    /** 记录级合并：远端 updatedAt 更晚才覆盖本地（含备注、删除、中断原因）。 */
    override suspend fun mergeAll(items: List<FocusSession>): MergeReport {
        if (items.isEmpty()) return MergeReport()
        val local = dao.all().associateBy { it.id }
        val toUpsert = items.filter { remote ->
            val localEntity = local[remote.id]
            localEntity == null || remote.updatedAt > localEntity.updatedAt
        }
        val updated = toUpsert.count { local[it.id] != null }
        dao.upsertAll(toUpsert.associateBy { it.id }.values.map { it.toEntity() })
        return MergeReport(
            inserted = toUpsert.size - updated,
            updated = updated,
            skipped = items.size - toUpsert.size,
        )
    }

    override suspend fun completedPomodorosOn(date: LocalDate): Int =
        dao.countCompletedPomodoros(date.toString())
}
