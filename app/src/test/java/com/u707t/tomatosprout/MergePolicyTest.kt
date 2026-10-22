package com.u707t.tomatosprout.sync

import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergePolicyTest {
    private fun task(id: String, updatedAt: Long) = Task(
        id = id,
        type = TaskType.HABIT,
        title = id,
        createdAt = 0L,
        updatedAt = updatedAt,
    )

    @Test
    fun `first sync treats every difference as changed`() {
        assertTrue(MergePolicy.changed(1L, lastSync = 0L))
        assertTrue(MergePolicy.changed(0L, lastSync = 0L))
        assertEquals(emptySet<String>(), MergePolicy.protectedTaskIds(listOf(task("a", 1L)), 0L))
    }

    @Test
    fun `clock skew window keeps edits near the sync boundary`() {
        val lastSync = 10_000_000L
        val justBefore = lastSync - MergePolicy.CLOCK_SKEW_MS + 1_000L
        val longBefore = lastSync - MergePolicy.CLOCK_SKEW_MS - 1_000L

        assertTrue(MergePolicy.changed(justBefore, lastSync))
        assertFalse(MergePolicy.changed(longBefore, lastSync))
    }

    @Test
    fun `only locally changed pre-merge tasks are protected`() {
        val lastSync = 10_000_000L
        val local = listOf(
            task("changed", lastSync + 1_000L),
            task("unchanged", lastSync - MergePolicy.CLOCK_SKEW_MS - 1_000L),
        )

        assertEquals(setOf("changed"), MergePolicy.protectedTaskIds(local, lastSync))
    }

    @Test
    fun `new remote task is not in the pre-merge local set and cannot be protected`() {
        val lastSync = 10_000_000L
        val localBefore = listOf(task("existing", lastSync - 1_000_000L))
        val protected = MergePolicy.protectedTaskIds(localBefore, lastSync)

        // 远端新任务（id 不在 localBefore）不会进入保护名单，它的规则会被正常应用
        assertFalse("remote-new" in protected)
        assertTrue(protected.isEmpty())
    }
}
