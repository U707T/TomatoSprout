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
    /** 单调递增的序号，系统据此判断哪一帧是最新状态。 */
    val sequence: Long = 0L,
    val accentColor: String = FocusIslandPayload.ACCENT,
)

/**
 * 澎湃 OS 超级岛 / 焦点通知的 `miui.focus.param` 载荷。
 *
 * 写法对齐多个**已在真机上出岛**的第三方实现，而不是照抄系统 App 的样例：
 *
 * - 只保留 `param_v2` 一层（系统 App 的样例里还有一堆顶层平铺字段，第三方用不到）；
 * - 必带 `sequence`（否则系统把后续更新当成旧帧丢掉）；
 * - 必带 `filterWhenNoPermission: false`（否则没拿到焦点通知授权时整帧被过滤）；
 * - `islandProperty = 2`（交互型岛），`islandTimeout` 按剩余时长给；
 * - `timerInfo` 同时写 `timerSystemCurrent` 与 `timerCurrent`（官方字段表与样例不一致）；
 * - 正计时 `timerType = 1`，倒计时 `-1`，暂停不给计时参数。
 *
 * 模板是厂商私有协议，字段名对不上时系统只会忽略对应块（最差退回普通通知），
 * 所以这里保持「缺字段而不是错字段」的写法。
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

    /** 业务标识；系统只做透传，不认也无副作用。 */
    private const val BUSINESS = "pomodoro"

    /** 岛最长驻留 12 小时，防止异常状态把岛钉死。 */
    private const val MAX_ISLAND_TIMEOUT = 43_200

    private const val MAX_CLOCK_SECONDS = 24 * 3600

    fun build(input: FocusIslandInput): JsonObject {
        val timer = timerInfo(input)
        val status = statusLabel(input)
        val headline = headline(input)

        return buildJsonObject {
            // 顶层留一份最小镜像：OS2 的焦点通知按顶层字段取 ticker / 计时参数
            put("protocol", PROTOCOL)
            put("ticker", headline)
            put("content", status)
            timer?.let { info ->
                put("timerType", info.getValue("timerType"))
                put("timerWhen", info.getValue("timerWhen"))
                put("timerSystemCurrent", info.getValue("timerSystemCurrent"))
                info["timerTotal"]?.let { put("timerTotal", it) }
            }
            put("param_v2", paramV2(input, status, headline, timer))
        }
    }

    /** 岛内/通知里的进度百分比，越界一律夹到 0..100。 */
    fun progress(input: FocusIslandInput): Int {
        if (input.plannedSeconds <= 0) return 0
        val elapsed = input.elapsedSeconds.coerceAtLeast(0)
        return ((elapsed * PROGRESS_MAX) / input.plannedSeconds).coerceIn(0, PROGRESS_MAX)
    }

    /** 岛上的等宽数字：倒计时显示剩余，正计时显示已过去。 */
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
                put("timerType", 1) // 1 = 正计时（与系统录音机 / 训练计时一致）
                put("timerWhen", start)
                put("timerTotal", 0L)
                put("timerSystemCurrent", input.nowEpochMillis)
                put("timerCurrent", input.nowEpochMillis)
            }
        } else {
            val end = input.endEpochMillis ?: return null
            buildJsonObject {
                put("timerType", -1) // -1 = 倒计时
                put("timerWhen", end)
                put("timerTotal", input.plannedSeconds.coerceAtLeast(0) * 1000L)
                put("timerSystemCurrent", input.nowEpochMillis)
                put("timerCurrent", input.nowEpochMillis)
            }
        }
    }

    /** 岛自动收起的时间（秒）：比剩余时长多留 5 分钟，避免计时结束时岛还挂着。 */
    fun islandTimeoutSeconds(input: FocusIslandInput): Int {
        if (input.paused || input.countUp) return MAX_ISLAND_TIMEOUT
        return (input.remainingSeconds + 300).coerceIn(60, MAX_ISLAND_TIMEOUT)
    }

    private fun paramV2(
        input: FocusIslandInput,
        status: String,
        headline: String,
        timer: JsonObject?,
    ): JsonObject = buildJsonObject {
        put("protocol", PROTOCOL)
        put("business", BUSINESS)
        put("updatable", true)
        put("enableFloat", true)
        put("islandFirstFloat", true)
        // 没有焦点通知授权时不要把整帧过滤掉：能显示多少显示多少
        put("filterWhenNoPermission", false)
        put("sequence", input.sequence)
        put("ticker", headline)
        put("aodTitle", headline)
        put("aodPic", PIC_MAIN)
        put("highlightInfo", buildJsonObject {
            put("type", 1) // 1 = 时间类型，配合 timerInfo 显示大号数字
            put("subContent", if (input.paused) status else input.title.trim().ifBlank { status })
            put("colorSubContent", input.accentColor)
            put("colorSubContentDark", input.accentColor)
            put("picFunction", PIC_MAIN)
            timer?.let { put("timerInfo", it) }
        })
        put("hintInfo", buildJsonObject {
            put("type", 2) // 2 = 复杂提示行
            put("title", status)
            put("content", durationLabel(input))
            put("colorContent", "#99000000")
            put("colorContentDark", "#80FFFFFF")
        })
        put("actions", buildJsonArray {
            add(action(ACTION_TOGGLE, input.accentColor))
            add(action(ACTION_STOP, "#8A8078"))
        })
        put("param_island", buildJsonObject {
            put("islandProperty", 2) // 2 = 交互操作型岛；0 只在 OS4 闪一次
            put("islandTimeout", islandTimeoutSeconds(input))
            put("bigIslandArea", buildJsonObject {
                put("imageTextInfoLeft", buildJsonObject {
                    put("type", 1)
                    put("picInfo", picInfo())
                    put("textInfo", buildJsonObject {
                        put("title", status)
                        put("showHighlightColor", true)
                    })
                })
                put("sameWidthDigitInfo", buildJsonObject {
                    if (timer != null) {
                        put("timerInfo", timer)
                    } else {
                        // 暂停：没有计时参数，用静态数字占住大岛，避免只留一个图标
                        put("digit", clock(input))
                    }
                    put("showHighlightColor", true)
                })
            })
            put("smallIslandArea", buildJsonObject { put("picInfo", picInfo()) })
        })
    }

    private fun action(key: String, color: String): JsonObject = buildJsonObject {
        put("type", 0)
        put("action", key)
        put("actionTitleColor", "#FFFFFF")
        put("actionTitleColorDark", "#FFFFFF")
        put("actionBgColor", color)
        put("actionBgColorDark", color)
    }

    private fun picInfo(): JsonObject = buildJsonObject {
        put("type", 1)
        put("pic", PIC_MAIN)
        put("picDark", PIC_MAIN)
    }

    private fun durationLabel(input: FocusIslandInput): String {
        if (input.countUp) return "正计时 · 已 ${clock(input)}"
        val minutes = (input.plannedSeconds / 60).coerceAtLeast(1)
        return "$minutes 分钟"
    }
}
