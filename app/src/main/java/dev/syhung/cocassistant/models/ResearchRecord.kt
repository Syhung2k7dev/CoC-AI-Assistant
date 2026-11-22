package dev.syhung.cocassistant.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity ResearchRecord - Ghi chép quá trình nghiên cứu (Deep Research)
 */
@Entity(tableName = "research_records", indices = [
    Index(value = ["skillId"], name = "idx_research_record_skill_id"),
    Index(value = ["researchType"], name = "idx_research_type"),
    Index(value = ["researchSteps"], name = "idx_research_steps")
])
data class ResearchRecord(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var taskId: Long = 0,
    var skillId: Long = 0,
    var query: String = "",
    var status: String = "",
    var researchType: String = "",
    var oldCaslScript: String = "",
    var newProposedCaslScript: String = "",
    var researchSteps: Int = 0,
    var researchResult: String = "SUCCESS",
    var problemDescription: String = "",
    var knowledgeSource: String = "",
    var screenshotPath: String = "",
    var startTime: Date? = null,
    var endTime: Date? = null,
    var violationReason: String? = null,
    var consecutiveFailCount: Int = 0
)
