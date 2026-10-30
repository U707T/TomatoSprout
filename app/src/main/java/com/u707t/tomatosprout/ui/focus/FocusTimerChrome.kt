package com.u707t.tomatosprout.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Ink = Color.White

/** 顶部：返回 + 沉浸 / 常亮 / 分享。只保留线性图标，不做圆底。 */
@Composable
fun FocusTopBar(
    immersive: Boolean,
    keepScreenOn: Boolean,
    onBack: () -> Unit,
    onToggleImmersive: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "收起专注",
                tint = Ink.copy(alpha = 0.92f),
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        GlyphButton(
            glyph = FocusGlyph.FULLSCREEN,
            description = "沉浸模式",
            onClick = onToggleImmersive,
            tint = Ink.copy(alpha = if (immersive) 1f else 0.8f),
            iconSize = 21.dp,
            touchSize = 44.dp,
        )
        GlyphButton(
            glyph = FocusGlyph.SUN,
            description = "屏幕常亮",
            onClick = onToggleKeepScreenOn,
            tint = Ink.copy(alpha = if (keepScreenOn) 1f else 0.8f),
            iconSize = 21.dp,
            touchSize = 44.dp,
        )
        GlyphButton(
            glyph = FocusGlyph.SHARE,
            description = "分享海报",
            onClick = onShare,
            tint = Ink.copy(alpha = 0.88f),
            iconSize = 21.dp,
            touchSize = 44.dp,
        )
        Spacer(Modifier.size(6.dp))
    }
}

/**
 * 底部工具条：亮度 / 声音 / 开始暂停 / 重置 / 停止。
 * 五个等距线性图标，没有实心底、没有文字标签——和参考图一致，
 * 功能说明交给 TalkBack（contentDescription）而不是常驻文字。
 */
@Composable
fun FocusBottomControls(
    timerActive: Boolean,
    paused: Boolean,
    canPause: Boolean,
    canStop: Boolean,
    onToggleBrightness: () -> Unit,
    onToggleAudio: () -> Unit,
    onStartOrPause: () -> Unit,
    onReset: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(64.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            glyph = FocusGlyph.SUN,
            description = "亮度",
            onClick = onToggleBrightness,
            tint = Ink.copy(alpha = 0.86f),
            iconSize = 22.dp,
        )
        AudioGlyphButton(onClick = onToggleAudio)
        GlyphButton(
            glyph = if (timerActive && !paused) FocusGlyph.PAUSE else FocusGlyph.PLAY,
            description = if (timerActive && !paused) "暂停" else "开始",
            onClick = onStartOrPause,
            enabled = canPause,
            iconSize = 30.dp,
        )
        GlyphButton(
            glyph = FocusGlyph.RESET,
            description = "重置",
            onClick = onReset,
            enabled = canStop,
            tint = Ink.copy(alpha = 0.86f),
            iconSize = 24.dp,
        )
        GlyphButton(
            glyph = FocusGlyph.STOP,
            description = "停止",
            onClick = onStop,
            enabled = canStop,
            tint = Ink.copy(alpha = 0.86f),
            iconSize = 24.dp,
        )
    }
}

/** 音符下面带一个小箭头，提示「点开还有声音可选」。 */
@Composable
private fun AudioGlyphButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .size(56.dp)
            .semantics { contentDescription = "白噪音" }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FocusGlyphIcon(FocusGlyph.MUSIC, tint = Ink.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(1.dp))
        FocusGlyphIcon(
            FocusGlyph.CHEVRON_DOWN,
            tint = Ink.copy(alpha = 0.45f),
            modifier = Modifier.size(9.dp),
            strokeWidth = 1.4.dp,
        )
    }
}

/** 声音选择：一行文字，选中的更亮并带小圆点。 */
@Composable
fun FocusAudioPanel(
    current: FocusAudioOption,
    playing: Boolean,
    onSelect: (FocusAudioOption) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusAudioOption.entries.forEach { option ->
            val selected = option == current
            val interaction = remember(option) { MutableInteractionSource() }
            Column(
                Modifier
                    .clickable(interactionSource = interaction, indication = null) { onSelect(option) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (selected && playing) "♪ ${option.label}" else option.label,
                    color = if (selected) Ink else Ink.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(3.dp)
                        .background(
                            if (selected) Ink else Color.Transparent,
                            CircleShape,
                        ),
                )
            }
        }
    }
}

/** 亮度：只在需要时露出一条细滑杆，平时不占视觉。 */
@Composable
fun FocusBrightnessPanel(value: Float, onChange: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusGlyphIcon(
            FocusGlyph.SUN,
            tint = Ink.copy(alpha = 0.5f),
            modifier = Modifier.size(15.dp),
            strokeWidth = 1.5.dp,
        )
        Slider(
            value = value.coerceIn(0.05f, 1f),
            onValueChange = onChange,
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .padding(horizontal = 10.dp)
                .semantics { contentDescription = "亮度" },
            colors = SliderDefaults.colors(
                thumbColor = Ink,
                activeTrackColor = Ink.copy(alpha = 0.72f),
                inactiveTrackColor = Ink.copy(alpha = 0.18f),
            ),
        )
        Text(
            "${(value.coerceIn(0.05f, 1f) * 100).roundToInt()}%",
            color = Ink.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
