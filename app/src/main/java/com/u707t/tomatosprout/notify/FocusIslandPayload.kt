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
    /** 倒计时：结束时刻（墙钟毫秒，系统据此自己走秒）；暂停时也要给，用于冻结读数。 */
    val endEpochMillis: Long?,
    /** 正计时：开始时刻（墙钟毫秒）；暂停时也要给，用于冻结读数。 */
    val startEpochMillis: Long?,
    val nowEpochMillis: Long,
    /** 单调递增的序号，系统据此判断哪一帧是最新状态。 */
    val sequence: Long = 0L,
    /** 本次专注的第一帧：只有第一帧才允许「浮现」，后续更新不再重复弹出。 */
    val firstFrame: Boolean = false,
    /** 已完成的一次性提示帧：不给计时器，岛短暂出现后自动消失。 */
    val finished: Boolean = false,
)

/**
 * 澎湃 OS 超级岛 / 焦点通知的 `miui.focus.param` 载荷（FocusTemplateV3）。
 *
 * 字段以三处证据对齐，不自己发明字段：
 *
 * 1. 小米澎湃 OS 开发者平台《开发指南》（pId=2131，2026-01-29）与《常见 Q&A》（pId=2146）；
 * 2. 系统时钟「倒计时」与录音机「正计时」的焦点通知 dump（`scene = timer / recorder`）；
 * 3. 反向阅读 `miui.systemui.dynamicisland` 的插件实现（模板选择顺序、排序规则）。
 *
 * 与 v1.10.x 相比的关键差异：
 *
 * - 只发 `param_v2` 一层（官方示例与第三方可用实现一致），外层不再平铺；
 * - `sequence` 必带，`filterWhenNoPermission = false`，没授权时退化成普通通知而不是被整帧过滤；
 * - `islandFirstFloat` / `enableFloat` 只在首帧为 true：系统首次加入岛读前者、后续更新读后者；
 * - `islandProperty = 1`（信息展示）、`islandPriority = 1`（0 高 / 1 中 / 2 低，2 还会挡住点击展开）；
 * - `expandedTime = 60`：岛浮现后 60 秒自动收回胶囊，与系统时钟倒计时一致；
 * - 暂停不删 `timerInfo`，改用官方的 `timerType = -2 / 2` 冻结读数；
 * - 大岛用模板 6（`imageTextInfoLeft` + `sameWidthDigitInfo`），小岛用模板 2（`combinePicInfo` 进度环）；
 * - `timeout`（分钟）与 `islandTimeout`（秒）分开给，两者不能互相替代。
 *
 * 模板是厂商私有协议：字段对不上时系统只会忽略对应块（最差退回普通通知），
 * 所以这里保持「缺字段而不是错字段」的写法。
 */
internal object FocusIslandPayload {

    /** 图片 key，必须与 `miui.focus.pics` Bundle 里的键一致。 */
    const val PIC_MAIN = "miui.focus.pic_tomato"

    /** 动作 key，必须与 `miui.focus.actions` Bundle 里的键一致。 */
    const val ACTION_TOGGLE = "miui.focus.action_toggle"
    const val ACTION_STOP = "miui.focus.action_stop"

    /** 延长 5 分钟；只进 `miui.focus.actions`，岛内保持两个按钮不拥挤。 */
    const val ACTION_EXTEND = "miui.focus.action_extend"

    /** 与 App 主题里的番茄红一致。 */
    const val ACCENT = "#E0553F"

    /** 休息态用低饱和的叶绿，和专注态区分。 */
    const val ACCENT_BREAK = "#4F8A6B"

    /** 完成态用更稳的深绿。 */
    const val ACCENT_DONE = "#3E8E5A"

    /** 次级文字：状态、说明行。 */
    const val SUBTLE = "#8A8078"

    /** 次级文字（深色模式）。 */
    const val SUBTLE_DARK = "#B9AFA8"

    /** 岛内进度条的刻度总量（系统按百分比读）。 */
    const val PROGRESS_MAX = 100

    /** 系统自己认识的模板场景名。 */
    const val SCENE = "template_v2"

    /** 焦点通知载荷上限；超过 3 KB 系统会整帧丢弃。 */
    const val MAX_PAYLOAD_BYTES = 3072

    /**
     * 岛展开后自动收起回胶囊的秒数。系统时钟的倒计时给的就是 60：
     * 少了会看不清数字，大了会让岛长期占着屏幕。
     */
    const val EXPANDED_SECONDS = 60

    /**
     * 岛排序优先级：0 高 / 1 中 / 2 低。
     *
     * 插件按「数值小的先占大岛」排序；2 还会让点击小岛无法展开成大岛，
     * 所以默认给 1，既不会被音乐/通话轻易挤掉，也不抢系统自己的岛。
     */
    const val ISLAND_PRIORITY = 1

