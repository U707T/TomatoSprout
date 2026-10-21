package com.u707t.tomatosprout

import com.u707t.tomatosprout.sync.Hashing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HashingTest {
    @Test
    fun `sha256 is stable and distinguishes content`() {
        val a = Hashing.sha256("tomato".toByteArray())
        val b = Hashing.sha256("tomato".toByteArray())
        val c = Hashing.sha256("tomato!".toByteArray())
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(64, a.length)
    }
}
