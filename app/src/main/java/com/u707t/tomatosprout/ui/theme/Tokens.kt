package com.u707t.tomatosprout.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 设计令牌：颜色、间距、圆角。
 *
 * 视觉方向是「暖白纸感 + 番茄红点缀」：底色带一点暖黄，卡片用细描边而不是重阴影，
 * 层级主要靠排版、留白和 1dp 分隔线建立。深色模式不是纯黑，而是暖炭灰，
 * 避免夜间大块纯黑与白色文字形成刺眼的硬边。
 */
object Palette {
    // 品牌
    val Tomato = Color(0xFFE0553F)
    val TomatoDeep = Color(0xFFB8432F)
    val TomatoSoft = Color(0xFFFCE7E0)

    // 纸面
    val Paper = Color(0xFFFAF6F1)
    val PaperDeep = Color(0xFFF3ECE4)
    val Card = Color(0xFFFFFFFF)

    // 文字
    val Ink = Color(0xFF231D19)
    val InkMuted = Color(0xFF6D6057)
    val InkFaint = Color(0xFF9B8D83)

    // 线条
    val Line = Color(0xFFEBE1D7)
    val LineStrong = Color(0xFFDDD0C4)

    // 辅助色
    val Sage = Color(0xFF7C9A6F)
    val SageSoft = Color(0xFFE9F0E4)
    val Apricot = Color(0xFFE2A05C)
    val ApricotSoft = Color(0xFFFBF0DF)
    val Sky = Color(0xFF6E8AA8)
    val SkySoft = Color(0xFFE7EEF5)
    val Danger = Color(0xFFB3261E)
    val DangerSoft = Color(0xFFF9E4E2)

    // 夜间
    val NightBg = Color(0xFF14110F)
    val NightSurface = Color(0xFF1E1916)
    val NightCard = Color(0xFF241E1A)
    val NightInk = Color(0xFFF1E9E2)
    val NightMuted = Color(0xFFB9ACA2)
    val NightFaint = Color(0xFF8A7D74)
    val NightLine = Color(0xFF352D28)
    val NightLineSoft = Color(0xFF2A2320)
    val NightTomato = Color(0xFFFF8A72)
    val NightTomatoDeep = Color(0xFFFFB3A3)
    val NightTomatoSoft = Color(0xFF3A211C)
}

/** 8pt 栅格，允许 4dp 半格。 */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp
}

object Radii {
    val xs = 8.dp
    val sm = 12.dp
    val md = 18.dp
    val lg = 24.dp
    val xl = 28.dp
    val sheet = 30.dp
}

object Sizes {
    val touch = 48.dp
    val icon = 22.dp
    val avatar = 44.dp

    /** 底栏内容高度（不含系统手势区）。FAB 与列表底部留白都按它推导。 */
    val bottomBar = 62.dp
}

// 兼容旧引用：其它文件仍然 import ui.theme.Tomato
val Tomato: Color = Palette.Tomato
val TomatoDeep: Color = Palette.TomatoDeep
val Paper: Color = Palette.Paper
val Ink: Color = Palette.Ink
val Sage: Color = Palette.Sage
val Apricot: Color = Palette.Apricot
val Sand: Color = Palette.LineStrong