    /** 焦点通知协议版本，1 为当前模板。 */
    private const val PROTOCOL = 1

    /** 业务标识；系统只做透传，不认也无副作用。 */
    private const val BUSINESS = "pomodoro"

    /** 岛最长驻留 12 小时，防止异常状态把岛钉死。 */
    private const val MAX_ISLAND_TIMEOUT = 43_200

    private const val MAX_CLOCK_SECONDS = 24 * 3600

    /** 单帧最多 16 个中文；超过就截断，避免系统自己裁切。 */
    private const val MAX_TITLE = 16
    private const val MAX_DETAIL = 40

    fun build(input: FocusIslandInput): JsonObject = buildJsonObject {
        put("param_v2", paramV2(input))
    }

    /** 专注 / 休息 / 完成三套强调色。 */
    fun accentFor(isBreak: Boolean, finished: Boolean = false): String = when {
        finished && isBreak -> ACCENT_BREAK
        finished -> ACCENT_DONE
        isBreak -> ACCENT_BREAK
        else -> ACCENT
    }

    /** 岛内/通知里的进度百分比，越界一律夹到 0..100。 */
    fun progress(input: FocusIslandInput): Int {
        if (input.plannedSeconds <= 0) return 0
        val elapsed = input.elapsedSeconds.coerceAtLeast(0)
        return ((elapsed * PROGRESS_MAX) / input.plannedSeconds).coerceIn(0, PROGRESS_MAX)
    }

    /** 岛上的等宽数字：倒计时显示剩余，正计时显示已过去，已结束固定 00:00。 */
    fun displaySeconds(input: FocusIslandInput): Int = when {
        input.finished -> 0
        input.countUp -> input.elapsedSeconds
        else -> input.remainingSeconds
    }

    fun clock(input: FocusIslandInput): String = formatClock(displaySeconds(input))

    fun statusLabel(input: FocusIslandInput): String = when {
        input.finished -> if (input.isBreak) "休息结束" else "专注完成"
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
        val short = if (name.length > MAX_TITLE) name.take(MAX_TITLE - 1) + "…" else name
        return "$status · $short"
    }

    /**
     * 息屏文案：给出「还剩几分钟」这类不会在两次刷新之间过期太快的读数。
     * 秒数交给系统计时器，这里不放秒，避免刷新时出现跳秒。
     */
    fun aodTitle(input: FocusIslandInput): String = when {
        input.finished -> statusLabel(input)
        input.paused -> "已暂停 · " + remainingLabel(input)
        input.isBreak -> "休息中 · " + remainingLabel(input)
        input.countUp -> "正计时 · " + clock(input)
        else -> "专注中 · " + remainingLabel(input)
    }

    /** 卡片标题：进行中是任务名，结束时是「专注完成 · 任务名」。 */
    fun cardTitle(input: FocusIslandInput): String =
        if (input.finished) headline(input) else shortName(input)

    /** 卡片正文：剩余 / 已专注的读数，不含秒表（秒表交给系统计时器）。 */
    fun reading(input: FocusIslandInput): String = when {
        input.finished -> "${minutesOf(input)} 分钟"
        input.countUp -> "已专注 " + clock(input)
        else -> "剩余 " + clock(input)
    }

    /**
     * 状态栏胶囊里的短文案；**返回 null 表示交给系统的计时器自己显示**。
     *
     * 胶囊宽度是有限的：写「已专注 00:10」这种 8 个字的文案，岛就会被撑得很长（真机实拍）。
     * 所以运行中一律不写文字，让系统按 `setWhen + chronometer` 显示 `14:59` / `00:10`，
     * 既是实时读数，也最短。倒计时超过 2 小时时系统会退回显示完整标题、把胶囊撑破，
     * 那种情况才给紧凑文案「剩 180 分」。
     */
    fun chipText(input: FocusIslandInput): String? = when {
        input.finished -> "已完成"
        input.paused -> "已暂停"
        // 正计时：系统从开始时刻往上走，比任何静态文案都准
        input.countUp -> null
        input.remainingSeconds <= CHIP_COUNTDOWN_SECONDS -> null
        else -> "剩 " + ((input.remainingSeconds + 59) / 60) + " 分"
    }

    /** 剩余这么久以内，胶囊交给系统的实时倒计时（超过 2 小时胶囊会溢出）。 */
    const val CHIP_COUNTDOWN_SECONDS = 2 * 3600

    /** 计划时长，例如 `25 分钟`。 */
    fun plannedLabel(input: FocusIslandInput): String = "${minutesOf(input)} 分钟"

