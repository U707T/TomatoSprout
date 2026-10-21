package com.u707t.tomatosprout.data.db

import com.u707t.tomatosprout.domain.Badge
import com.u707t.tomatosprout.domain.CheckIn
import com.u707t.tomatosprout.domain.FocusSession
import com.u707t.tomatosprout.domain.Frequency
import com.u707t.tomatosprout.domain.GoalRule
import com.u707t.tomatosprout.domain.HabitRule
import com.u707t.tomatosprout.domain.ReminderRule
import com.u707t.tomatosprout.domain.SubTask
import com.u707t.tomatosprout.domain.Task
import com.u707t.tomatosprout.domain.TaskStatus
import com.u707t.tomatosprout.domain.TaskType
import com.u707t.tomatosprout.domain.WishItem
import java.time.LocalDate

fun TaskEntity.toDomain(): Task {
    // 旧备份/旧数据里的 ARCHIVED 不是合法 TaskStatus：按「用户原本想隐藏它」处理为软删除
    val legacyArchived = status == "ARCHIVED"
    return Task(
        id = id,
        type = runCatching { TaskType.valueOf(type) }.getOrDefault(TaskType.TODO),
        title = title,
        note = note,
        color = color,
        icon = icon,
        priority = priority,
        dueAt = dueAt,
        repeatRule = repeatRule,
        estimatedPomos = estimatedPomos,
        completedPomos = completedPomos,
        status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.TODO),
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt ?: if (legacyArchived) updatedAt.coerceAtLeast(1L) else null,
        parentId = parentId,
        sortOrder = sortOrder,
        timerMode = timerMode ?: "countdown",
        focusMinutes = focusMinutes ?: 25,
    )
}

fun Task.toEntity() = TaskEntity(
    id = id,
    type = type.name,
    title = title,
    note = note,
    color = color,
    icon = icon,
    priority = priority,
    dueAt = dueAt,
    repeatRule = repeatRule,
    estimatedPomos = estimatedPomos,
    completedPomos = completedPomos,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    parentId = parentId,
    sortOrder = sortOrder,
    timerMode = timerMode,
    focusMinutes = focusMinutes,
)

fun SubTaskEntity.toDomain() = SubTask(id, parentTaskId, title, done, sortOrder, createdAt)

fun SubTask.toEntity() = SubTaskEntity(id, parentTaskId, title, done, sortOrder, createdAt)

fun HabitRuleEntity.toDomain() = HabitRule(
    taskId = taskId,
    frequency = runCatching { Frequency.valueOf(frequency) }.getOrDefault(Frequency.DAILY),
    targetCountPerPeriod = targetCountPerPeriod,
    targetDaysPerWeek = targetDaysPerWeek,
    weekdaysMask = weekdaysMask,
    intervalDays = intervalDays,
    startDate = runCatching { LocalDate.parse(startDate) }.getOrDefault(LocalDate.now()),
)

fun HabitRule.toEntity() = HabitRuleEntity(
    taskId = taskId,
    frequency = frequency.name,
    targetCountPerPeriod = targetCountPerPeriod,
    targetDaysPerWeek = targetDaysPerWeek,
    weekdaysMask = weekdaysMask,
    intervalDays = intervalDays,
    startDate = startDate.toString(),
)

fun GoalRuleEntity.toDomain() = GoalRule(
    taskId = taskId,
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    targetTotalCount = targetTotalCount,
    targetTotalSeconds = targetTotalSeconds,
)

fun GoalRule.toEntity() = GoalRuleEntity(
    taskId = taskId,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    targetTotalCount = targetTotalCount,
    targetTotalSeconds = targetTotalSeconds,
)

fun CheckInEntity.toDomain() = CheckIn(
    id = id,
    taskId = taskId,
    date = LocalDate.parse(checkDate),
    count = count,
    note = note,
    mood = mood,
    source = source,
    isMakeup = isMakeup,
    createdAt = createdAt,
    deletedAt = deletedAt,
)

fun CheckIn.toEntity() = CheckInEntity(
    id = id,
    taskId = taskId,
    checkDate = date.toString(),
    count = count,
    note = note,
    mood = mood,
    source = source,
    isMakeup = isMakeup,
    createdAt = createdAt,
    deletedAt = deletedAt,
)

fun FocusSessionEntity.toDomain() = FocusSession(
    id = id,
    taskId = taskId,
    mode = mode,
    plannedSeconds = plannedSeconds,
    actualSeconds = actualSeconds,
    startedAt = startedAt,
    endedAt = endedAt,
    isMidnightShifted = isMidnightShifted,
    interruptionCount = interruptionCount,
    note = note,
    deletedAt = deletedAt,
    outcome = outcome,
    attributedDate = attributedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    checkInId = checkInId,
)

fun FocusSession.toEntity() = FocusSessionEntity(
    id = id,
    taskId = taskId,
    mode = mode,
    plannedSeconds = plannedSeconds,
    actualSeconds = actualSeconds,
    startedAt = startedAt,
    endedAt = endedAt,
    isMidnightShifted = isMidnightShifted,
    interruptionCount = interruptionCount,
    note = note,
    deletedAt = deletedAt,
    outcome = outcome,
    attributedDate = attributedDate?.toString(),
    checkInId = checkInId,
)

fun ReminderRuleEntity.toDomain() = ReminderRule(id, taskId, timeOfDay, weekdaysMask, enabled)

fun ReminderRule.toEntity() = ReminderRuleEntity(id, taskId, timeOfDay, weekdaysMask, enabled)

fun BadgeEntity.toDomain() = Badge(id, badgeKey, name, conditionJson, unlockedAt, description)

fun Badge.toEntity() = BadgeEntity(id, key, name, conditionJson, unlockedAt, description)

fun WishItemEntity.toDomain() = WishItem(id, content, status, createdAt, deletedAt)

fun WishItem.toEntity() = WishItemEntity(id, content, status, createdAt, deletedAt)
