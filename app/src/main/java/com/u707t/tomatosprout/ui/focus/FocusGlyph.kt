package com.u707t.tomatosprout.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.u707t.tomatosprout.ui.feedback.LocalFeedback
import kotlin.math.cos
import kotlin.math.sin

/**
 * 专注页专用的线性图标。
 *
 * 不依赖 material-icons-extended（会把 APK 撑大十几 MB），
 * 统一 24×24 坐标系、1.6dp 圆头描边，和参考图里的细线图标气质一致。
 */
enum class FocusGlyph {
    FULLSCREEN,
    SUN,
    MUSIC,
    SHARE,
    PLAY,
    PAUSE,
    RESET,
    STOP,
    CHEVRON_DOWN,
}

@Composable
fun FocusGlyphIcon(
    glyph: FocusGlyph,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    strokeWidth: Dp = 1.6.dp,
) {
    Canvas(modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        val stroke = strokeWidth.toPx()
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, p(x1, y1), p(x2, y2), stroke, StrokeCap.Round)
        }

        when (glyph) {
            FocusGlyph.FULLSCREEN -> {
                line(4f, 9f, 4f, 4f); line(4f, 4f, 9f, 4f)
                line(15f, 4f, 20f, 4f); line(20f, 4f, 20f, 9f)
                line(20f, 15f, 20f, 20f); line(20f, 20f, 15f, 20f)
                line(9f, 20f, 4f, 20f); line(4f, 20f, 4f, 15f)
            }

            FocusGlyph.SUN -> {
                drawCircle(tint, 3.6f * unit, p(12f, 12f), style = Stroke(stroke))
                repeat(8) { i ->
                    val a = Math.toRadians(i * 45.0)
                    val c = cos(a).toFloat()
                    val s = sin(a).toFloat()
                    drawLine(
                        tint,
                        p(12f + c * 6.4f, 12f + s * 6.4f),
                        p(12f + c * 9.2f, 12f + s * 9.2f),
                        stroke,
                        StrokeCap.Round,
                    )
                }
            }

            FocusGlyph.MUSIC -> {
                drawOval(
                    tint,
                    topLeft = p(5.6f, 15.4f),
                    size = Size(4.6f * unit, 3.8f * unit),
                    style = Fill,
                )
                drawLine(tint, p(10.2f, 17.3f), p(10.2f, 4.6f), stroke, StrokeCap.Round)
                drawLine(tint, p(10.2f, 4.6f), p(17.2f, 6.9f), stroke, StrokeCap.Round)
                drawLine(tint, p(17.2f, 6.9f), p(17.2f, 10.6f), stroke, StrokeCap.Round)
            }

            FocusGlyph.SHARE -> {
                line(6f, 18f, 18f, 6f)
                line(18f, 6f, 11.6f, 6f)
                line(18f, 6f, 18f, 12.4f)
            }

            FocusGlyph.PLAY -> {
                val path = Path().apply {
                    moveTo(8.5f * unit, 5f * unit)
                    lineTo(19f * unit, 12f * unit)
                    lineTo(8.5f * unit, 19f * unit)
                    close()
                }
                drawPath(path, tint)
            }

            FocusGlyph.PAUSE -> {
                drawRoundRect(
                    tint,
                    topLeft = p(6.6f, 5.2f),
                    size = Size(3.6f * unit, 13.6f * unit),
                    cornerRadius = CornerRadius(1.6f * unit),
                )
                drawRoundRect(
                    tint,
                    topLeft = p(13.8f, 5.2f),
                    size = Size(3.6f * unit, 13.6f * unit),
                    cornerRadius = CornerRadius(1.6f * unit),
                )
            }

            FocusGlyph.RESET -> {
                val r = 7.4f * unit
                val c = p(12f, 12f)
                drawArc(
                    color = tint,
                    startAngle = -55f,
                    sweepAngle = 292f,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                val a = Math.toRadians(-55.0)
                val tip = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                val tx = -sin(a).toFloat()
                val ty = cos(a).toFloat()
                val nx = -ty
                val ny = tx
                val back = 3.4f * unit
                drawLine(
                    tint,
                    tip,
                    Offset(tip.x - tx * back + nx * 2.4f * unit, tip.y - ty * back + ny * 2.4f * unit),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    tip,
                    Offset(tip.x - tx * back - nx * 2.4f * unit, tip.y - ty * back - ny * 2.4f * unit),
                    stroke,
                    StrokeCap.Round,
                )
            }

            FocusGlyph.STOP -> {
                drawRoundRect(
                    tint,
                    topLeft = p(7f, 7f),
                    size = Size(10f * unit, 10f * unit),
                    cornerRadius = CornerRadius(1.6f * unit, 1.6f * unit),
                )
            }

            FocusGlyph.CHEVRON_DOWN -> {
                line(8f, 10f, 12f, 14f)
                line(12f, 14f, 16f, 10f)
            }
        }
    }
}

/** 56dp 触控区域，点击不显示水波纹（背景是照片，水波纹会显脏）。 */
@Composable
fun GlyphButton(
    glyph: FocusGlyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White,
    iconSize: Dp = 24.dp,
    touchSize: Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val feedback = LocalFeedback.current
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "glyphPress",
    )
    Box(
        modifier = modifier
            .size(touchSize)
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    feedback?.tap()
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        FocusGlyphIcon(
            glyph = glyph,
            tint = if (enabled) tint else tint.copy(alpha = 0.28f),
            modifier = Modifier.size(iconSize).graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        )
    }
}
