package com.u707t.tomatosprout.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import com.u707t.tomatosprout.MainActivity
import com.u707t.tomatosprout.R
import com.u707t.tomatosprout.domain.TimerMode
import com.u707t.tomatosprout.domain.TimerPhase
import com.u707t.tomatosprout.domain.TimerState

/**
 * 把「进行中的专注」翻译成三种系统呈现：
 *
 * - [LiveSurface.HYPER_OS]：澎湃 OS 焦点通知 / 超级岛（`miui.focus.*` extras）；
 * - [LiveSurface.LIVE_UPDATE]：Android 16 起的进度型通知（`Notification.ProgressStyle` + 常驻提升）；
 * - [LiveSurface.PLAIN]：自定义 RemoteViews 卡片，所有系统都能显示。
 *
 * 三者互斥：同一个通知既设 ProgressStyle 又设自定义布局时，系统只会挑一个渲染，
 * 所以按机型能力挑一条，挑不到就往下退，不做叠加。
 */
internal class FocusLiveNotification(private val context: Context) {

    data class Built(
        val notification: Notification,
        val surface: LiveSurface,
        /** 内容指纹：指纹相同且未到重发间隔时，跳过本次 notify()。 */
        val key: String,
        /** 内容指纹相同时的最短重发间隔。 */
        val minIntervalMillis: Long,
        /** 本次档位的判定原因，写进自检面板。 */
        val reason: String,
    )

    /** 岛与通知共用的一枚彩色图标（256px，纸雕插画裁成圆形）。 */
    private val islandIcon: Icon by lazy { Icon.createWithResource(context, R.drawable.ic_island) }

