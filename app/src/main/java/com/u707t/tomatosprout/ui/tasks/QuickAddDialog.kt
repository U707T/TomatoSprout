package com.u707t.tomatosprout.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.u707t.tomatosprout.ui.theme.Tomato

/**
 * 半屏快捷添加。输入名称 + 三个单选项；高级设置携带当前参数进入完整编辑页。
 */
@Composable
fun QuickAddDialog(
    state: QuickAddUiState,
    onTitle: (String) -> Unit,
    onType: (QuickTaskType) -> Unit,
    onTimer: (QuickTimerMode) -> Unit,
    onDuration: (Int) -> Unit,
    onCustomDuration: (Boolean) -> Unit,
    onAdvanced: () -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    var customText by remember(state.customDuration) { mutableStateOf(state.durationMinutes.toString()) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
            ) {
                Text("新建任务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitle,
                    placeholder = { Text("请输入待办名称", fontSize = 20.sp) },
                    textStyle = MaterialTheme.typography.titleMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text("类型", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    QuickTaskType.entries.forEach { type ->
                        key(type) {
                            FilterChip(
                                selected = state.type == type,
                                onClick = { onType(type) },
                                label = { Text(type.label) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("计时方式", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickTimerMode.entries.forEach { mode ->
                        key(mode) {
                            FilterChip(
                                selected = state.timer == mode,
                                onClick = { onTimer(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
                Text(
                    if (state.timer == QuickTimerMode.NONE) {
                        "按次打卡：今天做一次，就在首页点一次「打卡」。"
                    } else {
                        "计时任务：首页只显示「开始」，完成后自动写入专注与打卡。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (state.timer == QuickTimerMode.COUNTDOWN) {
                    Spacer(Modifier.height(12.dp))
                    Text("时长", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(25, 35).forEach { minutes ->
                            key(minutes) {
                                FilterChip(
                                    selected = !state.customDuration && state.durationMinutes == minutes,
                                    onClick = { onCustomDuration(false); onDuration(minutes) },
                                    label = { Text("$minutes 分钟") },
                                )
                            }
                        }
                        FilterChip(
                            selected = state.customDuration,
                            onClick = { onCustomDuration(true) },
                            label = { Text("自定义") },
                        )
                    }
                    if (state.customDuration) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { text ->
                                customText = text.filter(Char::isDigit).take(3)
                                customText.toIntOrNull()?.let(onDuration)
                            },
                            label = { Text("分钟（5–180）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(180.dp).padding(top = 8.dp),
                            singleLine = true,
                        )
                    }
                }
                if (!state.error.isNullOrBlank()) {
                    Text(
                        state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onAdvanced, enabled = !state.saving) {
                    Text("展开更多高级设置", color = Tomato)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(
                        onClick = onCreate,
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) { Text(if (state.saving) "创建中…" else "创建任务") }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
