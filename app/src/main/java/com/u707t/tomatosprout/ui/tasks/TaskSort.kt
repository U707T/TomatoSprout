package com.u707t.tomatosprout.ui.tasks

/**
 * 首页排序方式。
 *
 * 「手动」保留仓库给的顺序（已完成沉底 → sortOrder → 优先级 → 创建时间），
 * 其余三种只改展示顺序，不动数据库里的 sortOrder —— 切回来还是原来的手动顺序。
 */
enum class TaskSort(val key: String, val label: String, val hint: String) {
    MANUAL("manual", "手动排序", "按上移 / 下移调好的顺序"),
    PRIORITY("priority", "优先级", "四象限里越「紧急重要」越靠前"),
    DUE("due", "截止时间", "快到期的在前，没设截止的排最后"),
    FOCUS("focus", "今日专注", "今天投入时间多的在前"),
    ;

    companion object {
        val DEFAULT = MANUAL

        fun of(key: String?): TaskSort = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** 纯函数排序，方便单测；不改动入参。 */
fun sortCards(cards: List<TodoCardUi>, mode: TaskSort): List<TodoCardUi> = when (mode) {
    TaskSort.MANUAL -> cards
    TaskSort.PRIORITY -> cards.sortedWith(
        compareBy(
            { it.isDone },
            { it.task.priority },
            { it.task.dueAt ?: Long.MAX_VALUE },
            { it.task.sortOrder },
            { it.task.createdAt },
        ),
    )
    TaskSort.DUE -> cards.sortedWith(
        compareBy(
            { it.isDone },
            { it.task.dueAt ?: Long.MAX_VALUE },
            { it.task.priority },
            { it.task.sortOrder },
        ),
    )
    TaskSort.FOCUS -> cards.sortedWith(
        compareBy(
            { it.isDone },
            { -it.todayFocusMinutes },
            { -it.todayCheckInCount },
            { it.task.sortOrder },
        ),
    )
}
