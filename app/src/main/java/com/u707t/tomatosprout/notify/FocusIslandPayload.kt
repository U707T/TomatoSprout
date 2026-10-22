package com.u707t.tomatosprout.notify

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 一次专注在「超级岛 / 焦点通知」里要显示的全部内容。
 *
 * 纯数据、无 Android 依赖，方便在 JVM 单测里直接断言生成的 JSON。
 */
internal data class FocusIslandInput(
    val title: String,
    val isBreak: Boolean,
    val paused: Boolean,
    val countUp: Boolean,
    val plannedSeconds: Int,
    val elapsedSeconds: Int,
    val remainingSeconds: Int,
    /** 倒计时运行中：结束时刻（墙钟毫秒，系统据此自己走秒）。 */
    val endEpochMillis: Long?,
    /** 正计时运行中：开始时刻（墙钟毫秒）。 */
    val startEpochMillis: Long?,
    val nowEpochMillis: Long,
    val accentColor: String = FocusIslandPayload.ACCENT,
)

/**
 * 澎湃 OS 超级岛 / 焦点通知的 `miui.focus.param` 载荷。
 *
 * 字段取自澎湃 OS 系统界面自己的焦点模板（`param_v2` + 内嵌 `param_island`），
 * 关键点是 [timerInfo]：只给系统一个「结束时刻 + 当前时刻」，
 * 由 SystemUI 自己走秒，本应用不需要每秒重发通知，进程被回收后岛上的秒数也不会停。
 *
 * 模板是厂商私有协议，字段名一旦对不上，系统只会忽略对应块（最差退回普通通知），
 * 所以这里保持「缺字段而不是错字段」的写法：不确定的键宁可不写。
 */
internal object FocusIslandPayload {

    /** 图片 key，必须与 `miui.focus.pics` Bundle 里的键一致。 */
    const val PIC_MAIN = "miui.focus.pic_tomato"

    /** 动作 key，必须与 `miui.focus.actions` Bundle 里的键一致。 */
    const val ACTION_TOGGLE = "miui.focus.action_toggle"
    const val ACTION_STOP = "miui.focus.action_stop"

    /** 与 App 主题里的番茄红一致。 */
    const val ACCENT = "#E0553F"

    /** 岛内进度条的刻度总量（系统按百分比读）。 */
    const val PROGRESS_MAX = 100

    /** 焦点通知协议版本，1 为当前模板。 */
    private const val PROTOCOL = 1

    /** 秒数显示上限，避免异常状态渲染出超长字符串。 */
    private const val MAX_CLOCK_SECONDS = 24 * 3600

    fun build(input: FocusIslandInput): JsonObject {
        val progress = progress(input)
        val timer = timerInfo(input)
        val status = statusLabel(input)
        val headline = headline(input)

        return buildJsonObject {
            put("protocol", PROTOCOL)
            put("scene", "timer")
            put("business", "pomodoro")
            put("ticker", headline)
            // 顶层 content 只放状态：秒数由 timerInfo 交给系统走，
            // 这里再写一遍时间就会在两次刷新之间变成过期文本
            put("content", status)
            put("enableFloat", true)
            put("updatable", true)
            // OS2 的状态栏焦点文字也要一份计时参数，没有它就只有一行静态文字
            timer?.let { info ->
                put("timerType", info.getValue("timerType"))
                put("timerWhen", info.getValue("timerWhen"))
                put("timerSystemCurrent", info.getValue("timerSystemCurrent"))
                info["timerTotal"]?.let { put("timerTotal", it) }
            }
            put("param_v2", paramV2(input, progress, status, headline, timer))
        }
    }

    /** 岛内/通知里的进度百分比，越界一律夹到 0..100。 */
    fun progress(input: FocusIslandInput): Int {
        if (input.plannedSeconds <= 0) return 0
        val elapsed = input.elapsedSeconds.coerceAtLeast(0)
        return ((elapsed * PROGRESS_MAX) / input.plannedSeconds).coerceIn(0, PROGRESS_MAX)
    }

    /** 大岛上的等宽数字：倒计时显示剩余，正计时显示已过去。 */
    fun displaySeconds(input: FocusIslandInput): Int =
        if (input.countUp) input.elapsedSeconds else input.remainingSeconds

    fun clock(input: FocusIslandInput): String = formatClock(displaySeconds(input))

    fun statusLabel(input: FocusIslandInput): String = when {
        input.paused -> "已暂停"
        input.isBreak -> "休息中"
        else -> "专注中"
    }

