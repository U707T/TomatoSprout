package com.u707t.tomatosprout.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.CheckInResult
import com.u707t.tomatosprout.domain.CheckInUseCase
import com.u707t.tomatosprout.domain.HabitSchedule
import com.u707t.tomatosprout.domain.StatsData
import com.u707t.tomatosprout.domain.StatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Empty(val today: LocalDate) : StatsUiState
    data class Content(val data: StatsData) : StatsUiState
    data class Error(val message: String) : StatsUiState
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val stats: StatsUseCase,
    private val checkIn: CheckInUseCase,
) : ViewModel() {
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val ui: StateFlow<StatsUiState> = stats.observe()
        .map { data ->
            if (data.habits.isEmpty() && data.days.isEmpty()) {
                StatsUiState.Empty(data.today) as StatsUiState
            } else {
                StatsUiState.Content(data)
            }
        }
        .catch { emit(StatsUiState.Error(it.message ?: "统计加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState.Loading)

    fun makeup(taskId: String, date: LocalDate) {
        val data = (ui.value as? StatsUiState.Content)?.data ?: return
        val habit = data.habits.firstOrNull { it.task.id == taskId } ?: return
        if (!HabitSchedule.isScheduled(habit.rule, date)) {
            messages.tryEmit("该日期不在排期中")
            return
        }
        viewModelScope.launch {
            when (val result = checkIn.makeup(habit.task, habit.rule, date)) {
                is CheckInResult.Checked -> messages.emit("补卡成功 · 当前连续 ${result.streak} 天")
                is CheckInResult.Rejected -> messages.emit(result.reason)
                is CheckInResult.Undone -> messages.emit(result.message)
            }
        }
    }
}
