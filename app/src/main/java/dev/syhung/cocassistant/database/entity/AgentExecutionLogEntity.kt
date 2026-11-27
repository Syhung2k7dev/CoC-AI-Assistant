package dev.syhung.cocassistant.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_execution_log")
data class AgentExecutionLogEntity(
    @PrimaryKey val id: Long,
    val skillId: String,
    val actionType: String,
    val success: Boolean,
    val durationMs: Long,
    val timestamp: Long,
    val errorDetails: String? = null
)
