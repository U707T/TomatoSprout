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
 * 倒计时必须是 -1 + 结束时刻，暂停用 -2 冻结，首帧才浮现，进度必须夹在 0..100。
 */
class FocusIslandPayloadTest {

    private val now = 1_700_000_000_000L

    private fun countdown(
        elapsed: Int = 600,
        remaining: Int = 900,
        paused: Boolean = false,
        firstFrame: Boolean = false,
        title: String = "写周报",
        isBreak: Boolean = false,
    ) = FocusIslandInput(
        title = title,
        isBreak = isBreak,
        paused = paused,
        countUp = false,
        plannedSeconds = 1500,
        elapsedSeconds = elapsed,
        remainingSeconds = remaining,
        endEpochMillis = now + remaining * 1000L,
        startEpochMillis = null,
        nowEpochMillis = now,
        firstFrame = firstFrame,
    )

    private fun countUp(
        elapsed: Int = 125,
        remaining: Int = 1375,
        paused: Boolean = false,
    ) = FocusIslandInput(
        title = "自由专注",
        isBreak = false,
        paused = paused,
        countUp = true,
        plannedSeconds = 1500,
        elapsedSeconds = elapsed,
        remainingSeconds = remaining,
        endEpochMillis = null,
        startEpochMillis = now - elapsed * 1000L,
        nowEpochMillis = now,
    )

    @Test
    fun `payload is a single param_v2 with the fields hyperos actually reads`() {
        val json = FocusIslandPayload.build(countdown())
        val v2 = json.obj("param_v2")

        assertEquals(1, v2.int("protocol"))
        assertEquals("pomodoro", v2.str("business"))
        // 系统自己的焦点通知也带这个场景名；缺了会退回旧模板
        assertEquals(FocusIslandPayload.SCENE, v2.str("scene"))
        assertEquals(true, v2.getValue("updatable").jsonPrimitive.boolean)
        assertEquals(false, v2.getValue("filterWhenNoPermission").jsonPrimitive.boolean)
        assertEquals(true, v2.getValue("isShowNotification").jsonPrimitive.boolean)
        // 焦点通知展开态的主内容：没有 baseInfo，通知只能显示成普通样式
        val base = v2.obj("baseInfo")
        assertEquals(2, base.int("type"))
        // 四行结构：任务名 → 状态 → 读数 → 时长
        assertEquals("写周报", base.str("title"))
        assertEquals("专注中", base.str("subTitle"))
        assertEquals("剩余 15:00", base.str("content"))
        assertEquals("25 分钟", base.str("subContent"))
        // 状态栏与息屏图标
        assertEquals(FocusIslandPayload.PIC_MAIN, v2.str("tickerPic"))
        assertEquals(FocusIslandPayload.PIC_MAIN, v2.str("aodPic"))
        // 通知栏卡片也带系统自走的读数（系统不读这个键时只是忽略）
        assertEquals(-1, base.obj("timerInfo").int("timerType"))
        assertEquals(now + 900_000L, base.obj("timerInfo").long("timerWhen"))
    }

    @Test
    fun `island properties follow the official meanings`() {
        val island = FocusIslandPayload.build(countdown()).obj("param_v2").obj("param_island")
        // 1 = 信息展示为主；2 是充电/录音那种操作为主的场景
        assertEquals(1, island.int("islandProperty"))
        // 0 高 / 1 中 / 2 低：给 2 会连「点小岛展开」一起被限制
        assertEquals(1, island.int("islandPriority"))
        assertEquals(false, island.getValue("islandOrder").jsonPrimitive.boolean)
        // 与系统时钟倒计时一致：浮现后 60 秒收回胶囊
        assertEquals(60, island.int("expandedTime"))
        assertFalse(island.containsKey("dismissIsland"))
    }

    @Test
    fun `countdown carries an absolute end time so systemui ticks on its own`() {
        val v2 = FocusIslandPayload.build(countdown()).obj("param_v2")
        val timer = v2.obj("param_island", "bigIslandArea", "sameWidthDigitInfo", "timerInfo")

        assertEquals(-1, timer.int("timerType"))
        assertEquals(now + 900_000L, timer.long("timerWhen"))
        assertEquals(now, timer.long("timerSystemCurrent"))
        assertEquals(now, timer.long("timerCurrent"))
        assertEquals(1_500_000L, timer.long("timerTotal"))
        // 等宽数字同时给静态兜底
        assertEquals("15:00", v2.obj("param_island", "bigIslandArea", "sameWidthDigitInfo").str("digit"))
    }