    /** `专注中 · 写代码`，长度按系统 ticker 的显示宽度裁一下。 */
    fun headline(input: FocusIslandInput): String {
        val status = statusLabel(input)
        val name = input.title.trim().ifBlank { if (input.isBreak) "休息" else "自由专注" }
        // 休息时标题本来就是「休息一下」，别再拼一次
        if (input.isBreak && (name == "休息" || name == "休息一下")) return status
        val short = if (name.length > 16) name.take(15) + "…" else name
        return "$status · $short"
    }

    /** `mm:ss` / `h:mm:ss`，与 App 内计时页保持一致。 */
    fun formatClock(totalSeconds: Int): String {
        val safe = totalSeconds.coerceIn(0, MAX_CLOCK_SECONDS)
        val h = safe / 3600
        val m = (safe % 3600) / 60
        val s = safe % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /**
     * 系统自己走秒所需的计时参数；暂停时为 null（宁可只显示静态文字，
     * 也不要让岛上的秒数在暂停后继续跳）。
     */
    fun timerInfo(input: FocusIslandInput): JsonObject? {
        if (input.paused) return null
        return if (input.countUp) {
            val start = input.startEpochMillis ?: return null
            buildJsonObject {
                put("timerType", 0) // 0 = 正计时
                put("timerWhen", start)
                put("timerSystemCurrent", input.nowEpochMillis)
            }
        } else {
            val end = input.endEpochMillis ?: return null
            buildJsonObject {
                put("timerType", -1) // -1 = 倒计时
                put("timerWhen", end)
                put("timerSystemCurrent", input.nowEpochMillis)
                put("timerTotal", input.plannedSeconds.coerceAtLeast(0) * 1000L)
            }
        }
    }

    private fun paramV2(
        input: FocusIslandInput,
        progress: Int,
        status: String,
        headline: String,
        timer: JsonObject?,
    ): JsonObject = buildJsonObject {
        put("protocol", PROTOCOL)
        put("scene", "timer")
        put("business", "pomodoro")
        put("ticker", headline)
        put("updatable", true)
        put("enableFloat", true)
        put("aodTitle", headline)
        put("aodPic", PIC_MAIN)
        put("highlightInfo", buildJsonObject {
            put("type", 1) // 1 = 时间类型，配合 timerInfo 显示大号数字
            put("subContent", if (input.paused) status else input.title.trim().ifBlank { status })
            put("colorSubContent", input.accentColor)
            put("picFunction", PIC_MAIN)
            timer?.let { put("timerInfo", it) }
        })
        put("hintInfo", buildJsonObject {
            put("type", 2) // 2 = 复杂提示行
            put("title", status)
            put("content", durationLabel(input))
        })
        put("actions", buildJsonArray {
            add(buildJsonObject { put("type", 0); put("action", ACTION_TOGGLE) })
            add(buildJsonObject { put("type", 0); put("action", ACTION_STOP) })
        })
        put("param_island", buildJsonObject {
            put("islandProperty", 1) // 1 = 信息展示型；0 只在 OS4 闪一次，2 是系统级交互
            put("islandOrder", true)
            put("expandedTime", 60)
            put("bigIslandArea", buildJsonObject {
                put("imageTextInfoLeft", buildJsonObject {
                    put("type", 1)
                    put("picInfo", buildJsonObject {
                        put("type", 1)
                        put("pic", PIC_MAIN)
                    })
                })
                if (timer != null) {
                    put("sameWidthDigitInfo", buildJsonObject { put("timerInfo", timer) })
                } else {
                    // 暂停：没有计时参数，用一行文字占住大岛，避免只剩一个图标
                    put("textInfo", buildJsonObject { put("title", status) })
                }
            })
            put("smallIslandArea", buildJsonObject {
                put("combinePicInfo", buildJsonObject {
                    put("picInfo", buildJsonObject {
                        put("type", 1)
                        put("pic", PIC_MAIN)
                    })
                    put("progressInfo", buildJsonObject {
                        put("progress", progress)
                        put("colorReach", input.accentColor)
                        put("colorUnReach", "#33FFFFFF")
                        put("isCCW", false)
                    })
                })
            })
        })
        put("islandFirstFloat", true)
    }

    private fun durationLabel(input: FocusIslandInput): String {
        if (input.countUp) return "正计时 · 已 ${clock(input)}"
        val minutes = (input.plannedSeconds / 60).coerceAtLeast(1)
        return "$minutes 分钟"
    }
}
