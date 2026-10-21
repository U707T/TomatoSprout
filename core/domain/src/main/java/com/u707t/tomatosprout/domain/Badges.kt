package com.u707t.tomatosprout.domain

enum class BadgeMetric { CURRENT_STREAK, LONGEST_STREAK, TOTAL_CHECK_INS, FOCUS_SECONDS, TOTAL_POMOS, PERFECT_DAYS }

data class BadgeDef(
    val key: String,
    val name: String,
    val description: String,
    val metric: BadgeMetric,
    val threshold: Long,
)

data class BadgeStats(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalCheckIns: Int = 0,
    val totalFocusSeconds: Long = 0,
    val totalPomos: Int = 0,
    val perfectDays: Int = 0,
)

object BadgeCatalog {
    val defs: List<BadgeDef> = listOf(
        BadgeDef("streak_7", "七日萌芽", "连续打卡 7 天", BadgeMetric.LONGEST_STREAK, 7),
        BadgeDef("streak_30", "月度常青", "连续打卡 30 天", BadgeMetric.LONGEST_STREAK, 30),
        BadgeDef("streak_100", "百日成林", "连续打卡 100 天", BadgeMetric.LONGEST_STREAK, 100),
        BadgeDef("checkins_100", "百次行动", "累计打卡 100 次", BadgeMetric.TOTAL_CHECK_INS, 100),
        BadgeDef("focus_100h", "百小时专注", "累计专注 100 小时", BadgeMetric.FOCUS_SECONDS, 100L * 3600L),
        BadgeDef("pomos_50", "番茄五十", "完成 50 个番茄", BadgeMetric.TOTAL_POMOS, 50),
        BadgeDef("perfect_day", "圆满一日", "某天完成全部习惯", BadgeMetric.PERFECT_DAYS, 1),
    )

    fun seedBadges(): List<Badge> = defs.map {
        Badge(
            id = it.key,
            key = it.key,
            name = it.name,
            conditionJson = "{\"metric\":\"${it.metric.name}\",\"gte\":${it.threshold}}",
            unlockedAt = null,
            description = it.description,
        )
    }
}

object BadgeEvaluator {
    fun value(metric: BadgeMetric, stats: BadgeStats): Long = when (metric) {
        BadgeMetric.CURRENT_STREAK -> stats.currentStreak.toLong()
        BadgeMetric.LONGEST_STREAK -> stats.longestStreak.toLong()
        BadgeMetric.TOTAL_CHECK_INS -> stats.totalCheckIns.toLong()
        BadgeMetric.FOCUS_SECONDS -> stats.totalFocusSeconds
        BadgeMetric.TOTAL_POMOS -> stats.totalPomos.toLong()
        BadgeMetric.PERFECT_DAYS -> stats.perfectDays.toLong()
    }

    fun qualifies(def: BadgeDef, stats: BadgeStats): Boolean = value(def.metric, stats) >= def.threshold

    fun newlyQualified(stats: BadgeStats, alreadyUnlocked: Set<String>): List<BadgeDef> =
        BadgeCatalog.defs.filter { it.key !in alreadyUnlocked && qualifies(it, stats) }
}
