package com.u707t.tomatosprout.data.repo

import com.u707t.tomatosprout.data.db.WishDao
import com.u707t.tomatosprout.data.db.toDomain
import com.u707t.tomatosprout.data.db.toEntity
import com.u707t.tomatosprout.domain.MergeReport
import com.u707t.tomatosprout.domain.WishItem
import com.u707t.tomatosprout.domain.WishRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WishRepositoryImpl @Inject constructor(private val dao: WishDao) : WishRepository {
    override fun observeAll(): Flow<List<WishItem>> = dao.observeActive().map { list -> list.map { it.toDomain() } }
    override suspend fun all(): List<WishItem> = dao.all().map { it.toDomain() }
    override suspend fun upsert(item: WishItem) = dao.upsert(item.toEntity())
    override suspend fun softDelete(id: String, at: Long) = dao.softDelete(id, at)

    override suspend fun restoreAll(items: List<WishItem>): MergeReport {
        val rows = dao.insertAllIgnore(items.map { it.toEntity() })
        val inserted = rows.count { it >= 0 }
        return MergeReport(inserted = inserted, skipped = items.size - inserted)
    }
}