    /** 通知卡片的大图标，系统会自己按密度缩放。 */
    private val islandBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.ic_island)
    }

    fun build(
        state: TimerState,
        nowMono: Long,
        title: String,
        richSurface: Boolean,
        forceFocus: Boolean,
        firstFrame: Boolean,
    ): Built {
        val running = state.phase == TimerPhase.RUNNING
        val paused = state.phase == TimerPhase.PAUSED
        val countUp = state.mode == TimerMode.COUNTUP
        val nowWall = System.currentTimeMillis()
        val remaining = (state.remainingMillis(nowMono) / 1000L).toInt()
        val elapsed = state.elapsedSeconds(nowMono)

        val input = FocusIslandInput(
            sequence = SEQUENCE.incrementAndGet(),
            title = title,
            isBreak = state.isBreak,
            paused = paused,
            countUp = countUp,
            plannedSeconds = state.plannedSeconds,
            elapsedSeconds = elapsed,
            remainingSeconds = remaining,
            // 暂停时也要给绝对时刻：timerType=-2/2 会让系统按 timerSystemCurrent 冻结读数
            endEpochMillis = if (countUp) null else nowWall + remaining * 1000L,
            startEpochMillis = if (countUp) nowWall - state.elapsedMillis(nowMono) else null,
            nowEpochMillis = nowWall,
            firstFrame = firstFrame,
        )

        val decision = LiveSurfacePolicy.decide(context, richSurface, forceFocus)
        val clockText = FocusIslandPayload.clock(input)
        val builder = baseBuilder(state, input, clockText)
        when (decision.surface) {
            LiveSurface.HYPER_OS -> builder.addExtras(hypeOsExtras(input))
            LiveSurface.LIVE_UPDATE -> {
                applyProgressStyle(builder, input)
                applyPromotion(builder, input)
            }
            LiveSurface.PLAIN -> {
                val views = remoteViews(input, clockText, paused)
                builder.setCustomContentView(views).setCustomBigContentView(views)
                builder.setProgress(FocusIslandPayload.PROGRESS_MAX, FocusIslandPayload.progress(input), false)
            }
        }

        // 普通卡片要逐秒走字；实时活动/超级岛由系统自己走秒，10 秒补一次进度即可
        val bucket = if (decision.surface == LiveSurface.PLAIN) 1 else 10
        val key = buildString {
            append(decision.surface).append('|').append(state.phase).append('|').append(state.mode)
            append('|').append(state.isBreak).append('|').append(state.plannedSeconds)
            append('|').append(title).append('|').append(input.remainingSeconds / bucket)
        }
        return Built(
            notification = builder.build(),
            surface = decision.surface,
            key = key,
            // 同内容重发的间隔：普通卡片要逐秒走字，实时活动一分钟补一次只作兜底
            minIntervalMillis = if (decision.surface == LiveSurface.PLAIN) 1_000L else 60_000L,
            reason = decision.reason,
        )
    }

    /**
     * 专注完成 / 休息结束的一次性提示。
     *
     * 用独立的通知 id，不覆盖正在进行的专注；岛只驻留 20 秒，通知本身也会自动消失。
     * 小米机型带焦点 extras（首帧浮现），其余系统就是一条普通通知。
     */
    fun buildFinished(title: String, isBreak: Boolean, minutes: Int): Notification {
        val safeMinutes = minutes.coerceAtLeast(1)
        val input = FocusIslandInput(
            sequence = SEQUENCE.incrementAndGet(),
            title = title,
            isBreak = isBreak,
            paused = false,
            countUp = false,
            plannedSeconds = safeMinutes * 60,
            elapsedSeconds = safeMinutes * 60,
            remainingSeconds = 0,
            endEpochMillis = null,
            startEpochMillis = null,
            nowEpochMillis = System.currentTimeMillis(),
            firstFrame = true,
            finished = true,
        )
        val builder = Notification.Builder(context, NotificationChannels.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(islandBitmap)
            .setColor(Color.parseColor(FocusIslandPayload.accentFor(isBreak, finished = true)))
            .setContentTitle(if (isBreak) "休息结束" else "专注完成 · $safeMinutes 分钟")
            .setContentText(if (isBreak) "准备下一段专注吧" else "$title 已记录")
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(FINISHED_TIMEOUT_MILLIS)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(ContentIntents.openApp(context, MainActivity.NAV_FOCUS))
        if (HyperOsFocus.isXiaomiDevice()) {
            builder.addExtras(hypeOsExtras(input, withActions = false))
        }
        return builder.build()
    }

    private fun baseBuilder(
        state: TimerState,
        input: FocusIslandInput,
        clockText: String,
    ): Notification.Builder {
        val running = state.phase == TimerPhase.RUNNING
        val paused = state.phase == TimerPhase.PAUSED
        val countUp = state.mode == TimerMode.COUNTUP
        val accent = FocusIslandPayload.accentFor(input.isBreak, input.finished)
        val builder = Notification.Builder(context, NotificationChannels.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            // 卡片左侧的大图标：番茄芽的纸雕插画，比一个白色小 glyph 好看得多
            .setLargeIcon(islandBitmap)
            // 强调色会染小图标、应用名与进度条
            .setColor(Color.parseColor(accent))
            // 标题放任务名（用户最关心的），状态进 subText，读数进正文
            .setContentTitle(FocusIslandPayload.cardTitle(input))
            .setSubText(FocusIslandPayload.statusLabel(input))
            .setContentText(bodyText(input, clockText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(ContentIntents.openApp(context, MainActivity.NAV_FOCUS))
            .addAction(toggleAction(paused))
            .addAction(stopAction())
        // 倒计时才谈得上延长：正计时没有终点，已结束的更不该再续
        if (state.mode == TimerMode.COUNTDOWN && state.isActive) {
            builder.addAction(extendAction())
        }

        // 系统计时器：把「结束时刻」交给 SystemUI，进程被回收后秒数也不会停
        val anchor = when {
            !running -> null
            countUp -> input.startEpochMillis
            else -> input.endEpochMillis
        }
        if (anchor != null) {
            builder.setWhen(anchor)
                .setUsesChronometer(true)
                .setChronometerCountDown(!countUp)
                .setShowWhen(true)
        } else {
            builder.setUsesChronometer(false).setShowWhen(false)
        }
        return builder
    }

    /**
     * 官方允许实时更新使用的样式之一：进度样式。
     *
     * 给卡片一条带强调色的进度条，比纯文字更像「正在进行的事」；
     * 进度按已专注百分比算，暂停时停在原地。
     */
    private fun applyProgressStyle(builder: Notification.Builder, input: FocusIslandInput) {
        if (Build.VERSION.SDK_INT < 36) return
        runCatching {
            val accent = Color.parseColor(
                FocusIslandPayload.accentFor(input.isBreak, input.finished),
            )
            val segment = Notification.ProgressStyle.Segment(FocusIslandPayload.PROGRESS_MAX)
                .setColor(accent)
            val style = Notification.ProgressStyle()
                .setStyledByProgress(true)
                .setProgressSegments(listOf(segment))
                .setProgress(FocusIslandPayload.progress(input))
            builder.setStyle(style)
        }
    }

    /**
     * 请求把这条通知提升为「实时更新」——澎湃 OS 4 的超级岛就是按这条通道渲染的。
     *
     * 两个键直接写进 extras，而不是调 `setShortCriticalText()` / `setRequestPromotedOngoing()`：
     * 后者是 Android 16 QPR1（SDK_INT_FULL ≥ 3600001）才有的方法，而这两个 key 在 36.0 的
     * SystemUI 里就已经被读取。参考实现 Live Clock 2.3.0 正是这么写的（它没有任何
     * `miui.focus` 字样，却在澎湃 OS 4 上能出岛）。
     *
     * 官方对实时更新的硬性要求（缺一条就不会被提升）：清单里声明
     * `POST_PROMOTED_NOTIFICATIONS`、`setOngoing(true)`、有 `contentTitle`、
     * 没有 `setCustomContentView`、不是分组摘要、频道不是 `IMPORTANCE_MIN`。
     * 本档位全部满足；自检面板会把「系统是否真的提升了」也显示出来。
     */
    private fun applyPromotion(builder: Notification.Builder, input: FocusIslandInput) {
        if (Build.VERSION.SDK_INT < 36) return
        runCatching {
            val extras = builder.extras
            chipText(input)?.let { extras.putCharSequence(EXTRA_SHORT_CRITICAL_TEXT, it) }
            extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
        }
    }

    /**
     * 状态栏胶囊 / 岛上的短文案；返回 null 表示「交给系统自己显示」。
     *
     * 倒计时剩余 2 小时内不写文案：系统会按 `setWhen + chronometer` 在胶囊里走实时倒计时，
     * 比每 10 秒才刷新一次的静态文字直观得多（这也是系统时钟倒计时的做法）。
     * 超过 2 小时系统会退回显示完整标题、把胶囊撑破，所以那时改用紧凑文案。
     * 暂停 / 正计时 / 完成一律给静态文案。
     */
    private fun chipText(input: FocusIslandInput): CharSequence? {
        val minutes = (input.remainingSeconds.coerceAtLeast(0) + 59) / 60
        return when {
            input.finished -> "已完成"
            input.paused -> "已暂停"
            input.countUp -> "已专注 " + FocusIslandPayload.formatClock(input.elapsedSeconds)
            input.isBreak -> "休息 $minutes 分"
            input.remainingSeconds <= CHIP_COUNTDOWN_SECONDS -> null
            else -> "剩 $minutes 分"
        }
    }

    private fun hypeOsExtras(input: FocusIslandInput, withActions: Boolean = true): Bundle {
        val pics = Bundle().apply {
            putParcelable(FocusIslandPayload.PIC_MAIN, islandIcon)
        }
        return Bundle().apply {
            putString(EXTRA_FOCUS_PARAM, FocusIslandPayload.build(input).toString())
            putBundle(EXTRA_FOCUS_PICS, pics)
            if (withActions) {
                putBundle(
                    EXTRA_FOCUS_ACTIONS,
                    Bundle().apply {
                        putParcelable(FocusIslandPayload.ACTION_TOGGLE, toggleAction(input.paused))
                        putParcelable(FocusIslandPayload.ACTION_STOP, stopAction())
                        putParcelable(FocusIslandPayload.ACTION_EXTEND, extendAction())
                    },
                )
            }
        }
    }

    /**
     * 通知正文：一句话说清「还剩多久 / 什么时候结束」。
     *
     * 秒表读数由系统的 chronometer 与岛上的 timerInfo 自己走，正文只补它们给不了的信息
     * （结束时刻、已专注多久），所以 10 秒才刷新一次也不会显得卡。
     */
    private fun bodyText(input: FocusIslandInput, clockText: String): String = when {
        input.finished -> FocusIslandPayload.reading(input) + " · 已记录"
        input.paused -> "已暂停 · 剩余 $clockText"
        input.countUp -> input.startEpochMillis
            ?.let { "已专注 $clockText · ${wallClock(it)} 开始" }
            ?: "已专注 $clockText"
        else -> input.endEpochMillis
            ?.let { "剩余 $clockText · ${wallClock(it)} 结束" }
            ?: "剩余 $clockText"
    }

    /** 通知正文里的墙钟时间，例如 `15:24`。 */
    private fun wallClock(epochMillis: Long): String = runCatching {
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("--:--")

    private fun remoteViews(input: FocusIslandInput, clockText: String, paused: Boolean): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_focus).apply {
            setTextViewText(R.id.focus_title, FocusIslandPayload.cardTitle(input))
            setTextViewText(R.id.focus_time, clockText)
            setProgressBar(R.id.focus_progress, 100, FocusIslandPayload.progress(input), false)
            setImageViewResource(
                R.id.focus_toggle,
                if (paused) R.drawable.ic_play else R.drawable.ic_pause,
            )
            setOnClickPendingIntent(R.id.focus_toggle, actionIntent(FocusActionReceiver.ACTION_TOGGLE, 1))
            setOnClickPendingIntent(R.id.focus_stop, actionIntent(FocusActionReceiver.ACTION_STOP, 2))
        }

    private fun toggleAction(paused: Boolean): Notification.Action = Notification.Action.Builder(
        Icon.createWithResource(context, if (paused) R.drawable.ic_play else R.drawable.ic_pause),
        if (paused) "继续" else "暂停",
        actionIntent(FocusActionReceiver.ACTION_TOGGLE, 1),
    ).build()

    private fun stopAction(): Notification.Action = Notification.Action.Builder(
        Icon.createWithResource(context, R.drawable.ic_stop),
        "结束",
        actionIntent(FocusActionReceiver.ACTION_STOP, 2),
    ).build()

    /** 通知栏的「+5 分钟」：走广播，不打断当前 App。 */
    private fun extendAction(): Notification.Action = Notification.Action.Builder(
        Icon.createWithResource(context, R.drawable.ic_add),
        "延长 5 分钟",
        actionIntent(FocusActionReceiver.ACTION_EXTEND, 3),
    ).build()

    /**
     * 按钮一律走广播：官方对 `miui.focus.actions` 的要求就是广播 PendingIntent，
     * 并要带 `FLAG_RECEIVER_FOREGROUND` 才能在前台广播队列里及时送达。
     *
     * 不用 Activity PendingIntent：Android 12+ 的 trampoline 限制会拦掉
     * 「通知按钮里拉起 Activity」，而且暂停一下番茄就跳回 App 也很突兀。
     */
    private fun actionIntent(action: String, request: Int): PendingIntent {
        val intent = Intent(context, FocusActionReceiver::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        /** 帧序号：系统据此判断哪一帧是最新状态，必须严格递增。 */
        val SEQUENCE = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())

        /** 完成提示在通知栏/岛上停留的时长。 */
        const val FINISHED_TIMEOUT_MILLIS = 20_000L

        /** 澎湃 OS 焦点通知主参数的 extras key。 */
        const val EXTRA_FOCUS_PARAM = "miui.focus.param"

        /** 图片 Bundle 的 extras key，键与 JSON 里的 `pic` 字符串一一对应。 */
        const val EXTRA_FOCUS_PICS = "miui.focus.pics"

        /** 动作 Bundle 的 extras key，键与 JSON 里的 `action` 字符串一一对应。 */
        const val EXTRA_FOCUS_ACTIONS = "miui.focus.actions"

        /** 实时更新：状态栏胶囊里的短文案（Android 16 起 SystemUI 读取）。 */
        const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"

        /** 实时更新：请求把通知提升为 promoted ongoing。 */
        const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

        /** 这段剩余时间内不给胶囊文案，交给系统的实时倒计时（超过 2 小时胶囊会溢出）。 */
        const val CHIP_COUNTDOWN_SECONDS = 2 * 3600
    }
}
