package com.u707t.tomatosprout.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BreakCycleTest {
    @Test
    fun `long break is due every four completed pomodoros`() {
        assertFalse(BreakCycle.isLongBreakDue(0))
        assertFalse(BreakCycle.isLongBreakDue(1))
        assertFalse(BreakCycle.isLongBreakDue(3))
        assertTrue(BreakCycle.isLongBreakDue(4))
        assertFalse(BreakCycle.isLongBreakDue(5))
        assertTrue(BreakCycle.isLongBreakDue(8))
        assertTrue(BreakCycle.isLongBreakDue(12))
    }
}
