package com.u707t.tomatosprout.sync

import com.u707t.tomatosprout.domain.Task

/**
 * 同步合并策略（纯函数，便于单测）。
 *
 * 设备时钟可能有几分钟偏差：用 `lastSync - 5min` 作为「变化窗口」。
 * 代价是极端情况下多生成一个冲突副本，但不会因为时钟慢几秒就静默丢掉一端的修改。
 */
object MergePolicy {
    const val CLOCK_SKEW_MS = 5 * 60_000L

    fun changedSince(lastSync: Long): Long =
        if (lastSync <= 0L) 0L else (lastSync - CLOCK_SKEW_MS).coerceAtLeast(0L)

    fun changed(updatedAt: Long, lastSync: Long): Boolean =
        lastSync <= 0L || updatedAt > changedSince(lastSync)

    /**
     * 规则保护名单：只包含「合并前就存在、且本地改过」的任务。
     *
     * 关键点：必须在插入远端新任务之前计算。否则远端新建的任务因为
     * `updatedAt > lastSync` 会被误判成「本地改过」，它的习惯 / 目标规则会被跳过，
     * 变成一条没有排期、没有连续天数的裸任务。
     */
    fun protectedTaskIds(localTasksBeforeMerge: Collection<Task>, lastSync: Long): Set<String> =
        if (lastSync <= 0L) {
            emptySet()
        } else {
            localTasksBeforeMerge.filter { it.updatedAt > changedSince(lastSync) }.map { it.id }.toSet()
        }
}
