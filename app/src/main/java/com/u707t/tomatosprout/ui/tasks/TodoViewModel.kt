package com.u707t.tomatosprout.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.u707t.tomatosprout.domain.CheckInResult
import com.u707t.tomatosprout.domain.CheckInUseCase
import com.u707t.tomatosprout.domain.ReminderRepository
import com.u707t.tomatosprout.domain.ReminderRule
import com.u707t.tomatosprout.domain.SettingsRepository
import com.u707t.tomatosprout.domain.StatsUseCase
import com.u707t.tomatosprout.domain.SubTaskRepository
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.domain.TaskUseCase
import com.u707t.tomatosprout.domain.attributedLocalDate
import com.u707t.tomatosprout.notify.ReminderScheduler
import com.u707t.tomatosprout.ui.focus.FocusEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val useCase: TaskUseCase,
    private val stats: StatsUseCase,
    private val subTasks: SubTaskRepository,
    private val checkInUseCase: CheckInUseCase,
    private val reminders: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val settingsRepo: SettingsRepository,
    focusEvents: FocusEventBus,
) : ViewModel() {
    private val quickId = MutableStateFlow<String?>(null)
    private val refreshTick = MutableStateFlow(0)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** 排序偏好存在 DataStore 里：换设备不自带，重装 / 清数据回到手动。 */
    val sort: StateFlow<TaskSort> = settingsRepo.settings
        .map { TaskSort.of(it.taskSort) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskSort.DEFAULT)

    fun setSort(mode: TaskSort) {
        viewModelScope.launch {
            settingsRepo.update { if (it.taskSort == mode.key) it else it.copy(taskSort = mode.key) }
        }
    }

    /** 已完成的任务默认留在列表底部；打开后只看还没做完的。 */
    val hideDone: StateFlow<Boolean> = settingsRepo.settings
        .map { it.hideDone }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setHideDone(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { if (it.hideDone == enabled) it else it.copy(hideDone = enabled) }
        }
    }

    init {
        // 专注页结算后主动刷新首页，并给出一次性反馈（与 Room Flow 双保险）
        viewModelScope.launch {
            focusEvents.events.collect { event ->
                refreshTick.update { it + 1 }
                val minutes = max(1, event.actualSeconds / 60)
                messages.emit(
                    when {
                        event.discarded -> "本次专注未记录：${event.discardReason ?: "已丢弃"}"
                        event.interrupted -> "已记录中断 $minutes 分钟，不计入打卡"
                        event.checkInWritten && event.streak > 0 ->
                            "专注 $minutes 分钟 · 打卡成功 · 连续 ${event.streak} 天"
                        event.checkInWritten -> "专注 $minutes 分钟 · 打卡成功"
                        else -> "专注 $minutes 分钟已记录"
                    },
                )
            }
        }
    }

    val ui: StateFlow<TodoUiState> = combine(
        useCase.observeAll(),
        stats.observe(),
        subTasks.observeAll(),
        quickId,
        refreshTick,
    ) { allTasks, stat, allSubTasks, selectedId, _ ->
        // 用 runCatching 而不是 .catch：catch 会让整条 flow 终止，出错后「重试」就再也不会恢复
        runCatching {
        val zone = ZoneId.systemDefault()
        val today = stat.today
        val visible = allTasks.filter { it.deletedAt == null && it.parentId == null }
        val active = visible.sortedWith(
            compareBy(
                { it.status == TaskStatus.DONE },
                { it.sortOrder },
                { it.priority },
                { it.createdAt },
            ),
        )
        // 一次性分组，避免在任务循环里反复 filter 全部专注 / 打卡 / 子任务（O(任务数 × 记录数)）
        val completedByTask = stat.sessions
            .filter { it.completed }
            .groupBy { it.taskId }
        val todayChecksByTask = stat.checks
            .filter { it.date == today }
            .groupBy { it.taskId }
        val subTasksByParent = allSubTasks.groupBy { it.parentTaskId }
        val habitsByTask = stat.habits.associateBy { it.task.id }
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val weekDates = (0..6).map { weekStart.plusDays(it.toLong()) }
        val cards = active.map { task ->
            val todayChecks = todayChecksByTask[task.id].orEmpty()
            // 历史专注汇总与「今日专注」都基于 completedByTask，口径只有一份
            val completedSessions = completedByTask[task.id].orEmpty()
            val todayCompleted = completedSessions.filter { it.attributedLocalDate(zone) == today }
            val weekMinutes = weekDates.map { date ->
                completedSessions.filter { it.attributedLocalDate(zone) == date }
                    .sumOf { it.actualSeconds } / 60
            }
            val children = subTasksByParent[task.id].orEmpty()
            val habit = habitsByTask[task.id]
            val checkTarget = max(1, habit?.rule?.targetCountPerPeriod ?: 1)
            val checkCount = todayChecks.sumOf { max(1, it.count) }
            TodoCardUi(
                task = task,
                timerLabel = when (task.timerMode) {
                    "none" -> "按次打卡"
                    "countup" -> "正向计时"
                    else -> "倒计时 · ${task.focusMinutes} 分钟"
                },
                todayFocusCount = todayCompleted.size,
                todayFocusMinutes = todayCompleted.sumOf { it.actualSeconds } / 60,
                totalFocusCount = completedSessions.size,
                totalFocusSeconds = completedSessions.sumOf { it.actualSeconds.toLong() },
                weekMinutes = weekMinutes,
                subtaskDone = children.count { it.done },
                subtaskTotal = children.size,
                goal = stat.goalProgress[task.id],
                checkedToday = if (task.type == TaskType.HABIT) {
                    checkCount >= checkTarget
                } else {
                    todayChecks.isNotEmpty()
                },
                todayCheckInCount = checkCount,
                habitTarget = checkTarget,
                streak = habit?.currentStreak ?: 0,
            )
        }
        val selected = cards.firstOrNull { it.task.id == selectedId }
        val selectedIndex = cards.indexOfFirst { it.task.id == selectedId }
        TodoUiState(
            loading = false,
            today = today,
            cards = cards,
            quickAction = selected?.let {
                QuickActionUiState(
                    taskId = it.task.id,
                    title = it.task.title,
                    icon = it.task.icon,
                    color = it.task.color,
                    todayFocusCount = it.todayFocusCount,
                    totalFocusCount = it.totalFocusCount,
                    totalFocusSeconds = it.totalFocusSeconds,
                    weekMinutes = it.weekMinutes,
                    canMoveUp = selectedIndex > 0,
                    canMoveDown = selectedIndex >= 0 && selectedIndex < cards.lastIndex,
                    countBased = it.isCountBased,
                    isDone = it.isDone,
                )
            },
        )
        }.getOrElse { TodoUiState(loading = false, error = it.message ?: "任务列表加载失败") }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoUiState())

    fun refresh() {
        refreshTick.update { it + 1 }
    }

    fun showQuickActions(taskId: String) {
        quickId.value = taskId
    }

    fun dismissQuickActions() {
        quickId.value = null
    }

    fun toggleCheckIn(task: Task) {
        if (task.timerMode != "none") {
            viewModelScope.launch {
                messages.emit("这是计时任务，请点「开始」完成一次专注后自动打卡")
            }
            return
        }
        viewModelScope.launch {
            when (val result = checkInUseCase.toggleToday(task)) {
                is CheckInResult.Checked ->
                    messages.emit(
                        if (result.streak > 0) "已打卡 · 连续 ${result.streak} 天" else "已打卡",
                    )
                // 撤销打卡的反馈由首页的 Snackbar（带「撤销」）负责，避免两条提示互相覆盖
                is CheckInResult.Undone -> Unit
                is CheckInResult.Rejected -> messages.emit(result.reason)
            }
        }
    }

    fun markDone(task: Task, done: Boolean) {
        viewModelScope.launch { useCase.setDone(task, done, System.currentTimeMillis()) }
    }

    /** 精确撤销今天的打卡（不走 toggle），供首页撤销条使用。 */
    fun undoCheckIn(task: Task) {
        viewModelScope.launch {
            when (val result = checkInUseCase.undoToday(task)) {
                is CheckInResult.Rejected -> messages.emit(result.reason)
                else -> Unit
            }
        }
    }

    fun cycleColor(task: Task) {
        val palette = listOf<Long>(
            0xFFFF6B6BL, 0xFFF3B58AL, 0xFFE5B95CL, 0xFF8FAE83L,
            0xFF7FB5B5L, 0xFF8C9ED6L, 0xFFC59ACFL,
        )
        val index = palette.indexOf(task.color).let { if (it < 0) 0 else it }
        val next = palette[(index + 1) % palette.size]
        viewModelScope.launch {
            useCase.updateTask(task.copy(color = next, updatedAt = System.currentTimeMillis()))
        }
    }

    fun move(taskId: String, delta: Int) {
        viewModelScope.launch { useCase.moveTask(taskId, delta, System.currentTimeMillis()) }
    }

    /** 快捷面板「提醒」用：读当前任务的提醒时间，没有就返回 null。 */
    suspend fun reminderTime(taskId: String): String? =
        reminders.forTask(taskId)?.takeIf { it.enabled && it.deletedAt == null }?.timeOfDay

    /**
     * 直接设置 / 关闭提醒，不必进编辑页。
     *
     * 复用同一条规则（含墓碑 id），因为 `taskId` 上有唯一索引；
     * 新时间排期前先取消旧 Alarm，避免两条提醒同时响。
     */
    fun setReminder(task: Task, timeOfDay: String?) {
        viewModelScope.launch {
            val existing = reminders.forTask(task.id)
            if (timeOfDay == null) {
                if (existing != null) {
                    reminders.deleteForTask(task.id, System.currentTimeMillis())
                    reminderScheduler.cancel(existing.id)
                }
                messages.emit("已关闭「${task.title}」的提醒")
                return@launch
            }
            val now = System.currentTimeMillis()
            val saved = reminders.saveForTask(
                ReminderRule(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    taskId = task.id,
                    timeOfDay = timeOfDay,
                    weekdaysMask = existing?.weekdaysMask ?: 0b1111111,
                    enabled = true,
                    updatedAt = now,
                    deletedAt = null,
                ),
            )
            reminderScheduler.cancel(saved.id)
            reminderScheduler.schedule(saved)
            messages.emit("提醒已设为 $timeOfDay")
        }
    }

    /**
     * 物理删除在 v1.6 中改为软删除：任务立即从首页消失，
     * 但 CheckIn / FocusSession 保留，历史页显示「[已删除] 原任务名」。
     *
     * [announce] 为 false 时由调用方（滑动删除 + 撤销条）负责提示，
     * 避免「撤销窗口还没结束就先弹一条已删除」。
     */
    fun delete(task: Task, announce: Boolean = true) {
        viewModelScope.launch {
            // 先取提醒规则 id，删除后取消已排期的 Alarm，避免幽灵提醒
            val reminder = reminders.forTask(task.id)
            useCase.delete(task, System.currentTimeMillis())
            reminder?.let { reminderScheduler.cancel(it.id) }
            quickId.value = null
            if (announce) messages.emit("已删除「${task.title}」，历史记录已保留")
        }
    }
}
