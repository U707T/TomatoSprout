package com.u707t.tomatosprout.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.ui.components.BackBar
import com.u707t.tomatosprout.ui.components.SoftCard
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Space
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    viewModel: WebDavViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var initialized by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("/tomatosprout") }
    var wifiOnly by remember { mutableStateOf(true) }

    LaunchedEffect(settings) {
        if (!initialized) {
            server = settings.webDavServer
            username = settings.webDavUsername
            path = settings.webDavPath
            wifiOnly = settings.wifiOnly
            initialized = true
        }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            BackBar("WebDAV 同步", onBack, actions = {
                TextButton(onClick = onOpenLogs) { Text("日志") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                "只在你点「立即同步」时联网。服务器由你自己提供，没有托管后端，也没有账号体系。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SoftCard {
                Text("服务器", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://dav.example.com/remote.php/dav/files/me") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码（留空表示不修改）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("远程目录") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("仅 Wi-Fi 同步")
                        Text(
                            "开启后移动网络下不会上传或下载",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                }
                Text(
                    "密码使用 Android Keystore 的 AES-GCM 加密保存，不会写入数据库、日志或同步文件。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (busy) {
                Spacer(Modifier.height(Space.md))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.save(server, username, password, path, wifiOnly) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = !busy,
                    shape = RoundedCornerShape(Radii.sm),
                ) { Text("保存") }
                OutlinedButton(
                    onClick = { viewModel.test(server, username, password, path, wifiOnly) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = !busy,
                    shape = RoundedCornerShape(Radii.sm),
                ) { Text("测试连接") }
            }
            Spacer(Modifier.height(Space.sm))
            Button(
                onClick = { viewModel.sync(server, username, password, path, wifiOnly) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !busy && server.isNotBlank(),
                shape = RoundedCornerShape(Radii.sm),
            ) { Text("立即同步") }

            Spacer(Modifier.height(Space.xs))
            SoftCard {
                Text("同步怎么工作", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "每个设备上传一份全量快照到自己的文件，下载其他设备的快照后按 id 合并。打卡与专注只增不删；同一条记录双方都改过时，保留本地版本，并把远端版本改名保存为「冲突副本」。以内容哈希和 ETag 判断文件是否真的变化，不依赖设备时间戳。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    "上次同步：" + (settings.lastSyncedAt?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("M 月 d 日 HH:mm"))
                    } ?: "从未"),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "同步只在点击按钮时触发；失败后最多自动重试 3 次。未配置服务器时不会发起任何网络请求。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}
