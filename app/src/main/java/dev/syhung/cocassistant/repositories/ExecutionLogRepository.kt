package dev.syhung.cocassistant.repositories

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.syhung.cocassistant.models.ExecutionLog
import java.util.Date

@Dao
interface ExecutionLogRepository {
    @Insert
    suspend fun insert(log: ExecutionLog): Long

    @Update
    suspend fun update(log: ExecutionLog)

    @Delete
    suspend fun delete(log: ExecutionLog)

    @Query("SELECT * FROM execution_logs WHERE skillId = :skillId ORDER BY executionDate DESC")
    suspend fun findBySkillID(skillId: Long): List<ExecutionLog>

    @Query("SELECT * FROM execution_logs WHERE result = 'SUCCESS' AND skillId = :skillId ORDER BY executionDate DESC LIMIT :limit")
    suspend fun findSuccessLogs(skillId: Long, limit: Int): List<ExecutionLog>

    @Query("SELECT * FROM execution_logs WHERE result = 'FAILED' AND skillId = :skillId ORDER BY executionDate DESC LIMIT :limit")
    suspend fun findFailedLogs(skillId: Long, limit: Int): List<ExecutionLog>

    @Query("DELETE FROM execution_logs WHERE executionDate < :cutoffDate")
    suspend fun cleanupOldLogs(cutoffDate: Date)

    @Query("SELECT * FROM execution_logs WHERE actionType = :actionType ORDER BY executionDate DESC LIMIT 1")
    suspend fun findByActionType(actionType: String): ExecutionLog?
}
