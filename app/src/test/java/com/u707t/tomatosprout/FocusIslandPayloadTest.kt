package com.u707t.tomatosprout

import com.u707t.tomatosprout.notify.FocusIslandInput
import com.u707t.tomatosprout.notify.FocusIslandPayload
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 超级岛 / 焦点通知载荷的纯 JVM 断言。
 *
 * 模板字段是厂商私有协议，这里锁的是「我们承诺给系统的语义」：
 * 倒计时必须是 -1 + 结束时刻，暂停不许再走秒，进度必须夹在 0..100。
 */
class FocusIslandPayloadTest {

    private val now = 1_700_000_000_000L

    private fun countdown(
        elapsed: Int = 600,
        remaining: Int = 900,
        paused: Boolean = false,
    ) = FocusIslandInput(
        title = "写周报",
        isBreak = false,
        paused = paused,
        countUp = false,
        plannedSeconds = 1500,
        elapsedSeconds = elapsed,
        remainingSeconds = remaining,
        endEpochMillis = if (paused) null else now + remaining * 1000L,
        startEpochMillis = null,
        nowEpochMillis = now,
    )

    @Test
    fun `countdown carries an absolute end time so systemui ticks on its own`() {
        val json = FocusIslandPayload.build(countdown())

        assertEquals(1, json.int("protocol"))
        assertEquals("timer", json.str("scene"))
        assertEquals(-1, json.int("timerType"))
        assertEquals(now + 900_000L, json.long("timerWhen"))
        assertEquals(now, json.long("timerSystemCurrent"))
        assertEquals(1_500_000L, json.long("timerTotal"))

        val timer = json.obj("param_v2", "highlightInfo", "timerInfo")
        assertEquals(-1, timer.int("timerType"))
        assertEquals(now + 900_000L, timer.long("timerWhen"))

        val islandTimer = json.obj("param_v2", "param_island", "bigIslandArea", "sameWidthDigitInfo", "timerInfo")
        assertEquals(now + 900_000L, islandTimer.long("timerWhen"))
    }

    @Test
    fun `progress and small island ring use the same clamped percentage`() {
        val json = FocusIslandPayload.build(countdown())
        val ring = json.obj(
            "param_v2", "param_island", "smallIslandArea", "combinePicInfo", "progressInfo",
        )
        assertEquals(40, ring.int("progress"))
        assertEquals("#E0553F", ring.str("colorReach"))

        // 超时也不会画出 100 以上
        val overflowing = countdown(elapsed = 9_999, remaining = 0)
        val overflow = FocusIslandPayload.build(overflowing)
        assertEquals(100, FocusIslandPayload.progress(overflowing))
        assertEquals(100, overflow.obj(
            "param_v2", "param_island", "smallIslandArea", "combinePicInfo", "progressInfo",
        ).int("progress"))

        // 时长为 0（理论上不会出现）不除零
        val zero = FocusIslandInput(
            title = "x", isBreak = false, paused = false, countUp = false,
            plannedSeconds = 0, elapsedSeconds = 10, remainingSeconds = 0,
            endEpochMillis = now, startEpochMillis = null, nowEpochMillis = now,
        )
        assertEquals(0, FocusIslandPayload.progress(zero))
    }

    @Test
    fun `paused payload stops ticking but keeps the ring`() {
        val json = FocusIslandPayload.build(countdown(paused = true))

        assertFalse(json.containsKey("timerType"))
        assertFalse(json.containsKey("timerWhen"))
        val highlight = json.obj("param_v2", "highlightInfo")
        assertFalse(highlight.containsKey("timerInfo"))
        assertEquals("已暂停", highlight.str("subContent"))
        assertFalse(json.obj("param_v2", "param_island", "bigIslandArea").containsKey("sameWidthDigitInfo"))
        assertEquals(
            "已暂停",
            json.obj("param_v2", "param_island", "bigIslandArea").obj("textInfo").str("title"),
        )
        assertEquals(
            40,
            json.obj("param_v2", "param_island", "smallIslandArea", "combinePicInfo", "progressInfo").int("progress"),
        )
    }