    @Test
    fun `only the first frame may float the island`() {
        val first = FocusIslandPayload.build(countdown(firstFrame = true)).obj("param_v2")
        assertEquals(true, first.getValue("islandFirstFloat").jsonPrimitive.boolean)
        assertEquals(true, first.getValue("enableFloat").jsonPrimitive.boolean)

        // 后续更新（暂停/继续/每秒刷新）不再弹出，避免岛每 10 秒重新展开
        val update = FocusIslandPayload.build(countdown(firstFrame = false)).obj("param_v2")
        assertEquals(false, update.getValue("islandFirstFloat").jsonPrimitive.boolean)
        assertEquals(false, update.getValue("enableFloat").jsonPrimitive.boolean)
    }

    @Test
    fun `paused countdown freezes instead of dropping the timer`() {
        val v2 = FocusIslandPayload.build(countdown(paused = true)).obj("param_v2")
        val island = v2.obj("param_island")
        val digit = island.obj("bigIslandArea", "sameWidthDigitInfo")

        // 官方 -2 = 倒计时暂停；timerWhen - timerSystemCurrent 就是冻结的剩余时间
        assertEquals(-2, digit.obj("timerInfo").int("timerType"))
        assertEquals(now + 900_000L, digit.obj("timerInfo").long("timerWhen"))
        assertEquals(now, digit.obj("timerInfo").long("timerSystemCurrent"))
        assertEquals("15:00", digit.str("digit"))
        assertEquals("已暂停", digit.str("content"))
        // 大岛 A 区第一行是任务名、第二行是状态（4 个汉字宽，顺序不能反）
        val left = island.obj("bigIslandArea", "imageTextInfoLeft", "textInfo")
        assertEquals("写周报", left.str("title"))
        assertEquals("已暂停", left.str("content"))
    }

    @Test
    fun `count up uses start time and paused count up uses timer type 2`() {
        val running = FocusIslandPayload.build(countUp()).obj("param_v2")
            .obj("param_island", "bigIslandArea", "sameWidthDigitInfo", "timerInfo")
        assertEquals(1, running.int("timerType"))
        assertEquals(now - 125_000L, running.long("timerWhen"))
        assertEquals(0L, running.long("timerTotal"))

        val paused = FocusIslandPayload.build(countUp(paused = true)).obj("param_v2")
            .obj("param_island", "bigIslandArea", "sameWidthDigitInfo", "timerInfo")
        assertEquals(2, paused.int("timerType"))
    }

    @Test
    fun `progress is clamped and never divides by zero`() {
        assertEquals(40, FocusIslandPayload.progress(countdown()))
        assertEquals(100, FocusIslandPayload.progress(countdown(elapsed = 9_999, remaining = 0)))
        val zero = countdown().copy(plannedSeconds = 0, elapsedSeconds = 10, remainingSeconds = 0)
        assertEquals(0, FocusIslandPayload.progress(zero))
    }

    @Test
    fun `small island shows a progress ring while counting down`() {
        val small = FocusIslandPayload.build(countdown()).obj("param_v2")
            .obj("param_island", "smallIslandArea")
        val combine = small.obj("combinePicInfo")
        assertEquals(FocusIslandPayload.PIC_MAIN, combine.obj("picInfo").str("pic"))
        assertEquals(40, combine.obj("progressInfo").int("progress"))

        // 正计时没有计划进度，只放图标，避免画一个永远 0% 的环
        val countUpSmall = FocusIslandPayload.build(countUp()).obj("param_v2")
            .obj("param_island", "smallIslandArea")
        assertEquals(FocusIslandPayload.PIC_MAIN, countUpSmall.obj("picInfo").str("pic"))
        assertFalse(countUpSmall.containsKey("combinePicInfo"))
    }

    @Test
    fun `actions and picture keys match the bundles the controller sends`() {
        val v2 = FocusIslandPayload.build(countdown()).obj("param_v2")
        val actions = v2.getValue("actions").toString()
        assertTrue(actions.contains(FocusIslandPayload.ACTION_TOGGLE))
        assertTrue(actions.contains(FocusIslandPayload.ACTION_STOP))
        // 「延长 5 分钟」只在通知栏出现，岛内保持两个按钮不拥挤
        assertFalse(actions.contains(FocusIslandPayload.ACTION_EXTEND))

        val pic = v2.obj("param_island", "bigIslandArea", "imageTextInfoLeft", "picInfo")
        assertEquals(FocusIslandPayload.PIC_MAIN, pic.str("pic"))
        assertEquals(FocusIslandPayload.PIC_MAIN, pic.str("picDark"))
    }