    /** 焦点通知展开态的正文：任务名 + 计划/已专注时长。 */
    fun detail(input: FocusIslandInput): String {
        val name = input.title.trim().ifBlank { if (input.isBreak) "休息" else "自由专注" }
        val text = when {
            input.finished -> "$name · ${minutesOf(input)} 分钟"
            input.paused -> "$name · 已专注 ${clock(input)}"
            input.countUp -> "$name · 已专注 ${clock(input)}"
            input.isBreak -> "休息 ${minutesOf(input)} 分钟"
            else -> "$name · ${minutesOf(input)} 分钟"
        }
        return if (text.length > MAX_DETAIL) text.take(MAX_DETAIL - 1) + "…" else text
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
     * 系统自己走秒所需的计时参数。
     *
     * 官方/系统样例里的 timerType：-1 倒计时运行、-2 倒计时暂停、1 正计时运行、2 正计时暂停
     * （插件里 ±1/±3 运行、其余按 `timerSystemCurrent` 冻结）。
     * 暂停时保留 timerInfo 并把 `timerSystemCurrent` 固定在当前时刻，读数会停在
     * `timerWhen - timerSystemCurrent`，不会继续跳。
     */
    fun timerInfo(input: FocusIslandInput): JsonObject? {
        if (input.finished) return null
        return if (input.countUp) {
            val start = input.startEpochMillis ?: return null
            buildJsonObject {
                put("timerType", if (input.paused) 2 else 1)
                put("timerWhen", start)
                put("timerTotal", 0L)
                put("timerSystemCurrent", input.nowEpochMillis)
                put("timerCurrent", input.nowEpochMillis)
            }
        } else {
            val end = input.endEpochMillis ?: return null
            buildJsonObject {
                put("timerType", if (input.paused) -2 else -1)
                put("timerWhen", end)
                put("timerTotal", input.plannedSeconds.coerceAtLeast(0) * 1000L)
                put("timerSystemCurrent", input.nowEpochMillis)
                put("timerCurrent", input.nowEpochMillis)
            }
        }
    }

    /** 岛自动收起的时间（秒）：比剩余时长多留一点，避免计时结束时岛还挂着。 */
    fun islandTimeoutSeconds(input: FocusIslandInput): Int = when {
        input.finished -> 20
        // 暂停 / 正计时没有确定的结束点，给上限，结算时由 App 撤掉
        input.paused || input.countUp -> MAX_ISLAND_TIMEOUT
        input.isBreak -> (input.remainingSeconds + 60).coerceIn(60, MAX_ISLAND_TIMEOUT)
        else -> (input.remainingSeconds + 300).coerceIn(60, MAX_ISLAND_TIMEOUT)
    }

    /** 焦点通知自身的默认消失时间（分钟）；岛另有 `islandTimeout`（秒）。 */
    fun timeoutMinutes(input: FocusIslandInput): Int = when {
        input.finished -> 1
        input.paused || input.countUp -> 720
        // 剩余分钟数向上取整，再留 5 分钟余量：900 秒 → 15 + 5 = 20 分钟
        else -> ((input.remainingSeconds + 59) / 60 + 5).coerceIn(5, 720)
    }

    private fun paramV2(input: FocusIslandInput): JsonObject = buildJsonObject {
        val accent = accentFor(input.isBreak, input.finished)
        put("protocol", PROTOCOL)
        put("business", BUSINESS)
        put("scene", SCENE)
        put("ticker", headline(input))
        put("tickerPic", PIC_MAIN)
        put("tickerPicDark", PIC_MAIN)
        put("aodTitle", aodTitle(input))
        put("aodPic", PIC_MAIN)
        put("updatable", true)
        // 用户划掉通知后不要再自己弹回来
        put("reopen", "close")
        // 首帧才浮现；后续更新只改内容，避免岛每 10 秒重新展开
        put("enableFloat", input.firstFrame)
        put("islandFirstFloat", input.firstFrame)
        // 没有焦点通知授权时不要把整帧过滤掉：能显示多少显示多少
        put("filterWhenNoPermission", false)
        put("isShowNotification", true)
        put("sequence", input.sequence)
        put("timeout", timeoutMinutes(input))
        // 旧模板（无 param_v2 读取能力的版本）直接读这两项
        put("content", detail(input))
        put("baseInfo", baseInfo(input, accent))
        put("picInfo", picInfo())
        // 完成提示是一次性的，不给按钮；否则 JSON 引用了 actions Bundle 里不存在的 key
        if (!input.finished) {
            put("actions", buildJsonArray {
                add(action(ACTION_TOGGLE, accent))
                add(action(ACTION_STOP, "#8A8078"))
            })
        }
        put("param_island", island(input, accent))
    }

    /**
     * 通知内容数据：标题（状态 · 任务）+ 正文（任务 · 时长）。
     *
     * `timerInfo` 一并塞进来：模板选择器读的是「首个区域模块」的 `timerInfo`，
     * 多给一份能让通知栏卡片也带上系统自走的读数；系统不读这个键时只是忽略。
     */
    private fun baseInfo(input: FocusIslandInput, accent: String): JsonObject = buildJsonObject {
        // 官方 BaseInfo 有四个文本槽：标题 / 副标题 / 正文 / 补充。
        // 之前只用了两个，卡片看起来像一行流水账；这里按「任务名 → 状态 → 读数 → 时长」排
        put("type", 2)
        put("title", cardTitle(input))
        put("subTitle", statusLabel(input))
        put("content", reading(input))
        put("subContent", plannedLabel(input))
        put("colorTitle", accent)
        put("colorTitleDark", accent)
        put("colorSubTitle", SUBTLE)
        put("colorSubTitleDark", SUBTLE_DARK)
        put("colorContent", SUBTLE)
        put("colorContentDark", SUBTLE_DARK)
        put("colorSubContent", SUBTLE)
        put("colorSubContentDark", SUBTLE_DARK)
        put("showDivider", false)
        timerInfo(input)?.let { put("timerInfo", it) }
    }

    private fun island(input: FocusIslandInput, accent: String): JsonObject = buildJsonObject {
        // 1 = 信息展示为主（官方默认）；2 是充电 / 录音那种「操作为主」的场景
        put("islandProperty", 1)
        put("islandPriority", ISLAND_PRIORITY)
        // 更新时不抢排序，避免用户手动收起的岛又跳出来
        put("islandOrder", false)
        put("expandedTime", EXPANDED_SECONDS)
        put("islandTimeout", islandTimeoutSeconds(input))
        put("highlightColor", accent)
        // 完成提示只闪一次：摘要态直接消失，不留在岛上
        if (input.finished) put("dismissIsland", true)
        put("bigIslandArea", buildJsonObject {
            // 官方大岛模板 6：A 区图文组件 1 + B 区等宽数字文本组件
            put("imageTextInfoLeft", buildJsonObject {
                put("type", 1)
                put("picInfo", picInfo())
                put("textInfo", buildJsonObject {
                    // A 区只有约 4 个汉字宽：第一行放任务名（用户最关心的），第二行放状态
                    put("title", if (input.finished) statusLabel(input) else shortName(input))
                    put("content", statusLabel(input))
                    put("showHighlightColor", true)
                    put("narrowFont", false)
                })
            })
            put("sameWidthDigitInfo", buildJsonObject {
                // digit 是静态兜底，timerInfo 让系统自己走秒；两个都给，系统读哪个都能对上
                put("digit", clock(input))
                timerInfo(input)?.let { put("timerInfo", it) }
                put("content", digitCaption(input))
                put("showHighlightColor", true)
                put("narrowFont", false)
            })
        })
        put("smallIslandArea", buildJsonObject {
            val progress = progress(input)
            if (!input.countUp && !input.finished && progress > 0) {
                // 官方小岛模板 2：图标 + 进度环
                put("combinePicInfo", buildJsonObject {
                    put("picInfo", picInfo())
                    put("progressInfo", buildJsonObject {
                        put("progress", progress)
                        put("colorReach", accent)
                        put("colorUnReach", "#33FFFFFF")
                        put("isCCW", false)
                    })
                })
            } else {
                put("picInfo", picInfo())
            }
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

    private fun digitCaption(input: FocusIslandInput): String = when {
        input.finished -> "已完成"
        input.paused -> "已暂停"
        input.countUp -> "已专注"
        input.isBreak -> "休息"
        else -> "剩余"
    }

    private fun shortName(input: FocusIslandInput): String {
        val name = input.title.trim().ifBlank { if (input.isBreak) "休息" else "自由专注" }
        // 大岛 A 区建议不超过 4 个中文字，超长会先缩字再裁切
        return if (name.length > 8) name.take(7) + "…" else name
    }

    private fun remainingLabel(input: FocusIslandInput): String {
        val seconds = input.remainingSeconds.coerceAtLeast(0)
        val minutes = (seconds + 59) / 60
        return if (minutes >= 1) "还剩 $minutes 分钟" else "还剩 $seconds 秒"
    }

    private fun minutesOf(input: FocusIslandInput): Int =
        (input.plannedSeconds.coerceAtLeast(0) / 60).coerceAtLeast(1)
}
