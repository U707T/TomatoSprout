package com.u707t.tomatosprout.ui.badges

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.u707t.tomatosprout.domain.Badge
import com.u707t.tomatosprout.ui.components.BackBar
import com.u707t.tomatosprout.ui.components.EmptyState
import com.u707t.tomatosprout.ui.theme.Radii
import com.u707t.tomatosprout.ui.theme.Space
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BadgesScreen(onBack: () -> Unit, viewModel: BadgesViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { BackBar("成就徽章", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val s = state) {
            BadgesUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center,
            ) { CircularProgressIndicator() }

            BadgesUiState.Empty -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center,
            ) {
                EmptyState("🏅", "徽章还在路上", "完成第一次打卡后，这里会亮起第一枚徽章。")
            }

            is BadgesUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                Alignment.Center,
            ) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }

            is BadgesUiState.Content -> {
                val unlocked = s.badges.count { it.unlockedAt != null }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Space.lg,
                        end = Space.lg,
                        top = Space.sm,
                        bottom = 40.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    item(span = { GridItemSpan(2) }) {
                        Column(Modifier.padding(bottom = Space.xs)) {
                            Text(
                                "已点亮 $unlocked / ${s.badges.size}",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                "不是惩罚清单，只是把你已经做过的事，认真地记下来。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
        if (visible) 1f else 0.9f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "badgeScale",
    )
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(280), label = "badgeAlpha")
    Surface(
        shape = RoundedCornerShape(Radii.md),
        color = if (unlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (unlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
    ) {
        Column(
            Modifier.fillMaxSize().padding(Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(
                        if (unlocked) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (unlocked) "🏅" else "🔒", fontSize = 22.sp)
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                badge.name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                badge.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                if (unlocked) {
                    badge.unlockedAt?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("M 月 d 日")) + " 解锁"
                    } ?: "已解锁"
                } else {
                    "继续积累"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
