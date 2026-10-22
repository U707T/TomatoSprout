package com.u707t.tomatosprout.domain

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** 一次专注的结束方式。结算规则全部收敛在这里，UI 只负责选择原因。 */
enum class FocusEndReason {
    /** 倒计时自然归零。 */
    COUNTDOWN_FINISHED,

    /** 正计时/倒计时由用户确认「记录这次专注」。 */
    COUNTUP_FINISHED,

    /** 用户中途手动停止，未达计划时长。 */
    MANUAL_STOP,

    /** 用户明确放弃，或不足 30 秒且设置要求忽略。 */
    DISCARDED,
}

data class FocusSettlementInput(
    val task: Task?,
    val mode: String,
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val startedAt: Long,
    val endedAt: Long,
    val interruptionCount: Int = 0,
    val strict: Boolean = false,
    /** true = 跨天时强制归属开始日；false = 归属结束日。 */
    val midnightMode: Boolean = false,
    /** 正向计时不足 30 秒时是否直接丢弃。 */
    val ignoreShortFocus: Boolean = true,
    val reason: FocusEndReason,
    /** 手动停止时的中断原因；完成/丢弃时忽略。 */
    val interruptReason: InterruptReason? = null,
)

data class FocusSettlementResult(
    val session: FocusSession?,
    val discarded: Boolean,
    val discardReason: String? = null,
    /** 本次是否新写入/累加了打卡（已存在同一天打卡时为 false）。 */
    val checkInWritten: Boolean = false,
    /** 本次专注对应的打卡 id（包含已经存在的当天打卡）。 */
    val checkInId: String? = null,
    val streak: Int = 0,
    val newBadges: List<Badge> = emptyList(),
    val attributedDate: LocalDate,
    val interrupted: Boolean = false,
    val interruptReason: String? = null,
)

/**
 * 专注结算（v1.6 唯一入口）：
 *
 * 1. 倒计时自然结束 → 写 FocusSession + 自动写 CheckIn + 更新连续天数 + 任务实际番茄数（仅番茄钟）；
 * 2. 正向计时结束（≥30s）→ 由 UI 弹窗确认后调用，写 FocusSession + CheckIn + 实际番茄数；
 * 3. 正向计时 <30s → ignoreShortFocus 时直接丢弃，不写任何记录；
 * 4. 手动放弃/中途停止 → 只写一条 outcome=interrupted 的 FocusSession，
 *    开启严格模式则彻底丢弃；两种情况都不触发打卡；
 * 5. 跨午夜 → 默认归属结束日；midnightMode 开启时归属开始日。
 */
class FocusSessionUseCase @Inject constructor(
    private val focus: FocusRepository,
    private val tasks: TaskRepository,
    private val checkIns: CheckInUseCase,
    private val badges: BadgeRefresher,
    private val clock: AppClock = SystemAppClock,
) {
    suspend fun settle(input: FocusSettlementInput): FocusSettlementResult {
        val zone = clock.zone()
        val attributedDate = SessionAttribution.dateOf(
            startedAtEpoch = input.startedAt,
            endedAtEpoch = input.endedAt,
            midnightMode = input.midnightMode,
            zone = zone,
        )
        val shifted = SessionAttribution.isShifted(input.startedAt, input.endedAt, zone)

        fun discarded(reason: String) = FocusSettlementResult(
            session = null,
            discarded = true,
            discardReason = reason,
            attributedDate = attributedDate,
        )

        if (input.actualSeconds <= 0) return discarded("时长为 0")
        if (input.reason == FocusEndReason.DISCARDED) return discarded("已放弃本次专注")
        if (input.reason == FocusEndReason.MANUAL_STOP && input.strict) {
            return discarded("严格模式下提前结束，本次专注已作废")
        }
        if (input.reason == FocusEndReason.COUNTUP_FINISHED &&
            input.actualSeconds < 30 &&
            input.ignoreShortFocus
        ) {
            return discarded("不足 30 秒，按设置忽略")
        }

        val completed = input.reason == FocusEndReason.COUNTDOWN_FINISHED ||
            input.reason == FocusEndReason.COUNTUP_FINISHED
        val outcome = if (completed) "completed" else "interrupted"
        val task = input.task?.takeIf { it.deletedAt == null }

        // 倒计时最多算一个计划番茄：进程被杀后回到 App 时，elapsedRealtime 会把
        // 后台挂起的时长一起算进来（可能几小时），不夹住就会写出一个巨大的专注记录。
        val planned = input.plannedSeconds.coerceAtLeast(0)
        val actualSeconds = if (
            input.reason == FocusEndReason.COUNTDOWN_FINISHED && planned > 0
        ) {
            input.actualSeconds.coerceIn(1, planned)
        } else {
            input.actualSeconds.coerceAtLeast(0)
        }
        val endedAt = if (actualSeconds < input.actualSeconds) {
            (input.startedAt + actualSeconds * 1000L).coerceAtMost(input.endedAt)
        } else {
            input.endedAt
        }

        // 只有「完成」才触发打卡；中断记录永远不写 CheckIn
        val checkIn = if (completed && task != null) {
            checkIns.recordFromFocus(task, attributedDate, endedAt)
        } else {
            null
        }

        val session = FocusSession(
            id = UUID.randomUUID().toString(),
            taskId = input.task?.id,
            mode = input.mode,
            plannedSeconds = planned,
            actualSeconds = actualSeconds,
            startedAt = input.startedAt,
            endedAt = endedAt,
            isMidnightShifted = shifted,
            interruptionCount = input.interruptionCount.coerceAtLeast(0),
            outcome = outcome,
            attributedDate = attributedDate,
            checkInId = checkIn?.checkInId,
            interruptReason = input.interruptReason?.name?.takeIf { !completed },
            updatedAt = endedAt,
        )
        focus.insert(session)

        if (completed && task != null && input.mode == "pomodoro") {
            // 番茄ToDo 的账本：只有「倒计时番茄」计入实际番茄数，正计时只算专注时长。
            // 与 StatsCalculator.totalPomos / 徽章口径保持一致，避免同一张卡片出现两套番茄数。
            tasks.incrementCompletedPomos(task.id, 1, endedAt)
        }

        // 专注时长/番茄数徽章依赖 session 落库，重新刷新一次；与打卡徽章去重合并
        val refreshed = runCatching { badges.refresh() }.getOrDefault(emptyList())
        val merged = (checkIn?.newBadges.orEmpty() + refreshed).distinctBy { it.key }

        return FocusSettlementResult(
            session = session,
            discarded = false,
            checkInWritten = checkIn?.written == true,
            checkInId = checkIn?.checkInId,
            streak = checkIn?.streak ?: 0,
            newBadges = merged,
            attributedDate = attributedDate,
            interrupted = !completed,
            interruptReason = session.interruptReason,
        )
    }
}
