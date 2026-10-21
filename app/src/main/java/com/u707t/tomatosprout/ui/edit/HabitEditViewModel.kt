package com.u707t.tomatosprout.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.Frequency
import com.u707t.tomatosprout.domain.GoalRule
import com.u707t.tomatosprout.domain.HabitRule
import com.u707t.tomatosprout.domain.ReminderRule
import com.u707t.tomatosprout.domain.SubTask
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.domain.TaskUseCase
import com.u707t.tomatosprout.notify.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class EditState(
    val taskId: String? = null,
    val type: TaskType = TaskType.HABIT,
    val title: String = "",
    val note: String = "",
    val icon: String = "🌱",
    val color: Long = 0xFFFF6B6B,
    val priority: Int = 1,
    val dueAt: Long? = null,
    val frequency: Frequency = Frequency.DAILY,
    val targetCount: Int = 1,
    val weekdaysMask: Int = 0b0011111,
    val intervalDays: Int = 1,
    val startDate: LocalDate = LocalDate.now(),
    val reminderEnabled: Boolean = false,
    val reminderTime: String = "20:00",
    val goalTarget: String = "",
    val goalEndDate: LocalDate = LocalDate.now().plusDays(30),
    val timerMode: String = "none",
    val focusMinutes: Int = 25,
    val subtasks: List<SubTask> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val isNew: Boolean get() = taskId == null
}

