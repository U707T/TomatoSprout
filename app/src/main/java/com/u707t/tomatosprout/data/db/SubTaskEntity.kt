package com.u707t.tomatosprout.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parentTaskId")],
)
data class SubTaskEntity(
    @PrimaryKey val id: String,
    val parentTaskId: String,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
)
