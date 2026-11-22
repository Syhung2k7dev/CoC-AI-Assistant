package dev.syhung.cocassistant.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity LearnedSkill - Lưu chi tiết skill đã học
 */
@Entity(tableName = "learned_skills", indices = [
    Index(value = ["skillId"], name = "idx_learned_skill_id"),
    Index(value = ["sceneId"], name = "idx_learned_skill_scene_id")
])
data class LearnedSkill(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var skillId: Long = 0,
    var sceneId: String = "",
    var caslScript: String = "",
    var anchorIds: List<Long> = emptyList(),
    var preConditionCheck: String = "",
    var postConditionCheck: String = "",
    var isTestOnly: Boolean = false,
    var version: Int = 0,
    var learnedDate: Date? = null,
    var executionCount: Long = 0,
    var successCount: Long = 0,
    var successRate: Float = 1f,
    var selfHealCount: Int = 0,
    var lastErrorReason: String? = null,
    var selfHealWarningCount: Int = 0,
    var templateImageURL: String = ""
)
