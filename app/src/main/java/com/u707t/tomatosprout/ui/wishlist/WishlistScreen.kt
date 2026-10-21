package com.u707t.tomatosprout.ui.wishlist

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.ui.components.EmptyState
import com.u707t.tomatosprout.ui.components.SoftCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(onBack: () -> Unit, viewModel: WishlistViewModel = hiltViewModel()) {
    var text by remember { mutableStateOf("") }
    val state by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的愿望清单") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(120) },
                    label = { Text("想做的一件小事") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(
                    onClick = { if (text.isNotBlank()) { viewModel.add(text); text = "" } },
                ) { Icon(Icons.Default.Add, contentDescription = "添加愿望") }
            }
            when (val s = state) {
                WishUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                WishUiState.Empty -> EmptyState("🌠", "还没有愿望", "写下一件想做的事。它只存在这台设备上，也只属于你。")
                is WishUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
                is WishUiState.Content -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(s.items, key = { it.id }) { item ->
                        SoftCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = item.status == "done",
                                    onCheckedChange = { viewModel.toggle(item) },
                                )
                                Text(
                                    item.content,
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (item.status == "done") TextDecoration.LineThrough else null,
                                    color = if (item.status == "done") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                IconButton(onClick = { viewModel.delete(item) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除愿望", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
