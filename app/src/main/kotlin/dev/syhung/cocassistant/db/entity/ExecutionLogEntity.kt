package dev.syhung.cocassistant.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "execution_log")
data class ExecutionLogEntity(
    @PrimaryKey val id: Long = 0,
    val skillId: String,
    val actionType: String,
    val success: Boolean,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val errorDetails: String? = null
)