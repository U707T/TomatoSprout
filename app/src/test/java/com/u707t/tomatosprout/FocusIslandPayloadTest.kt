package com.u707t.tomatosprout

import com.u707t.tomatosprout.notify.FocusIslandInput
import com.u707t.tomatosprout.notify.FocusIslandPayload
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
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
        // 第三方帧不带 scene：认不出的场景会让系统整帧不渲染
        assertFalse(json.containsKey("scene"))
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
    fun `progress is clamped and never divides by zero`() {
        assertEquals(40, FocusIslandPayload.progress(countdown()))
        // 超时也不会画出 100 以上
        assertEquals(100, FocusIslandPayload.progress(countdown(elapsed = 9_999, remaining = 0)))
        // 时长为 0（理论上不会出现）不除零
        val zero = FocusIslandInput(
            title = "x", isBreak = false, paused = false, countUp = false,
            plannedSeconds = 0, elapsedSeconds = 10, remainingSeconds = 0,
            endEpochMillis = now, startEpochMillis = null, nowEpochMillis = now,
        )
        assertEquals(0, FocusIslandPayload.progress(zero))
    }

    @Test
    fun `island frame keeps the fields that make hyperos accept it`() {
        val json = FocusIslandPayload.build(countdown().copy(sequence = 42L))
        val island = json.obj("param_v2", "param_island")
        // 2 = 交互型岛；0 在 OS4 上只闪一次
        assertEquals(2, island.int("islandProperty"))
        assertEquals(1200, island.int("islandTimeout")) // 900 秒剩余 + 300 秒余量
        // 没有焦点通知授权时也不该被整帧过滤
        assertEquals(false, json.obj("param_v2").getValue("filterWhenNoPermission").jsonPrimitive.boolean)
        assertEquals(42L, json.obj("param_v2").long("sequence"))
        // 小岛只放图标：combinePicInfo 在部分版本会渲染成空白
        assertEquals(
            FocusIslandPayload.PIC_MAIN,
            island.obj("smallIslandArea", "picInfo").str("pic"),
        )
        // 系统计时器两个字段名都要给（官方字段表与样例不一致）
        val timer = island.obj("bigIslandArea", "sameWidthDigitInfo", "timerInfo")
        assertEquals(now, timer.long("timerCurrent"))
        assertEquals(now, timer.long("timerSystemCurrent"))
    }

    @Test
    fun `paused payload stops ticking and falls back to static digits`() {
        val json = FocusIslandPayload.build(countdown(paused = true))

        assertFalse(json.containsKey("timerType"))
        assertFalse(json.containsKey("timerWhen"))
        val highlight = json.obj("param_v2", "highlightInfo")
        assertFalse(highlight.containsKey("timerInfo"))
        assertEquals("已暂停", highlight.str("subContent"))

        val digit = json.obj("param_v2", "param_island", "bigIslandArea", "sameWidthDigitInfo")
        assertFalse(digit.containsKey("timerInfo"))
        assertEquals("15:00", digit.str("digit"))
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

        assertEquals(1, json.int("timerType"))
        assertEquals(now - 125_000L, json.long("timerWhen"))
        // 正计时给 0，字段要在但不算总量
        assertEquals(0L, json.long("timerTotal"))
        assertEquals(0L, json.obj("param_v2", "highlightInfo", "timerInfo").long("timerTotal"))
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
        assertEquals(
            "休息中",
            FocusIslandPayload.headline(countdown().copy(isBreak = true, title = "休息一下")),
        )

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
