package com.u707t.tomatosprout.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import com.u707t.tomatosprout.MainActivity
import com.u707t.tomatosprout.R
import com.u707t.tomatosprout.domain.TimerMode
import com.u707t.tomatosprout.domain.TimerPhase
import com.u707t.tomatosprout.domain.TimerState

/**
 * 把「进行中的专注」翻译成三种系统呈现：
 *
 * - [Surface.HYPER_OS]：澎湃 OS 焦点通知 / 超级岛（`miui.focus.*` extras）；
 * - [Surface.LIVE_UPDATE]：Android 16 起的进度型通知（`Notification.ProgressStyle` + 常驻提升）；
 * - [Surface.PLAIN]：自定义 RemoteViews 卡片，所有系统都能显示。
 *
 * 三者互斥：同一个通知既设 ProgressStyle 又设自定义布局时，系统只会挑一个渲染，
 * 所以按机型能力挑一条，挑不到就往下退，不做叠加。
 */
internal class FocusLiveNotification(private val context: Context) {

    enum class Surface { HYPER_OS, LIVE_UPDATE, PLAIN }

    data class Built(
        val notification: Notification,
        val surface: Surface,
        /** 内容指纹：指纹相同且未到重发间隔时，跳过本次 notify()。 */
        val key: String,
        /** 内容指纹相同时的最短重发间隔。 */
        val minIntervalMillis: Long,
    )

    /** 能力探测含 ContentResolver 调用，缓存一份，避免每 10 秒问一次系统。 */
    private var cachedSupport: HyperOsFocus.Support? = null
    private var cachedAtMono = 0L

    fun build(state: TimerState, nowMono: Long, title: String, richSurface: Boolean): Built {
        val running = state.phase == TimerPhase.RUNNING
        val paused = state.phase == TimerPhase.PAUSED
        val countUp = state.mode == TimerMode.COUNTUP
        val nowWall = System.currentTimeMillis()

        val input = FocusIslandInput(
            title = title,
            isBreak = state.isBreak,
            paused = paused,
            countUp = countUp,
            plannedSeconds = state.plannedSeconds,
            elapsedSeconds = state.elapsedSeconds(nowMono),
            remainingSeconds = (state.remainingMillis(nowMono) / 1000L).toInt(),
            endEpochMillis = if (running && !countUp) nowWall + state.remainingMillis(nowMono) else null,
            startEpochMillis = if (running && countUp) nowWall - state.elapsedMillis(nowMono) else null,
            nowEpochMillis = nowWall,
        )

        val surface = chooseSurface(richSurface)
        val clockText = FocusIslandPayload.clock(input)
        val builder = baseBuilder(state, input, clockText)
        when (surface) {
            Surface.HYPER_OS -> builder.addExtras(hypeOsExtras(input))
            Surface.LIVE_UPDATE -> applyLiveUpdate(builder, input, running)
            Surface.PLAIN -> {
                val views = remoteViews(input, clockText, paused)
                builder.setCustomContentView(views).setCustomBigContentView(views)
            }
        }

        // 普通卡片要逐秒走字；实时活动/超级岛由系统自己走秒，10 秒补一次进度即可
        val bucket = if (surface == Surface.PLAIN) 1 else 10
        val key = buildString {
            append(surface).append('|').append(state.phase).append('|').append(state.mode)
            append('|').append(state.isBreak).append('|').append(state.plannedSeconds)
            append('|').append(title).append('|').append(input.remainingSeconds / bucket)
        }
        return Built(
            notification = builder.build(),
            surface = surface,
            key = key,
            // 同内容重发的间隔：普通卡片要逐秒走字，实时活动一分钟补一次只作兜底
            minIntervalMillis = if (surface == Surface.PLAIN) 1_000L else 60_000L,
        )
    }

    private fun chooseSurface(richSurface: Boolean): Surface {
        if (!richSurface) return Surface.PLAIN
        val support = hyperOsSupport()
        if (support.usable) return Surface.HYPER_OS
        // 小米设备但「焦点通知」被关掉：自定义卡片比半吊子的系统样式更稳
        if (support.supportsFocus) return Surface.PLAIN
        if (Build.VERSION.SDK_INT >= 36 && promotionAllowed()) return Surface.LIVE_UPDATE
        return Surface.PLAIN
    }

    private fun hyperOsSupport(): HyperOsFocus.Support {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedSupport
        if (cached != null && now - cachedAtMono < SUPPORT_TTL_MILLIS) return cached
        val fresh = runCatching { HyperOsFocus.probe(context) }
            .getOrDefault(HyperOsFocus.Support(0, null))
        cachedSupport = fresh
        cachedAtMono = now
        return fresh
    }

    private fun promotionAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return runCatching {
            context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
        }.getOrDefault(true)
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
            .setColor(android.graphics.Color.parseColor(input.accentColor))
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

    private fun hypeOsExtras(input: FocusIslandInput): Bundle {
        val pics = Bundle().apply {
            putParcelable(
                FocusIslandPayload.PIC_MAIN,
                Icon.createWithResource(context, R.drawable.ic_notification),
            )
        }
        val actions = Bundle().apply {
            putParcelable(FocusIslandPayload.ACTION_TOGGLE, toggleAction(input.paused))
            putParcelable(FocusIslandPayload.ACTION_STOP, stopAction())
        }
        return Bundle().apply {
            putString(EXTRA_FOCUS_PARAM, FocusIslandPayload.build(input).toString())
            putBundle(EXTRA_FOCUS_PICS, pics)
            putBundle(EXTRA_FOCUS_ACTIONS, actions)
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
            setOnClickPendingIntent(R.id.focus_toggle, focusIntent(FocusActionReceiver.ACTION_TOGGLE, 1))
            setOnClickPendingIntent(R.id.focus_stop, focusIntent(FocusActionReceiver.ACTION_STOP, 2))
        }

    private fun toggleAction(paused: Boolean): Notification.Action = Notification.Action.Builder(
        Icon.createWithResource(context, if (paused) R.drawable.ic_play else R.drawable.ic_pause),
        if (paused) "继续" else "暂停",
        focusIntent(FocusActionReceiver.ACTION_TOGGLE, 1),
    ).build()

    private fun stopAction(): Notification.Action = Notification.Action.Builder(
        Icon.createWithResource(context, R.drawable.ic_stop),
        "结束",
        focusIntent(FocusActionReceiver.ACTION_STOP, 2),
    ).build()

    /**
     * 直接用 Activity PendingIntent：Android 12+ 限制通知 trampoline（广播/服务里拉起 Activity），
     * 用广播再 startActivity 会被系统拦截。
     */
    private fun focusIntent(action: String, request: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE, MainActivity.NAV_FOCUS)
            putExtra(MainActivity.EXTRA_FOCUS_ACTION, action)
        }
        return PendingIntent.getActivity(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val SUPPORT_TTL_MILLIS = 60_000L

        /** 澎湃 OS 焦点通知主参数的 extras key。 */
        const val EXTRA_FOCUS_PARAM = "miui.focus.param"

        /** 图片 Bundle 的 extras key，键与 JSON 里的 `pic` 字符串一一对应。 */
        const val EXTRA_FOCUS_PICS = "miui.focus.pics"

        /** 动作 Bundle 的 extras key，键与 JSON 里的 `action` 字符串一一对应。 */
        const val EXTRA_FOCUS_ACTIONS = "miui.focus.actions"
    }
}
