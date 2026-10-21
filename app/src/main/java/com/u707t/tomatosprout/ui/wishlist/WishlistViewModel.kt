package com.u707t.tomatosprout.ui.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.WishItem
import com.u707t.tomatosprout.domain.WishRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface WishUiState {
    data object Loading : WishUiState
    data object Empty : WishUiState
    data class Content(val items: List<WishItem>) : WishUiState
    data class Error(val message: String) : WishUiState
}

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val repo: WishRepository,
) : ViewModel() {
    val ui: StateFlow<WishUiState> = repo.observeAll()
        .map { list -> if (list.isEmpty()) WishUiState.Empty as WishUiState else WishUiState.Content(list) }
        .catch { emit(WishUiState.Error(it.message ?: "愿望清单加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WishUiState.Loading)

    fun add(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        viewModelScope.launch {
            repo.upsert(WishItem(UUID.randomUUID().toString(), content, "pending", System.currentTimeMillis()))
        }
    }

    fun toggle(item: WishItem) {
        viewModelScope.launch {
            repo.upsert(item.copy(status = if (item.status == "done") "pending" else "done"))
        }
    }

    fun delete(item: WishItem) {
        viewModelScope.launch { repo.softDelete(item.id, System.currentTimeMillis()) }
    }
}