    @Test
    fun `count up uses start time and never sends a total`() {
        val input = FocusIslandInput(
            title = "自由专注",
            isBreak = false,
            paused = false,
            countUp = true,
            plannedSeconds = 1500,
            elapsedSeconds = 125,
            remainingSeconds = 1375,
            endEpochMillis = null,
            startEpochMillis = now - 125_000L,
            nowEpochMillis = now,
        )
        val json = FocusIslandPayload.build(input)

        assertEquals(0, json.int("timerType"))
        assertEquals(now - 125_000L, json.long("timerWhen"))
        assertFalse(json.containsKey("timerTotal"))
        assertFalse(json.obj("param_v2", "highlightInfo", "timerInfo").containsKey("timerTotal"))
        assertEquals("专注中 · 自由专注", json.obj("param_v2").str("aodTitle"))
        assertEquals("专注中", json.str("content"))
    }

    @Test
    fun `actions and picture keys match the bundles the controller sends`() {
        val json = FocusIslandPayload.build(countdown())
        val actions = json.obj("param_v2").getValue("actions").toString()
        assertTrue(actions.contains(FocusIslandPayload.ACTION_TOGGLE))
        assertTrue(actions.contains(FocusIslandPayload.ACTION_STOP))

        val pic = json.obj("param_v2", "param_island", "bigIslandArea", "imageTextInfoLeft", "picInfo")
        assertEquals(FocusIslandPayload.PIC_MAIN, pic.str("pic"))
        assertEquals(FocusIslandPayload.PIC_MAIN, json.obj("param_v2").str("aodPic"))
    }

    @Test
    fun `break and long titles stay readable`() {
        val breakJson = FocusIslandPayload.build(countdown().copy(isBreak = true))
        assertTrue(breakJson.str("ticker").startsWith("休息中"))

        val long = FocusIslandPayload.build(
            countdown().copy(title = "一个特别特别长的任务名称需要被截断掉"),
        )
        val ticker = long.str("ticker")
        assertTrue(ticker.length <= "专注中 · ".length + 16)
        assertTrue(ticker.endsWith("…"))

        val blank = FocusIslandPayload.build(countdown().copy(title = "   "))
        assertEquals("专注中 · 自由专注", blank.str("ticker"))
    }

    @Test
    fun `clock formatting matches the in app timer`() {
        assertEquals("00:00", FocusIslandPayload.formatClock(0))
        assertEquals("01:05", FocusIslandPayload.formatClock(65))
        assertEquals("59:59", FocusIslandPayload.formatClock(3599))
        assertEquals("1:01:01", FocusIslandPayload.formatClock(3661))
        assertEquals("24:00:00", FocusIslandPayload.formatClock(999_999))
    }

    @Test
    fun `timerInfo is absent when the clock is not running`() {
        val finished = countdown().copy(endEpochMillis = null)
        assertNull(FocusIslandPayload.timerInfo(finished))
        assertEquals(now + 900_000L, FocusIslandPayload.timerInfo(countdown())!!.long("timerWhen"))
    }

    // --- helpers -------------------------------------------------------

    private fun JsonObject.obj(vararg path: String): JsonObject {
        var current = this
        path.forEach { key -> current = current.getValue(key).jsonObject }
        return current
    }

    private fun JsonObject.str(vararg path: String): String = leaf(*path).jsonPrimitive.content

    private fun JsonObject.int(vararg path: String): Int = leaf(*path).jsonPrimitive.int

    private fun JsonObject.long(vararg path: String): Long = leaf(*path).jsonPrimitive.long

    private fun JsonObject.leaf(vararg path: String) = path.dropLast(1).fold(this) { acc, key ->
        acc.getValue(key).jsonObject
    }.getValue(path.last())
}
