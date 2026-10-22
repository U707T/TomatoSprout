package com.u707t.tomatosprout.data.repo

import com.u707t.tomatosprout.data.db.CheckInDao
import com.u707t.tomatosprout.data.db.CheckInEntity
import com.u707t.tomatosprout.data.db.toDomain
import com.u707t.tomatosprout.data.db.toEntity
import com.u707t.tomatosprout.domain.CheckIn
import com.u707t.tomatosprout.domain.CheckInRepository
import com.u707t.tomatosprout.domain.MergeReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

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
            at = checkIn.updatedAt.takeIf { it > 0 } ?: checkIn.createdAt,
            source = checkIn.source,
        ) > 0
    }

    override suspend fun updateCount(id: String, count: Int, at: Long) = dao.updateCount(id, count, at)

    override suspend fun updateMeta(id: String, note: String?, mood: Int?, at: Long) =
        dao.updateMeta(id, note, mood, at)

    override suspend fun softDelete(id: String, at: Long) = dao.softDelete(id, at)

    /**
     * 记录级合并：
     * - 远端 updatedAt 更晚才覆盖本地；
     * - 两端都未删除时，同一天多次打卡取较大 count（各设备各 +1 不会丢一次）；
     * - 删除/复活按 updatedAt 的新旧决定，旧快照不会把已删除记录拉回来。
     */
    override suspend fun mergeAll(items: List<CheckIn>): MergeReport {
        if (items.isEmpty()) return MergeReport()
        val localRows = dao.all()
        val localById = localRows.associateBy { it.id }
        // 同一任务 + 日期 + 补卡标记是唯一键；两台设备各自新建同一天的打卡会得到不同 id，
        // 直接 upsert 会撞唯一索引。这里按逻辑键合并到本地行，避免整次同步失败。
        val localByKey = localRows.associateBy { Triple(it.taskId, it.checkDate, it.isMakeup) }
        val toUpsert = ArrayList<CheckInEntity>(items.size)
        val pendingKeys = HashSet<Triple<String, String, Boolean>>()
        var inserted = 0
        var updated = 0
        items.forEach { remote ->
            val entity = remote.toEntity()
            val local = localById[entity.id]
            if (local != null) {
                val bothActive = local.deletedAt == null && entity.deletedAt == null
                val mergedCount = if (bothActive) max(local.count, entity.count) else local.count
                when {
                    entity.updatedAt > local.updatedAt -> {
                        toUpsert += if (bothActive) entity.copy(count = mergedCount) else entity
                        updated++
                    }
                    // 远端时间更早但 count 更高（例如另一台设备先打卡、本机后写备注）：
                    // 备注保留本地，次数取较大值，避免漏掉一次打卡。
                    mergedCount > local.count -> {
                        toUpsert += local.copy(count = mergedCount)
                        updated++
                    }
                }
                return@forEach
            }
            val key = Triple(entity.taskId, entity.checkDate, entity.isMakeup)
            val sameKey = localByKey[key]
            if (sameKey != null) {
                val newer = if (entity.updatedAt > sameKey.updatedAt) entity else sameKey
                val bothActive = sameKey.deletedAt == null && entity.deletedAt == null
                val mergedCount = if (bothActive) max(sameKey.count, entity.count) else newer.count
                toUpsert += newer.copy(id = sameKey.id, count = mergedCount)
                updated++
            } else {
                // 远端文件若自带重复日期（手工改过的备份），只保留第一条，避免撞唯一索引
                if (pendingKeys.add(key)) {
                    toUpsert += entity
                    inserted++
                }
            }
        }
        // 远端文件理论上不会重复 id，但导入的文件未必；upsert 前按 id 去重，避免同一批
        // 出现重复主键导致 SQLite 报错。
        dao.upsertAll(toUpsert.associateBy { it.id }.values.toList())
        return MergeReport(
            inserted = inserted,
            updated = updated,
            skipped = items.size - inserted - updated,
        )
    }
}
