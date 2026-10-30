package com.u707t.tomatosprout.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
            LiveSurface.LIVE_UPDATE -> applyLiveUpdate(builder, input, running)
            LiveSurface.PLAIN -> {
                val views = remoteViews(input, clockText, paused)
                builder.setCustomContentView(views).setCustomBigContentView(views)
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
        // 计时器已经交给系统走秒，正文只写「何时结束 / 何时开始」这类不会过期的信息
        val content = when {
            paused -> "已暂停 · 剩余 $clockText"
            countUp -> input.startEpochMillis?.let { "开始于 ${wallClock(it)}" } ?: "正计时中"
            else -> input.endEpochMillis?.let { "${wallClock(it)} 结束" } ?: "倒计时中"
        }
        val builder = Notification.Builder(context, NotificationChannels.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(FocusIslandPayload.headline(input))
            .setContentText(content)
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

    private fun applyLiveUpdate(builder: Notification.Builder, input: FocusIslandInput, running: Boolean) {
        if (Build.VERSION.SDK_INT < 36) return
        val segment = Notification.ProgressStyle.Segment(FocusIslandPayload.PROGRESS_MAX)
            .setColor(android.graphics.Color.parseColor(FocusIslandPayload.accentFor(input.isBreak)))
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(listOf(segment))
            .setProgress(FocusIslandPayload.progress(input))
        builder.setStyle(style)
        // 暂停时没有计时器可用，用一句短文案占住状态栏胶囊
        if (!running) builder.setShortCriticalText("已暂停")
        requestPromotion(builder)
    }

    /** 提升为「常驻实时活动」需要系统允许；用户关掉时只是不提升，通知照常显示。 */
    @SuppressLint("NewApi")
    private fun requestPromotion(builder: Notification.Builder) {
        // setRequestPromotedOngoing 是 Android 16 QPR1（SDK_INT_FULL 3600001）才有的接口
        if (Build.VERSION.SDK_INT_FULL < Build.VERSION_CODES_FULL.BAKLAVA_1) return
        runCatching { builder.setRequestPromotedOngoing(true) }
    }

    private fun hypeOsExtras(input: FocusIslandInput, withActions: Boolean = true): Bundle {
        val pics = Bundle().apply {
            putParcelable(
                FocusIslandPayload.PIC_MAIN,
                Icon.createWithResource(context, R.drawable.ic_notification),
            )
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

    /** 通知正文里的墙钟时间，例如 `15:24`。 */
    private fun wallClock(epochMillis: Long): String = runCatching {
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("--:--")

    private fun remoteViews(input: FocusIslandInput, clockText: String, paused: Boolean): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_focus).apply {
            setTextViewText(R.id.focus_title, FocusIslandPayload.headline(input))
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
    }
}
