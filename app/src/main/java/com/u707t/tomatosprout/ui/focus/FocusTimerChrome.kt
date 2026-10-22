package com.u707t.tomatosprout.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.u707t.tomatosprout.domain.TimerPhase
import com.u707t.tomatosprout.ui.theme.Tomato

/** 顶部工具条：返回、沉浸、白噪音、常亮、分享。 */
@Composable
fun FocusTopBar(
    immersive: Boolean,
    audioPlaying: Boolean,
    keepScreenOn: Boolean,
    onBack: () -> Unit,
    onToggleImmersive: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "退出专注", tint = Color.White)
        }
        Spacer(Modifier.weight(1f))
        TopAction("⛶", "沉浸模式", active = immersive, onClick = onToggleImmersive)
        TopAction("🎧", "白噪音", active = audioPlaying, onClick = onToggleAudio)
        TopAction("☀", "屏幕常亮", active = keepScreenOn, onClick = onToggleKeepScreenOn)
        TopAction("↗", "分享海报", active = false, onClick = onShare)
    }
}

/** 底部固定控制区：亮度、白噪音、重置/开始暂停/停止。 */
@Composable
fun FocusBottomControls(
    ui: FocusUiState,
    base: Color,
    audioMenuExpanded: Boolean,
    onAudioMenuChange: (Boolean) -> Unit,
    onSelectAudio: (FocusAudioOption) -> Unit,
    onBrightness: (Float) -> Unit,
    onReset: () -> Unit,
    onStartOrPause: () -> Unit,
    onStop: () -> Unit,
) {
    val timer = ui.timer
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.24f)),
                ),
            )
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☀", color = Color.White.copy(alpha = 0.7f))
            Slider(
                value = if (ui.brightness < 0f) 0.6f else ui.brightness,
                onValueChange = onBrightness,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(alpha = 0.7f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                ),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box {
                TextButton(onClick = { onAudioMenuChange(true) }) {
                    Text(
                        "🎵 " + ui.audioTrack.label + if (ui.audioPlaying) " · 播放中" else "",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                DropdownMenu(
                    expanded = audioMenuExpanded,
                    onDismissRequest = { onAudioMenuChange(false) },
                ) {
                    FocusAudioOption.entries.forEach { option ->
                        key(option) {
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onAudioMenuChange(false)
                                    onSelectAudio(option)
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "白噪音为本地音频",
                color = Color.White.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleControl(
                label = "重置",
                enabled = timer != null && !(timer.strict && timer.phase == TimerPhase.RUNNING),
                onClick = onReset,
            )
            Surface(
                onClick = onStartOrPause,
                enabled = timer == null || !(timer.strict && timer.phase == TimerPhase.RUNNING),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(78.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            timer == null -> "开始"
                            timer.phase == TimerPhase.PAUSED -> "继续"
                            else -> "暂停"
                        },
                        color = lerp(base, Color.Black, 0.55f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                }
            }
            CircleControl(
                label = "停止",
                enabled = timer != null,
                square = true,
                onClick = onStop,
            )
        }
    }
}

@Composable
private fun TopAction(label: String, description: String, active: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            label,
            color = if (active) Tomato else Color.White.copy(alpha = 0.78f),
            fontSize = 18.sp,
            modifier = Modifier.padding(4.dp),
        )
    }
}

@Composable
private fun CircleControl(
    label: String,
    enabled: Boolean,
    square: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = if (square) RoundedCornerShape(14.dp) else CircleShape,
        color = Color.White.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
        modifier = Modifier.size(if (square) 58.dp else 64.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (square) "■" else "↺",
                color = Color.White.copy(alpha = if (enabled) 0.86f else 0.3f),
                fontSize = if (square) 20.sp else 24.sp,
            )
        }
    }
}
