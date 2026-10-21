package com.u707t.tomatosprout.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.CheckInResult
import com.u707t.tomatosprout.domain.CheckInUseCase
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.domain.TodayData
import com.u707t.tomatosprout.domain.TodayItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data class Empty(val today: LocalDate) : TodayUiState
    data class Content(val data: TodayData, val simpleMode: Boolean) : TodayUiState
    data class Error(val message: String) : TodayUiState
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val checkIn: CheckInUseCase,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages

    val ui: StateFlow<TodayUiState> = combine(checkIn.observeToday(), settings.settings) { data, config ->
        if (data.items.isEmpty() && data.habitTotal == 0) {
            TodayUiState.Empty(data.today) as TodayUiState
        } else {
            TodayUiState.Content(data, config.simpleMode)
        }
    }.catch { e ->
        emit(TodayUiState.Error(e.message ?: "读取数据失败"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    fun toggle(item: TodayItem) {
        viewModelScope.launch {
            when (val result = checkIn.toggle(item)) {
                is CheckInResult.Checked -> {
                    val badge = result.newBadges.firstOrNull()
                    _messages.emit(
                        when {
                            badge != null -> "打卡成功，解锁徽章「${badge.name}」"
                            item.target > 1 -> "已记录 ${item.count + 1}/${item.target} 次"
                            else -> "已打卡 · 连续 ${result.streak} 天"
                        },
                    )
                }
                is CheckInResult.Undone -> _messages.emit(result.message)
                is CheckInResult.Rejected -> _messages.emit(result.reason)
            }
        }
    }

    fun undo(item: TodayItem) {
        viewModelScope.launch {
            checkIn.removeAll(item)
            _messages.emit("已撤销打卡")
        }
    }

    fun saveMeta(checkInId: String, note: String, mood: Int?) {
        viewModelScope.launch {
            checkIn.updateMeta(checkInId, note, mood)
            _messages.emit("备注已保存")
        }
    }

    fun setSimpleMode(enabled: Boolean) {
        viewModelScope.launch { settings.update { it.copy(simpleMode = enabled) } }
    }

    fun notify(text: String) {
        _messages.tryEmit(text)
    }
}
