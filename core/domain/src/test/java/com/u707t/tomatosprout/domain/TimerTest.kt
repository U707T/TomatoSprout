package com.u707t.tomatosprout.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimerTest {
    private val t0 = 1_000_000L

    @Test
    fun `countdown finishes exactly at planned seconds`() {
        var state = TimerEngine.start(TimerMode.COUNTDOWN, 25 * 60, t0, 1_700_000_000_000L)
        assertEquals(1_500_000L, state.remainingMillis(t0))
        state = TimerEngine.tick(state, t0 + 1_499_999)
        assertEquals(TimerPhase.RUNNING, state.phase)
        state = TimerEngine.tick(state, t0 + 1_500_000)
        assertEquals(TimerPhase.FINISHED, state.phase)
        assertEquals(1_500, state.elapsedSeconds(t0 + 1_500_000))
    }

    @Test
    fun `countup never auto finishes`() {
        var state = TimerEngine.start(TimerMode.COUNTUP, 60, t0, 0L)
        state = TimerEngine.tick(state, t0 + 10_000_000)
        assertEquals(TimerPhase.RUNNING, state.phase)
        state = TimerEngine.stop(state, t0 + 10_000_000)
        assertEquals(TimerPhase.FINISHED, state.phase)
        assertEquals(10_000, state.elapsedSeconds(t0 + 10_000_000))
    }

    @Test
    fun `extend pushes the deadline and keeps elapsed time`() {
        val state = TimerEngine.start(TimerMode.COUNTDOWN, 25 * 60, t0, 0L)
        val extended = TimerEngine.extend(state, 5 * 60)
        assertEquals(30 * 60, extended.plannedSeconds)
        // 已经跑过的时间不清零，剩余时间按新计划重算
        assertEquals(30 * 60_000L - 60_000L, extended.remainingMillis(t0 + 60_000))
        // 原状态不可变
        assertEquals(25 * 60, state.plannedSeconds)
    }

    @Test
    fun `extend ignores countup and finished timers`() {
        val countUp = TimerEngine.start(TimerMode.COUNTUP, 60, t0, 0L)
        assertEquals(countUp, TimerEngine.extend(countUp, 300))

        val finished = TimerEngine.stop(TimerEngine.start(TimerMode.COUNTDOWN, 60, t0, 0L), t0 + 60_000)
        assertEquals(finished, TimerEngine.extend(finished, 300))
    }

    @Test
    fun `extend stops at the three hour ceiling`() {
        val state = TimerEngine.start(TimerMode.COUNTDOWN, 170 * 60, t0, 0L)
        val extended = TimerEngine.extend(state, 60 * 60)
        assertEquals(TimerEngine.MAX_PLANNED_SECONDS, extended.plannedSeconds)
    }

    @Test
    fun `strict mode rejects pause`() {
        val state = TimerEngine.start(TimerMode.COUNTDOWN, 60, t0, 0L, strict = true)
        assertInstanceOf(PauseResult.StrictBlocked::class.java, TimerEngine.togglePause(state, t0 + 1000))
    }

    @Test
    fun `pause and resume preserves elapsed and counts interruption`() {
        var state = TimerEngine.start(TimerMode.COUNTDOWN, 600, t0, 0L)
        val paused = TimerEngine.togglePause(state, t0 + 5_000)
        state = (paused as PauseResult.Ok).state
        assertEquals(5_000L, state.elapsedMillis(t0 + 99_000))
        assertEquals(1, state.interruptionCount)
        val resumed = TimerEngine.togglePause(state, t0 + 100_000) as PauseResult.Ok
        assertEquals(5_000L, resumed.state.elapsedMillis(t0 + 100_000))
        assertEquals(6_000L, resumed.state.elapsedMillis(t0 + 101_000))
    }

    @Test
    fun `restore after background uses real elapsed time`() {
        val state = TimerEngine.start(TimerMode.COUNTDOWN, 60, t0, 0L)
        // 进程被杀后回到前台：单调时钟继续走，按真实流逝校正
        val restored = TimerEngine.tick(state, t0 + 3_600_000)
        assertEquals(TimerPhase.FINISHED, restored.phase)
    }

    @Test
    fun `changing wall clock does not affect duration`() {
        val state = TimerEngine.start(TimerMode.COUNTDOWN, 300, t0, 1_700_000_000_000L)
        val later = TimerEngine.tick(state, t0 + 120_000)
        assertEquals(120, later.elapsedSeconds(t0 + 120_000))
    }

    @Test
    fun `cross midnight defaults to end date and midnight mode uses start date`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = ZonedDateTime.of(2026, 3, 1, 23, 50, 0, 0, zone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 3, 2, 0, 20, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("2026-03-02", SessionAttribution.dateOf(start, end, false, zone).toString())
        assertEquals("2026-03-01", SessionAttribution.dateOf(start, end, true, zone).toString())
        assertTrue(SessionAttribution.isShifted(start, end, zone))
    }

    @Test
    fun `early morning session without crossing stays on the same day`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = ZonedDateTime.of(2026, 3, 2, 1, 30, 0, 0, zone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 3, 2, 2, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("2026-03-02", SessionAttribution.dateOf(start, end, true, zone).toString())
        assertEquals("2026-03-02", SessionAttribution.dateOf(start, end, false, zone).toString())
        assertTrue(!SessionAttribution.isShifted(start, end, zone))
    }

    @Test
    fun `timer clamps absurd durations`() {
        val state = TimerEngine.start(TimerMode.COUNTDOWN, 999_999, t0, 0L)
        assertEquals(10_800, state.plannedSeconds)
    }
}
