package com.u707t.tomatosprout

import com.u707t.tomatosprout.ui.theme.AccentTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题色的硬约束：换色可以，读不清不行。
 *
 * 三个位置必须过 WCAG AA（4.5:1）：
 * 1. 实心按钮：onPrimary / primary
 * 2. 浅底强调块：onPrimaryContainer / primaryContainer
 * 3. 正文里的强调字：primary / 纸面（浅色）或 primary / 夜间底（深色）
 */
class AccentContrastTest {

    private val paper = 0xFFFAF6F1
    private val nightBg = 0xFF14110F

    private fun channel(value: Int): Double {
        val v = value / 255.0
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    private fun luminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `light accents keep text readable`() {
        AccentTheme.entries.forEach { accent ->
            val role = accent.light
            assertAtLeast(4.5, contrast(role.onPrimary, role.primary), "${accent.key} 浅色按钮")
            assertAtLeast(4.5, contrast(role.onPrimaryContainer, role.primaryContainer), "${accent.key} 浅色容器")
            assertAtLeast(4.5, contrast(role.primary, paper), "${accent.key} 浅色强调字")
        }
    }

    @Test
    fun `dark accents keep text readable`() {
        AccentTheme.entries.forEach { accent ->
            val role = accent.dark
            assertAtLeast(4.5, contrast(role.onPrimary, role.primary), "${accent.key} 深色按钮")
            assertAtLeast(4.5, contrast(role.onPrimaryContainer, role.primaryContainer), "${accent.key} 深色容器")
            assertAtLeast(4.5, contrast(role.primary, nightBg), "${accent.key} 深色强调字")
        }
    }

    @Test
    fun `unknown key falls back to default`() {
        assertTrue(AccentTheme.of("nope") == AccentTheme.DEFAULT)
        assertTrue(AccentTheme.of(null) == AccentTheme.DEFAULT)
        assertTrue(AccentTheme.of("pine") == AccentTheme.PINE)
    }

    private fun assertAtLeast(min: Double, actual: Double, label: String) {
        assertTrue("$label 对比度 ${"%.2f".format(actual)} < $min", actual >= min)
    }
}
