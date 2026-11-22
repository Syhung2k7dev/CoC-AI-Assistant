package dev.syhung.cocassistant.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity WorldLayoutAnchor - Lưu trữ các điểm neo, vùng ROI, công trình/nút đã nhận diện
 */
@Entity(tableName = "world_layout_anchors", indices = [
    Index(value = ["sceneId"], name = "idx_anchor_scene_id")
])
data class WorldLayoutAnchor(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var sceneId: String = "",
    var category: String = "",
    var label: String = "",
    var x: Float = 0f,
    var y: Float = 0f,
    var scale: Int = 0,
    var confidenceThreshold: Float = 0f,
    var templateImagePath: String = "",
    var lastUpdateTime: Date? = null,
    var existsInCurrentUI: Boolean = true
) {
    data class ROI(
        var left: Int = 0,
        var top: Int = 0,
        var right: Int = 0,
        var bottom: Int = 0
    )
}
