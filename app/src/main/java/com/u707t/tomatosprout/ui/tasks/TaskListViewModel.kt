package com.u707t.tomatosprout.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.domain.TaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TaskListUiState {
    data object Loading : TaskListUiState
    data object Empty : TaskListUiState
    data class Content(val active: List<Task>, val archived: List<Task>) : TaskListUiState
    data class Error(val message: String) : TaskListUiState
}

@HiltViewModel
class TaskListViewModel @Inject constructor(private val useCase: TaskUseCase) : ViewModel() {
    val ui: StateFlow<TaskListUiState> = useCase.observeAll()
        .map { all ->
            val visible = all.filter { it.deletedAt == null }
            val active = visible.filter { it.status != TaskStatus.ARCHIVED }
                .sortedWith(compareBy({ it.status == TaskStatus.DONE }, { it.priority }, { it.createdAt }))
            val archived = visible.filter { it.status == TaskStatus.ARCHIVED }
            if (active.isEmpty() && archived.isEmpty()) TaskListUiState.Empty as TaskListUiState
            else TaskListUiState.Content(active, archived)
        }
        .catch { emit(TaskListUiState.Error(it.message ?: "任务加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState.Loading)

    fun restore(task: Task) {
        viewModelScope.launch { useCase.restore(task, System.currentTimeMillis()) }
    }

    fun reopen(task: Task) {
        viewModelScope.launch { useCase.setDone(task, false, System.currentTimeMillis()) }
    }
}
