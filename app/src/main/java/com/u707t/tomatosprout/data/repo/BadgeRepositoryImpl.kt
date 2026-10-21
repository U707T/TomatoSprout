package com.u707t.tomatosprout.data.repo

import com.u707t.tomatosprout.data.db.BadgeDao
import com.u707t.tomatosprout.data.db.toDomain
import com.u707t.tomatosprout.data.db.toEntity
import com.u707t.tomatosprout.domain.Badge
import com.u707t.tomatosprout.domain.BadgeRepository
import com.u707t.tomatosprout.domain.MergeReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BadgeRepositoryImpl @Inject constructor(private val dao: BadgeDao) : BadgeRepository {
    override fun observeAll(): Flow<List<Badge>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun all(): List<Badge> = dao.all().map { it.toDomain() }
    override suspend fun seed(defs: List<Badge>) = dao.seed(defs.map { it.toEntity() })
    override suspend fun unlock(keys: Set<String>, at: Long) = dao.unlock(keys.toList(), at)

    override suspend fun restoreAll(items: List<Badge>): MergeReport {
        val rows = dao.insertAllIgnore(items.map { it.toEntity() })
        val inserted = rows.count { it >= 0 }
        return MergeReport(inserted = inserted, skipped = items.size - inserted)
    }
}
