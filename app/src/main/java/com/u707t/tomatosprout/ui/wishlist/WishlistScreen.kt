package com.u707t.tomatosprout.ui.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.ui.components.BackBar
import com.u707t.tomatosprout.ui.components.CheckCircle
import com.u707t.tomatosprout.ui.components.EmptyState
import com.u707t.tomatosprout.ui.feedback.LocalFeedback
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Space

@Composable
fun WishlistScreen(onBack: () -> Unit, viewModel: WishlistViewModel = hiltViewModel()) {
    var text by remember { mutableStateOf("") }
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current

    Scaffold(
        topBar = { BackBar("愿望清单", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Surface(
                shape = RoundedCornerShape(Radii.md),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
            ) {
                Row(
                    Modifier.padding(start = Space.md, end = Space.xs, top = Space.xs, bottom = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = text,
                        onValueChange = { text = it.take(120) },
                        placeholder = {
                            Text(
                                "想做的一件小事",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Space.xs))
                    Surface(
                        onClick = {
                            if (text.isNotBlank()) {
                                feedback?.confirm()
                                viewModel.add(text)
                                text = ""
                            }
                        },
                        shape = RoundedCornerShape(Radii.sm),
                        color = if (text.isBlank()) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "添加愿望",
                                tint = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            when (val s = state) {
                WishUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                WishUiState.Empty -> EmptyState(
                    Icons.Default.FavoriteBorder,
                    "还没有愿望",
                    "写下一件想做的事。它只存在这台设备上，也只属于你。",
                )
                is WishUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
                is WishUiState.Content -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Space.lg,
                        end = Space.lg,
                        top = Space.sm,
                        bottom = 40.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    items(s.items, key = { it.id }) { item ->
                        val done = item.status == "done"
                        Surface(
                            shape = RoundedCornerShape(Radii.sm),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(start = Space.xs, end = Space.xs, top = Space.xs, bottom = Space.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CheckCircle(
                                    checked = done,
                                    onClick = {
                                        if (done) feedback?.tap() else feedback?.confirm()
                                        viewModel.toggle(item)
                                    },
                                    description = if (done) "标记为未完成" else "标记为已完成",
                                )
                                Text(
                                    item.content,
                                    modifier = Modifier.weight(1f).padding(horizontal = Space.sm),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (done) TextDecoration.LineThrough else null,
                                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                IconButton(onClick = {
                                    feedback?.reject()
                                    viewModel.delete(item)
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除愿望",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
