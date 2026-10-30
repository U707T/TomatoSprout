package com.u707t.tomatosprout.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * 动效令牌：时长只有三档，避免每个文件各写一个数字。
 *
 * 原则：
 * - 90% 的交互（选中、展开、淡入）用 [base]，用户感觉不到「在等动画」；
 * - 只有页面进出、整块内容替换才允许 [slow]；
 * - 按压回弹统一用 [pressSpring]，跟手、不过冲。
 */
object Motion {
    const val instant = 80
    const val fast = 140
    const val base = 220
    const val slow = 320

    /** 强调曲线：起步快、收尾慢，适合位移与缩放。 */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 标准曲线：颜色与透明度过渡。 */
    val standard: Easing = FastOutSlowInEasing

    val pressSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    val settleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
