package com.u707t.tomatosprout.domain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class TaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderRepository,
    private val subTasks: SubTaskRepository,
) {
    fun observeActive(): Flow<List<Task>> = tasks.observeActiveTasks()
    fun observeAll(): Flow<List<Task>> = tasks.observeAllTasks()

    suspend fun task(id: String): Task? = tasks.task(id)
    suspend fun updateTask(task: Task) = tasks.upsert(task)
    suspend fun habitRule(id: String): HabitRule? = tasks.habitRule(id)
    suspend fun goalRule(id: String): GoalRule? = tasks.goalRule(id)
    suspend fun reminder(taskId: String): ReminderRule? = reminders.forTask(taskId)
    fun observeSubtasks(parentId: String): Flow<List<SubTask>> = subTasks.observeForParent(parentId)
    suspend fun subtasks(parentId: String): List<SubTask> = subTasks.forParent(parentId)

    suspend fun addSubtask(parentId: String, title: String, at: Long): SubTask {
        val existing = subTasks.forParent(parentId)
        val item = SubTask(
            id = UUID.randomUUID().toString(),
            parentTaskId = parentId,
            title = title.trim(),
            done = false,
            sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            createdAt = at,
        )
        subTasks.upsert(item)
        refreshParent(parentId)
        return item
    }

    suspend fun setSubtaskDone(subTask: SubTask, done: Boolean) {
        subTasks.upsert(subTask.copy(done = done))
        refreshParent(subTask.parentTaskId)
    }

    suspend fun deleteSubtask(subTask: SubTask) {
        subTasks.delete(subTask.id)
        refreshParent(subTask.parentTaskId)
    }

    /** 任务卡片流中的上移/下移；首次移动时把隐式顺序固化为 sortOrder。 */
    suspend fun moveTask(taskId: String, delta: Int, at: Long) {
        val all = tasks.captureAll().tasks
            .filter { it.deletedAt == null && it.parentId == null }
            .sortedWith(compareBy({ it.sortOrder }, { it.priority }, { it.createdAt }))
        val index = all.indexOfFirst { it.id == taskId }
        val target = index + delta
        if (index < 0 || target !in all.indices) return
        val normalized = all.all { it.sortOrder == 0 }
        if (normalized) {
            all.forEachIndexed { i, task -> tasks.updateSortOrder(task.id, i + 1, at) }
        }
        val current = all[index]
        val swapped = all[target]
        val currentOrder = if (normalized) index + 1 else current.sortOrder
        val swappedOrder = if (normalized) target + 1 else swapped.sortOrder
        tasks.updateSortOrder(current.id, swappedOrder, at)
        tasks.updateSortOrder(swapped.id, currentOrder, at)
    }

    suspend fun save(
        task: Task,
        habitRule: HabitRule?,
        goalRule: GoalRule?,
        reminder: ReminderRule?,
    ) {
        tasks.upsert(task)
        if (habitRule != null) tasks.upsertHabitRule(habitRule) else tasks.deleteHabitRule(task.id)
        if (goalRule != null) tasks.upsertGoalRule(goalRule) else tasks.deleteGoalRule(task.id)
        if (reminder != null) reminders.upsert(reminder) else reminders.deleteForTask(task.id)
        if (task.parentId != null) refreshParent(task.parentId)
    }

    suspend fun setDone(task: Task, done: Boolean, at: Long) {
        tasks.setStatus(task.id, if (done) TaskStatus.DONE else TaskStatus.TODO, at)
        task.parentId?.let { refreshParent(it) }
    }

    suspend fun restore(task: Task, at: Long) {
        tasks.setStatus(task.id, TaskStatus.TODO, at)
    }

    /**
     * 删除任务（v1.6）：
     * - 任务本身软删除，列表立即消失；
     * - 关联的 CheckIn / FocusSession **不删除**，历史与统计继续保留；
     * - 删除提醒、习惯/目标规则与子任务，避免脏数据继续参与排期。
     */
    suspend fun delete(task: Task, at: Long) {
        tasks.softDelete(task.id, at)
        tasks.tasksByParent(task.id).forEach { child -> tasks.softDelete(child.id, at) }
        reminders.deleteForTask(task.id)
        tasks.deleteHabitRule(task.id)
        tasks.deleteGoalRule(task.id)
        subTasks.deleteForParent(task.id)
        task.parentId?.let { refreshParent(it) }
    }

    /** 子任务全部完成 → 父任务自动完成；任一取消则回退。 */
    suspend fun refreshParent(parentId: String) {
        val parent = tasks.task(parentId) ?: return
        if (parent.deletedAt != null) return
        val children = subTasks.forParent(parentId)
        if (children.isEmpty()) return
        val allDone = children.all { it.done }
        tasks.setStatus(parentId, if (allDone) TaskStatus.DONE else TaskStatus.TODO, System.currentTimeMillis())
    }
}

