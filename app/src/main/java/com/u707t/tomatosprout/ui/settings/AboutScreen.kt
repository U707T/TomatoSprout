package com.u707t.tomatosprout.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.u707t.tomatosprout.BuildConfig
import com.u707t.tomatosprout.ui.components.BackBar
import com.u707t.tomatosprout.ui.components.SoftCard
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Space
import com.u707t.tomatosprout.ui.theme.Tomato

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            BackBar("关于番茄芽", onBack)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(Radii.md)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🍅", fontSize = 30.sp)
                }
                Column(Modifier.padding(start = Space.lg)) {
                    Text("番茄芽", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
            SoftCard {
                Text("隐私说明", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "番茄芽不收集、不上报任何用户数据，没有埋点、没有广告、没有账号体系。所有习惯、打卡与专注记录默认只保存在本机。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "如果你主动配置 WebDAV，数据会在你的设备与你自己的服务器之间传输；密码使用 Android Keystore 加密，服务器地址与账号仅保存在本机。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            SoftCard {
                Text("权限", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• 通知（可选）：习惯提醒与专注实时活动\n" +
                        "• 网络 / 网络状态：仅在你手动触发 WebDAV 同步时使用\n\n" +
                        "不申请任何敏感权限，也没有桌面小组件；所有数据默认只保存在本机。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            SoftCard {
                Text("开源组件", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Kotlin · Jetpack Compose · Material 3\n" +
                        "AndroidX Lifecycle / Navigation / Room / DataStore / WorkManager\n" +
                        "Hilt · Kotlin Coroutines · kotlinx.serialization · OkHttp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "以上组件分别遵循 Apache-2.0 等开源许可。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            SoftCard {
                Text("设计原则", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "不索权 · 不打扰 · 不空洞\n\n" +
                        "连续中断时只会提示「重新开始」，不使用惩罚性文案。休息也是一种节奏。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text("Made with 🍅 and Kotlin", color = Tomato, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
