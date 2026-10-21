package com.u707t.tomatosprout.ui.badges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.Badge
import com.u707t.tomatosprout.domain.BadgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface BadgesUiState {
    data object Loading : BadgesUiState
    data object Empty : BadgesUiState
    data class Content(val badges: List<Badge>) : BadgesUiState
    data class Error(val message: String) : BadgesUiState
}

@HiltViewModel
class BadgesViewModel @Inject constructor(repo: BadgeRepository) : ViewModel() {
    val ui: StateFlow<BadgesUiState> = repo.observeAll()
        .map { list -> if (list.isEmpty()) BadgesUiState.Empty as BadgesUiState else BadgesUiState.Content(list) }
        .catch { emit(BadgesUiState.Error(it.message ?: "徽章加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgesUiState.Loading)
}