class BadgeUseCase @Inject constructor(
    private val badges: BadgeRepository,
    private val tasks: TaskRepository,
    private val checkIns: CheckInRepository,
    private val focus: FocusRepository,
    private val clock: AppClock = SystemAppClock,
) : BadgeRefresher {
    suspend fun ensureSeeded() {
        badges.seed(BadgeCatalog.seedBadges())
    }

    override suspend fun refresh(): List<Badge> {
        val all = badges.all()
        val bundle = tasks.captureAll()
        val stats = StatsCalculator.build(
            today = clock.today(),
            tasks = bundle.tasks,
            habitRules = bundle.habitRules,
            goalRules = bundle.goalRules,
            checkIns = checkIns.all(),
            focusSessions = focus.all(),
            badges = all,
            zone = clock.zone(),
        )
        val unlocked = all.filter { it.unlockedAt != null }.map { it.key }.toSet()
        val newly = BadgeEvaluator.newlyQualified(
            BadgeStats(
                currentStreak = stats.currentStreak,
                longestStreak = stats.longestStreak,
                totalCheckIns = stats.totalCheckIns,
                totalFocusSeconds = stats.totalFocusSeconds,
                totalPomos = stats.totalPomos,
                perfectDays = stats.perfectDays,
            ),
            unlocked,
        )
        if (newly.isEmpty()) return emptyList()
        val at = clock.nowMillis()
        badges.unlock(newly.map { it.key }.toSet(), at)
        return newly.map {
            Badge(it.key, it.key, it.name, "{}", at, it.description)
        }
    }
}

class HeatmapUseCase @Inject constructor(
    private val stats: StatsUseCase,
) {
    /** 两个独立 Flow，UI 切换维度时不需要重新查库（Room 会共享同一份冷流缓存）。 */
    fun observeCheckIns(): Flow<HeatmapData> = stats.observe()
        .map { data -> HeatmapData(HeatmapMetric.CHECK_INS, data.days.mapValues { it.value.checkIns.toLong() }) }
        .distinctUntilChanged()

    fun observeFocusDuration(): Flow<HeatmapData> = stats.observe()
        .map { data -> HeatmapData(HeatmapMetric.FOCUS_DURATION, data.days.mapValues { it.value.focusSeconds }) }
        .distinctUntilChanged()
}

class StatsUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val checkIns: CheckInRepository,
    private val focus: FocusRepository,
    private val badges: BadgeRepository,
    private val clock: AppClock = SystemAppClock,
) {
    /** 每分钟检查一次日期，跨午夜后让「今日」自动翻页；相同日期不重复发射。 */
    private fun dateTicker(): Flow<LocalDate> = flow {
        while (currentCoroutineContext().isActive) {
            emit(clock.today())
            delay(60_000)
        }
    }.distinctUntilChanged()

    fun observe(): Flow<StatsData> = combine(
        combine(tasks.observeAllTasks(), tasks.observeHabitRules(), tasks.observeGoalRules()) { t, h, g ->
            Triple(t, h, g)
        },
        checkIns.observeAll(),
        focus.observeAll(),
        badges.observeAll(),
        dateTicker(),
    ) { bundle, checks, sessions, badgeList, today ->
        StatsCalculator.build(
            today = today,
            tasks = bundle.first,
            habitRules = bundle.second,
            goalRules = bundle.third,
            checkIns = checks,
            focusSessions = sessions,
            badges = badgeList,
            zone = clock.zone(),
        )
    }
}