@HiltViewModel
class HabitEditViewModel @Inject constructor(
    private val useCase: TaskUseCase,
    private val scheduler: ReminderScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val taskId: String? = savedStateHandle.get<String>("taskId")
    private val prefillTitle: String? = savedStateHandle.get<String>("prefillTitle")
    private val prefillType: String? = savedStateHandle.get<String>("prefillType")
    private val prefillTimerMode: String? = savedStateHandle.get<String>("prefillTimerMode")
    private val prefillMinutes: Int? = savedStateHandle.get<Int>("prefillMinutes")
    private val _state = MutableStateFlow(EditState(taskId = taskId))
    val state: StateFlow<EditState> = _state.asStateFlow()
    val saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var createdAt: Long = System.currentTimeMillis()
    private var existingStatus: TaskStatus = TaskStatus.TODO
    private var existingSortOrder: Int = 0
    private var existingReminderId: String? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (taskId == null) {
            _state.update {
                it.copy(
                    loading = false,
                    title = prefillTitle ?: it.title,
                    type = prefillType?.let { value -> runCatching { TaskType.valueOf(value) }.getOrNull() } ?: it.type,
                    timerMode = prefillTimerMode ?: it.timerMode,
                    focusMinutes = prefillMinutes?.coerceIn(5, 180) ?: it.focusMinutes,
                )
            }
            return
        }
        val task = useCase.task(taskId)
        if (task == null) {
            _state.update { it.copy(loading = false, error = "任务不存在") }
            return
        }
        createdAt = task.createdAt
        existingStatus = task.status
        existingSortOrder = task.sortOrder
        val habit = useCase.habitRule(taskId)
        val goal = useCase.goalRule(taskId)
        val reminder = useCase.reminder(taskId)
        existingReminderId = reminder?.id
        val children = useCase.subtasks(taskId)
        _state.update {
            it.copy(
                taskId = task.id,
                type = task.type,
                title = task.title,
                note = task.note.orEmpty(),
                icon = task.icon ?: "🌱",
                color = task.color,
                priority = task.priority,
                dueAt = task.dueAt,
                frequency = habit?.frequency ?: Frequency.DAILY,
                targetCount = habit?.targetCountPerPeriod ?: 1,
                weekdaysMask = habit?.weekdaysMask ?: 0b0011111,
                intervalDays = habit?.intervalDays ?: 1,
                startDate = habit?.startDate ?: LocalDate.now(),
                reminderEnabled = reminder?.enabled ?: false,
                reminderTime = reminder?.timeOfDay ?: "20:00",
                goalTarget = goal?.targetTotalCount?.toString().orEmpty(),
                goalEndDate = goal?.endDate ?: LocalDate.now().plusDays(30),
                timerMode = task.timerMode,
                focusMinutes = task.focusMinutes,
                subtasks = children,
                loading = false,
            )
        }
    }

    fun edit(transform: (EditState) -> EditState) = _state.update(transform)

    fun setType(type: TaskType) = edit { state ->
        val defaultTimer = when (type) {
            TaskType.POMODORO -> "countdown"
            TaskType.HABIT, TaskType.GOAL, TaskType.TODO -> "none"
        }
        state.copy(
            type = type,
            timerMode = if (state.isNew) defaultTimer else state.timerMode,
        )
    }
    fun setTitle(value: String) = edit { it.copy(title = value.take(60)) }
    fun setNote(value: String) = edit { it.copy(note = value.take(500)) }
    fun setIcon(value: String) = edit { it.copy(icon = value) }
    fun setColor(value: Long) = edit { it.copy(color = value) }
    fun setPriority(value: Int) = edit { it.copy(priority = value) }
    fun setFrequency(value: Frequency) = edit { it.copy(frequency = value) }
    fun setTargetCount(value: Int) = edit { it.copy(targetCount = value.coerceIn(1, 20)) }
    fun setWeekdaysMask(value: Int) = edit { it.copy(weekdaysMask = value) }
    fun setIntervalDays(value: Int) = edit { it.copy(intervalDays = value.coerceIn(1, 60)) }
    fun setStartDate(value: LocalDate) = edit { it.copy(startDate = value) }
    fun setReminderEnabled(value: Boolean) = edit { it.copy(reminderEnabled = value) }
    fun setReminderTime(value: String) = edit { it.copy(reminderTime = value) }
    fun setGoalTarget(value: String) = edit { it.copy(goalTarget = value.filter { c -> c.isDigit() }.take(7)) }
    fun setGoalEndDate(value: LocalDate) = edit { it.copy(goalEndDate = value) }
    fun setDueAt(value: Long?) = edit { it.copy(dueAt = value) }
    fun setTimerMode(value: String) = edit { it.copy(timerMode = value) }
    fun setFocusMinutes(value: Int) = edit { it.copy(focusMinutes = value.coerceIn(5, 180)) }
    fun clearError() = edit { it.copy(error = null) }

    fun save() {
        val current = _state.value
        if (current.title.isBlank()) {
            edit { it.copy(error = "给这件事起个名字吧") }
            return
        }
        if (current.type == TaskType.HABIT && current.frequency == Frequency.WEEKLY && current.weekdaysMask == 0) {
            edit { it.copy(error = "每周模式至少选择一个星期") }
            return
        }
        if (current.type == TaskType.GOAL && current.goalEndDate.isBefore(current.startDate)) {
            edit { it.copy(error = "截止日期不能早于开始日期") }
            return
        }
        if (current.saving) return
        edit { it.copy(saving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = current.taskId ?: UUID.randomUUID().toString()
            val task = Task(
                id = id,
                type = current.type,
                title = current.title.trim(),
                note = current.note.trim().takeIf { it.isNotEmpty() },
                color = current.color,
                icon = current.icon,
                priority = current.priority,
                dueAt = current.dueAt,
                createdAt = createdAt,
                updatedAt = now,
                status = existingStatus,
                parentId = useCase.task(id)?.parentId,
                sortOrder = existingSortOrder,
                timerMode = current.timerMode,
                focusMinutes = current.focusMinutes,
            )
            val habit = if (current.type == TaskType.HABIT) {
                HabitRule(
                    taskId = id,
                    frequency = current.frequency,
                    targetCountPerPeriod = current.targetCount,
                    weekdaysMask = current.weekdaysMask,
                    intervalDays = current.intervalDays,
                    startDate = current.startDate,
                )
            } else null
            val goal = if (current.type == TaskType.GOAL) {
                GoalRule(
                    taskId = id,
                    startDate = current.startDate,
                    endDate = current.goalEndDate,
                    targetTotalCount = current.goalTarget.toIntOrNull(),
                )
            } else null
            val reminder = if (current.reminderEnabled) {
                ReminderRule(
                    id = existingReminderId ?: UUID.randomUUID().toString(),
                    taskId = id,
                    timeOfDay = current.reminderTime,
                    weekdaysMask = when (current.frequency) {
                        Frequency.WEEKLY -> current.weekdaysMask
                        Frequency.WEEKDAYS -> 0b0011111
                        else -> 0b1111111
                    },
                    enabled = true,
                )
            } else null
            runCatching {
                useCase.save(task, habit, goal, reminder)
                // 新建任务时先暂存子任务，主任务落库后再写入
                current.subtasks.filter { it.parentTaskId.isBlank() }.forEach { draft ->
                    val created = useCase.addSubtask(id, draft.title, now)
                    if (draft.done) useCase.setSubtaskDone(created, true)
                }
            }.onFailure { e ->
                edit { it.copy(saving = false, error = e.message ?: "保存失败") }
                return@launch
            }
            // 重新排期提醒（旧规则若存在先取消）
            existingReminderId?.let { scheduler.cancel(it) }
            if (reminder != null) scheduler.schedule(reminder)
            edit { it.copy(saving = false) }
            saved.emit(Unit)
        }
    }

    /**
     * v1.6：取消归档，直接软删除任务。
     * CheckIn / FocusSession 不级联删除，历史页会显示「[已删除] 原任务名」。
     */
    fun delete() {
        val current = _state.value
        val id = current.taskId ?: return
        viewModelScope.launch {
            useCase.task(id)?.let { useCase.delete(it, System.currentTimeMillis()) }
            existingReminderId?.let { scheduler.cancel(it) }
            saved.emit(Unit)
        }
    }

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        val current = _state.value
        val parent = current.taskId
        if (parent == null) {
            val draft = SubTask(
                id = UUID.randomUUID().toString(),
                parentTaskId = "",
                title = title.trim(),
                sortOrder = current.subtasks.size,
                createdAt = System.currentTimeMillis(),
            )
            edit { it.copy(subtasks = it.subtasks + draft) }
            return
        }
        viewModelScope.launch {
            useCase.addSubtask(parent, title, System.currentTimeMillis())
            val children = useCase.subtasks(parent)
            edit { it.copy(subtasks = children) }
        }
    }

    fun toggleSubtask(subTask: SubTask) {
        if (subTask.parentTaskId.isBlank()) {
            edit { state -> state.copy(subtasks = state.subtasks.map { if (it.id == subTask.id) it.copy(done = !it.done) else it }) }
            return
        }
        viewModelScope.launch {
            useCase.setSubtaskDone(subTask, !subTask.done)
            val children = useCase.subtasks(subTask.parentTaskId)
            edit { it.copy(subtasks = children) }
        }
    }

    fun deleteSubtask(subTask: SubTask) {
        if (subTask.parentTaskId.isBlank()) {
            edit { state -> state.copy(subtasks = state.subtasks.filterNot { it.id == subTask.id }) }
            return
        }
        viewModelScope.launch {
            useCase.deleteSubtask(subTask)
            val children = useCase.subtasks(subTask.parentTaskId)
            edit { it.copy(subtasks = children) }
        }
    }
}