    @Test
    fun `finished frame is a one shot without actions or timer`() {
        val v2 = FocusIslandPayload.build(
            countdown().copy(finished = true, firstFrame = true),
        ).obj("param_v2")

        assertTrue(v2.obj("baseInfo").str("title").startsWith("专注完成"))
        assertFalse(v2.containsKey("actions"))
        val digit = v2.obj("param_island", "bigIslandArea", "sameWidthDigitInfo")
        assertEquals("00:00", digit.str("digit"))
        assertFalse(digit.containsKey("timerInfo"))
        assertEquals(20, v2.obj("param_island").int("islandTimeout"))
        assertEquals(1, v2.int("timeout"))
        // 完成提示只闪一次：摘要态自己消失，不留在岛上
        assertEquals(true, v2.obj("param_island").getValue("dismissIsland").jsonPrimitive.boolean)
        // 卡片的计时读数也必须撤掉，否则通知栏会挂着一个永远 00:00 的计时器
        assertFalse(v2.obj("baseInfo").containsKey("timerInfo"))
    }

    @Test
    fun `break and long titles stay readable`() {
        val breakJson = FocusIslandPayload.build(countdown(isBreak = true, title = "休息一下")).obj("param_v2")
        assertEquals("休息中", breakJson.str("ticker"))
        // 强调色挂在 param_island 上（官方字段表里 highlightColor 属于岛属性）
        assertEquals(
            FocusIslandPayload.ACCENT_BREAK,
            breakJson.obj("param_island").str("highlightColor"),
        )
        assertEquals(FocusIslandPayload.ACCENT_BREAK, breakJson.obj("baseInfo").str("colorTitle"))
        assertTrue(breakJson.str("aodTitle").startsWith("休息中"))

        val long = FocusIslandPayload.build(
            countdown(title = "一个特别特别长的任务名称需要被截断掉"),
        ).obj("param_v2")
        assertTrue(long.str("ticker").length <= "专注中 · ".length + 16)
        assertTrue(long.str("ticker").endsWith("…"))

        val blank = FocusIslandPayload.build(countdown(title = "   ")).obj("param_v2")
        assertEquals("专注中 · 自由专注", blank.str("ticker"))
    }

    @Test
    fun `aod title gives minutes without ticking seconds`() {
        assertEquals("专注中 · 还剩 15 分钟", FocusIslandPayload.aodTitle(countdown()))
        assertEquals("已暂停 · 还剩 15 分钟", FocusIslandPayload.aodTitle(countdown(paused = true)))
        assertEquals("正计时 · 02:05", FocusIslandPayload.aodTitle(countUp()))
    }

    @Test
    fun `timeout and island timeout are separate units`() {
        val v2 = FocusIslandPayload.build(countdown()).obj("param_v2")
        // 焦点通知默认消失时间：分钟；900 秒剩余 + 5 分钟余量 → 20 分钟
        assertEquals(20, v2.int("timeout"))
        // 岛自动收起：秒；900 秒剩余 + 300 秒余量
        assertEquals(1200, v2.obj("param_island").int("islandTimeout"))
    }

    @Test
    fun `capsule text stays short and lets the system tick`() {
        // 运行中不写文案：胶囊里是系统计时器（14:59 / 00:10），这是最窄的形态
        assertNull(FocusIslandPayload.chipText(countdown()))
        assertNull(FocusIslandPayload.chipText(countUp()))
        assertNull(FocusIslandPayload.chipText(countdown(isBreak = true)))

        // 只有暂停 / 完成才给静态短文案
        assertEquals("已暂停", FocusIslandPayload.chipText(countdown(paused = true)))
        assertEquals(
            "已完成",
            FocusIslandPayload.chipText(countdown().copy(finished = true)),
        )

        // 超过 2 小时系统会退回显示标题，这时才给紧凑文案
        val long = countdown(remaining = 3 * 3600).copy(plannedSeconds = 3 * 3600)
        assertEquals("剩 180 分", FocusIslandPayload.chipText(long))
    }

    @Test
    fun `payload stays under the 3 KB system limit`() {
        val json = FocusIslandPayload.build(
            countdown(title = "一个特别特别长的任务名称需要被截断掉", firstFrame = true),
        )
        val bytes = json.toString().toByteArray(Charsets.UTF_8).size
        assertTrue("payload=$bytes", bytes < FocusIslandPayload.MAX_PAYLOAD_BYTES)
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
    fun `timerInfo is absent without an anchor`() {
        val finished = countdown().copy(finished = true)
        assertNull(FocusIslandPayload.timerInfo(finished))
        val noAnchor = countdown().copy(endEpochMillis = null)
        assertNull(FocusIslandPayload.timerInfo(noAnchor))
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
