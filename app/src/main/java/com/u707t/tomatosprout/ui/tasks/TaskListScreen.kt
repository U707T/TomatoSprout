package com.u707t.tomatosprout.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.ui.components.EmptyState
import com.u707t.tomatosprout.ui.components.SoftCard
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全部任务") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val s = state) {
            TaskListUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            TaskListUiState.Empty -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                EmptyState("🗒", "还没有任务", "在「今日」页点右下角 + 创建习惯、待办或目标。")
            }
            is TaskListUiState.Error -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is TaskListUiState.Content -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Text("进行中 · ${s.active.size}", style = MaterialTheme.typography.titleMedium) }
                items(s.active, key = { it.id }) { task ->
                    TaskRow(
                        task,
                        onEdit = { onEdit(task.id) },
                        onRestore = null,
                        onReopen = if (task.status == TaskStatus.DONE) {
                            { viewModel.reopen(task) }
                        } else null,
                    )
                }
                if (s.archived.isNotEmpty()) {
                    item {
                        Text(
                            "已归档 · ${s.archived.size}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    items(s.archived, key = { it.id }) { task ->
                        TaskRow(
                            task,
                            onEdit = { onEdit(task.id) },
                            onRestore = { viewModel.restore(task) },
                            onReopen = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    onEdit: () -> Unit,
    onRestore: (() -> Unit)?,
    onReopen: (() -> Unit)?,
) {
    SoftCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(task.icon ?: "🌱", fontSize = 20.sp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (task.type) {
                        TaskType.HABIT -> "习惯"
                        TaskType.TODO -> "待办"
                        TaskType.GOAL -> "目标"
                        TaskType.POMODORO -> "番茄任务"
                    } + when (task.status) {
                        TaskStatus.DONE -> " · 已完成"
                        TaskStatus.ARCHIVED -> " · 已归档"
                        TaskStatus.TODO -> task.dueAt?.let {
                            " · 截止 " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                                .toLocalDate().toString()
                        } ?: ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRestore != null) {
                TextButton(onClick = onRestore) { Text("恢复") }
            } else if (task.status == TaskStatus.DONE) {
                TextButton(onClick = onReopen ?: {}) { Text("重新打开") }
            }
        }
    }
}
