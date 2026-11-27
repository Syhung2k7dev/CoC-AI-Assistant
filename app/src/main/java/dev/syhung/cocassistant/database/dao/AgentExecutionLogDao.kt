package dev.syhung.cocassistant.database.dao

import androidx.room.Dao
import androidx.room.Query
import dev.syhung.cocassistant.database.entity.AgentExecutionLogEntity

@Dao
interface AgentExecutionLogDao {
    @Query("SELECT * FROM agent_execution_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentExecutionLogs(limit: Int): List<AgentExecutionLogEntity>
}
