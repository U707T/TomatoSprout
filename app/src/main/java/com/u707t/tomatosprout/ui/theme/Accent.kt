package com.u707t.tomatosprout.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题色（强调色）。
 *
 * 参考 cfbrowser「墨笺」的做法：**纸面与墨色固定，只让强调色变**。
 * 底色永远是那套暖白 / 暖炭灰，主题色只落在按钮、链接、进度条、选中态这些
 * 少量高信息量的位置，避免整屏换色带来的廉价感。
 *
 * 每个主题色提供浅色与深色两套角色，取值已按 WCAG AA 校验：
 * - `onPrimary / primary` ≥ 4.5（实心按钮上的白字）
 * - `onPrimaryContainer / primaryContainer` ≥ 4.5（浅底彩字）
 * - `primary / paper`（浅色）、`primary / nightBg`（深色）≥ 4.5（正文里的强调字）
 *
 * 对应单测：`AccentContrastTest`。改色值前先跑它。
 */
enum class AccentTheme(
    val key: String,
    val label: String,
    val light: AccentRole,
    val dark: AccentRole,
) {
    /** 默认：番茄红。比品牌色 #E0553F 深一档，白字才够 4.5:1。 */
    TOMATO(
        key = "tomato",
        label = "番茄",
        light = AccentRole(
            primary = 0xFFBE4834,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFFBE4DC,
            onPrimaryContainer = 0xFF6E2317,
            inversePrimary = 0xFFFFB4A3,
        ),
        dark = AccentRole(
            primary = 0xFFFF9A83,
            onPrimary = 0xFF3A0F08,
            primaryContainer = 0xFF4A2119,
            onPrimaryContainer = 0xFFFFD9D0,
            inversePrimary = 0xFFBE4834,
        ),
    ),

    /** 黛青：偏冷的蓝，适合夜间与「工作模式」。 */
    INDIGO(
        key = "indigo",
        label = "黛青",
        light = AccentRole(
            primary = 0xFF2C5A78,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFE2ECF4,
            onPrimaryContainer = 0xFF16344A,
            inversePrimary = 0xFF9CC6E6,
        ),
        dark = AccentRole(
            primary = 0xFF84B2D3,
            onPrimary = 0xFF0E2233,
            primaryContainer = 0xFF1D3B4E,
            onPrimaryContainer = 0xFFCFE4F2,
            inversePrimary = 0xFF2C5A78,
        ),
    ),

    /** 松烟：低饱和的绿，和「习惯 / 连续天数」最搭。 */
    PINE(
        key = "pine",
        label = "松烟",
        light = AccentRole(
            primary = 0xFF3B6A4E,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFE4EFE7,
            onPrimaryContainer = 0xFF1F3B2C,
            inversePrimary = 0xFF9FCFB2,
        ),
        dark = AccentRole(
            primary = 0xFF87BE9C,
            onPrimary = 0xFF10261A,
            primaryContainer = 0xFF22402F,
            onPrimaryContainer = 0xFFCDE9D6,
            inversePrimary = 0xFF3B6A4E,
        ),
    ),

    /** 赭石：暖黄棕，适合「目标 / 长期项目」。 */
    OCHRE(
        key = "ochre",
        label = "赭石",
        light = AccentRole(
            primary = 0xFF875B22,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFF6EAD7,
            onPrimaryContainer = 0xFF4A2F0F,
            inversePrimary = 0xFFE9BE84,
        ),
        dark = AccentRole(
            primary = 0xFFDCAA66,
            onPrimary = 0xFF33200A,
            primaryContainer = 0xFF463215,
            onPrimaryContainer = 0xFFF6E3C6,
            inversePrimary = 0xFF875B22,
        ),
    ),

    /** 梅子：偏紫的暗红，安静但不寡淡。 */
    PLUM(
        key = "plum",
        label = "梅子",
        light = AccentRole(
            primary = 0xFF8A3B5C,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFF7E3EB,
            onPrimaryContainer = 0xFF4C1C31,
            inversePrimary = 0xFFE9A9C3,
        ),
        dark = AccentRole(
            primary = 0xFFE29BB6,
            onPrimary = 0xFF33101F,
            primaryContainer = 0xFF45202F,
            onPrimaryContainer = 0xFFF8DDE7,
            inversePrimary = 0xFF8A3B5C,
        ),
    ),

    /** 墨色：无彩色，给「不想看到任何彩色按钮」的人。 */
    GRAPHITE(
        key = "graphite",
        label = "墨色",
        light = AccentRole(
            primary = 0xFF3B332D,
            onPrimary = 0xFFFDFAF6,
            primaryContainer = 0xFFEFE7DF,
            onPrimaryContainer = 0xFF231D19,
            inversePrimary = 0xFFD8CEC4,
        ),
        dark = AccentRole(
            primary = 0xFFD8CEC4,
            onPrimary = 0xFF241E1A,
            primaryContainer = 0xFF3A322C,
            onPrimaryContainer = 0xFFF1E9E2,
            inversePrimary = 0xFF3B332D,
        ),
    ),
    ;

    fun role(darkMode: Boolean): AccentRole = if (darkMode) dark else light

    /** 主题色自身（浅色档），用于设置页的色点与需要「本色」的插画。 */
    val swatch: Color get() = Color(light.primary)

    companion object {
        val DEFAULT: AccentTheme = TOMATO

        /** 从设置里存的字符串还原；脏数据回落到默认，不抛异常。 */
        fun of(key: String?): AccentTheme =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** 一套强调色角色，ARGB 用 Long 存，方便纯 JVM 单测直接算对比度。 */
data class AccentRole(
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val inversePrimary: Long,
) {
    fun primaryColor(): Color = Color(primary)
    fun onPrimaryColor(): Color = Color(onPrimary)
    fun primaryContainerColor(): Color = Color(primaryContainer)
    fun onPrimaryContainerColor(): Color = Color(onPrimaryContainer)
    fun inversePrimaryColor(): Color = Color(inversePrimary)
}
