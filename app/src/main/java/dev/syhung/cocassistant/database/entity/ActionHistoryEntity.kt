package dev.syhung.cocassistant.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "action_history")
data class ActionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionName: String,
    val anchorId: String?,
    val x: Float,
    val y: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean
)
