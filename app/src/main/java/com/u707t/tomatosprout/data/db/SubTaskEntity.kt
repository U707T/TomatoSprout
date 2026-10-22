package com.u707t.tomatosprout.data.db

import androidx.room.ColumnInfo
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
    /** v6：记录级合并时间戳。旧数据回填为 createdAt。 */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
    /** v7：软删除墓碑；null = 未删除。 */
    val deletedAt: Long? = null,
)
