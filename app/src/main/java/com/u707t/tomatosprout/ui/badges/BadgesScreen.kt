package com.u707t.tomatosprout.ui.badges

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.domain.Badge
import com.u707t.tomatosprout.ui.components.EmptyState
import com.u707t.tomatosprout.ui.theme.Tomato
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreen(onBack: () -> Unit, viewModel: BadgesViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("成就徽章") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val s = state) {
            BadgesUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            BadgesUiState.Empty -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                EmptyState("🏅", "徽章还在路上", "完成第一次打卡后，这里会亮起第一枚徽章。")
            }
            is BadgesUiState.Error -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
            is BadgesUiState.Content -> {
                val unlocked = s.badges.count { it.unlockedAt != null }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(2) }) {
                        Column {
                            Text("已点亮 $unlocked / ${s.badges.size}", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "不是惩罚清单，只是把你已经做过的事，认真地记下来。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    items(s.badges, key = { it.id }) { badge -> BadgeCard(badge) }
                }
            }
        }
    }
}

@Composable
private fun BadgeCard(badge: Badge) {
    val unlocked = badge.unlockedAt != null
    var visible by remember(badge.id) { mutableStateOf(false) }
    LaunchedEffect(badge.id) { visible = true }
    val scale by animateFloatAsState(
        if (visible) 1f else 0.85f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "badgeScale",
    )
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(260), label = "badgeAlpha")
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().height(168.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(if (unlocked) "🏅" else "🔒", fontSize = 34.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                badge.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                badge.description,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (unlocked) {
                    badge.unlockedAt?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("M月d日")) + " 解锁"
                    } ?: "已解锁"
                } else "继续积累",
                style = MaterialTheme.typography.labelSmall,
                color = if (unlocked) Tomato else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
