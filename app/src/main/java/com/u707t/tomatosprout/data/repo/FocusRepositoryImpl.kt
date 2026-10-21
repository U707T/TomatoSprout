package com.u707t.tomatosprout.data.repo

import com.u707t.tomatosprout.data.db.FocusDao
import com.u707t.tomatosprout.data.db.toDomain
import com.u707t.tomatosprout.data.db.toEntity
import com.u707t.tomatosprout.domain.FocusRepository
import com.u707t.tomatosprout.domain.FocusSession
import com.u707t.tomatosprout.domain.MergeReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusRepositoryImpl @Inject constructor(private val dao: FocusDao) : FocusRepository {
    override fun observeAll(): Flow<List<FocusSession>> = dao.observeActive().map { list -> list.map { it.toDomain() } }
    override suspend fun all(): List<FocusSession> = dao.all().map { it.toDomain() }
    override suspend fun insert(session: FocusSession) {
        dao.insertIgnore(session.toEntity())
    }

    override suspend fun updateNote(id: String, note: String?) = dao.updateNote(id, note)

    override suspend fun softDelete(id: String, at: Long) = dao.softDelete(id, at)

    override suspend fun restoreAll(items: List<FocusSession>): MergeReport {
        val rows = dao.insertAllIgnore(items.map { it.toEntity() })
        val inserted = rows.count { it >= 0 }
        return MergeReport(inserted = inserted, skipped = items.size - inserted)
    }
}
