package dev.syhung.cocassistant.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity ExecutionLog - Lịch sử thao tác thực thi
 */
@Entity(tableName = "execution_logs", indices = [
    Index(value = ["skillId"], name = "idx_execution_log_skill_id"),
    Index(value = ["worldStateId"], name = "idx_world_state_id"),
    Index(value = ["actionType"], name = "idx_action_type")
])
data class ExecutionLog(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var skillId: Long = 0,
    var worldStateId: Long = 0,
    var actionType: String = "",
    var x: Float = 0f,
    var y: Float = 0f,
    var description: String = "",
    var result: String = "SUCCESS",
    var failureReason: String? = null,
    var executionTimeMillis: Long = 0,
    var evidencePath: String = "",
    var executionDate: Date? = null,
    var isImportantAction: Boolean = false
)
