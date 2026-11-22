package dev.syhung.cocassistant.db.dao

import dev.syhung.cocassistant.db.entity.ExecutionLogEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExecutionLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExecutionLog(log: ExecutionLogEntity)

    @Query("DELETE FROM execution_log WHERE id = :id")
    suspend fun deleteExecutionLog(id: Long)

    @Query("SELECT * FROM execution_log")
    fun getAllExecutionLogs(): List<ExecutionLogEntity>

    @Query("SELECT * FROM execution_log WHERE skillId = :skillId ORDER BY timestamp DESC")
    fun getExecutionLogsBySkillId(skillId: String): List<ExecutionLogEntity>
}