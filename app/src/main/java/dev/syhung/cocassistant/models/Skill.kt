package dev.syhung.cocassistant.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File
import java.util.Date

/**
 * Entity Skill - Lưu trữ kỹ năng tự động hóa CoC
 */
@Entity(tableName = "skills", indices = [
    Index(value = ["sceneId"], name = "idx_skill_scene_id"),
    Index(value = ["version"], name = "idx_version")
])
data class Skill(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var name: String = "",
    var description: String = "",
    var caslScript: String = "",
    // Note: Assuming a TypeConverter for List<Anchor> and List<TemplateImage> is configured
    // in AppDatabase as these were complex types in Java implementation.
    var anchors: List<Anchor> = emptyList(),
    var templateImages: List<TemplateImage> = emptyList(),
    var sceneId: String = "",
    var conditionType: String = "",
    var condition: String = "",
    var postConditionCheck: String = "",
    var version: Int = 0,
    var selfHealCount: Int = 0,
    var executionCount: Long = 0,
    var successCount: Long = 0,
    var successRate: Float = 1f,
    var creationDate: Date? = null,
    var lastUpdateTime: Date? = null,
    var caslScriptHash: String = "",
    var status: String = "ACTIVE"
) {
    companion object {
        const val TEMPLATES_BASE_DIR = "/data/data/dev.syhung.cocassistant/filesDir/vision_artifacts/templates/"
    }

    data class TemplateImage(
        @PrimaryKey(autoGenerate = true)
        var id: Long = 0,
        var imageName: String = "",
        var sha256Hash: ByteArray = byteArrayOf(),
        var uploadDate: Date? = null,
        var confidenceThreshold: Float = 0.7f
    )

    data class Anchor(
        @PrimaryKey(autoGenerate = true)
        var id: Long = 0,
        var name: String = "",
        var category: String = "",
        var label: String = "",
        var x: Float = 0f,
        var y: Float = 0f,
        var scale: Int = 0,
        var confidenceThreshold: Float = 0f,
        var imageFile: File? = null
    )
}
