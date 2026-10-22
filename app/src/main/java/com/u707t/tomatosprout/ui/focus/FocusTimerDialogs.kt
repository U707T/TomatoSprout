package com.u707t.tomatosprout.ui.focus

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.u707t.tomatosprout.domain.InterruptReason
import com.u707t.tomatosprout.ui.theme.Tomato

/** 正计时结束：是否记录。 */
@Composable
fun RecordPromptDialog(
    prompt: RecordPrompt,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDiscard,
        shape = RoundedCornerShape(24.dp),
        containerColor = DialogSurface,
        titleContentColor = DialogInk,
        textContentColor = DialogMuted,
        title = { Text("记录这次专注？") },
        text = {
            Text(
                "正向计时 ${prompt.actualSeconds} 秒。记录后会写入一条专注记录，" +
                    "并自动完成今天「${prompt.taskTitle ?: "自由专注"}」的打卡。",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("记录") } },
        dismissButton = { TextButton(onClick = onDiscard) { Text("不记录") } },
    )
}

/** 手动停止倒计时前的拦截确认。 */
@Composable
fun StopConfirmDialog(
    confirm: StopConfirm,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onReason: (InterruptReason) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(24.dp),
        containerColor = DialogSurface,
        titleContentColor = DialogInk,
        textContentColor = DialogMuted,
        title = { Text("结束本次专注？") },
        text = {
            Column {
                Text("已专注 ${confirm.actualSeconds / 60} 分 ${confirm.actualSeconds % 60} 秒，还差 ${confirm.remainingSeconds / 60} 分钟。")
                Text(
                    if (confirm.strict) {
                        "严格模式下提前结束，本次专注会彻底作废，不写入任何记录。"
                    } else {
                        "结束后只记录一条「中断」记录，不会触发打卡。"
                    },
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!confirm.strict) {
                    Text(
                        "中断原因",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        InterruptReason.entries.forEach { reason ->
                            val selected = confirm.reason == reason
                            Surface(
                                onClick = { onReason(reason) },
                                shape = RoundedCornerShape(50),
                                color = if (selected) Tomato.copy(alpha = 0.24f) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Tomato.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.18f),
                                ),
                            ) {
                                Text(
                                    reason.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color(0xFFFFB3A3) else Color.White.copy(alpha = 0.68f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("结束") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("继续专注") } },
    )
}

/** 结算摘要：完成 / 中断 / 丢弃 / 休息结束。 */
@Composable
fun SessionSummaryDialog(
    summary: SessionSummary,
    breakMinutes: Int,
    longBreakMinutes: Int,
    onDismiss: () -> Unit,
    onStartBreak: (Boolean) -> Unit,
    onStartAgain: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = DialogSurface,
        titleContentColor = DialogInk,
        textContentColor = DialogMuted,
        title = {
            Text(
                when {
                    summary.isBreak -> "休息结束 ☕"
                    summary.discarded -> "本次专注未记录"
                    summary.interrupted -> "已记录中断"
                    else -> "完成专注 🍅"
                },
            )
        },
        text = {
            Column {
                when {
                    summary.discarded -> {
                        Text(summary.discardReason ?: "本次专注已按规则丢弃。")
                    }
                    summary.isBreak -> {
                        Text("休息了 ${summary.minutes} 分钟，准备好再开始。")
                    }
                    else -> {
                        Text(
                            (summary.taskTitle ?: "自由专注") + " · " +
                                if (summary.actualSeconds in 1..59) {
                                    "${summary.actualSeconds} 秒"
                                } else {
                                    "${summary.minutes} 分钟"
                                },
                        )
                        if (summary.interrupted) {
                            Text(
                                "未达计划时长，只记录中断状态，不计入打卡。",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            summary.interruptReason?.let { reason ->
                                Text(
                                    "中断原因：$reason",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        } else if (summary.checkInWritten) {
                            Text(
                                "已自动打卡" + if (summary.streak > 0) " · 连续 ${summary.streak} 天" else "",
                                color = Tomato,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        } else if (summary.streak > 0) {
                            Text(
                                "今日已完成打卡 · 连续 ${summary.streak} 天",
                                color = Tomato,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (summary.crossDay) {
                            Text(
                                "跨天专注 · 归入 " + (summary.attributedDate?.toString() ?: "开始日"),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (summary.newBadges.isNotEmpty()) {
                            Text(
                                "解锁徽章：" + summary.newBadges.joinToString("、") { it.name },
                                color = Tomato,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                summary.discarded -> TextButton(onClick = onDismiss) { Text("好") }
                summary.isBreak -> TextButton(onClick = onStartAgain) { Text("再专注一次") }
                else -> TextButton(onClick = { onStartBreak(summary.longBreakDue) }) {
                    Text(
                        if (summary.longBreakDue) {
                            "长休息 $longBreakMinutes 分钟"
                        } else {
                            "开始休息 $breakMinutes 分钟"
                        },
                    )
                }
            }
        },
        dismissButton = {
            if (!summary.discarded && !summary.isBreak) {
                Row {
                    if (summary.longBreakDue) {
                        TextButton(onClick = { onStartBreak(false) }) {
                            Text("短休息 $breakMinutes 分钟")
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("再来一个") }
                }
            }
        },
    )
}

private val DialogSurface = Color(0xFF241E1A)
private val DialogInk = Color(0xFFF1E9E2)
private val DialogMuted = Color(0xFFC9BCB2)
