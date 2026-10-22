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

    /** 记录级合并：远端 updatedAt 更新才覆盖本地（含完成状态 / 删除墓碑）。 */
    override suspend fun mergeAll(items: List<WishItem>): MergeReport {
        if (items.isEmpty()) return MergeReport()
        val local = dao.all().associateBy { it.id }
        val toUpsert = items
            .filter { remote -> local[remote.id]?.let { remote.updatedAt > it.updatedAt } ?: true }
            .associateBy { it.id }
            .values
        val updated = toUpsert.count { local[it.id] != null }
        dao.upsertAll(toUpsert.map { it.toEntity() })
        return MergeReport(
            inserted = toUpsert.size - updated,
            updated = updated,
            skipped = items.size - toUpsert.size,
        )
    }
}
