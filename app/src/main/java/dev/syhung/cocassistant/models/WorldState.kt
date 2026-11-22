package dev.syhung.cocassistant.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity WorldState - Lưu trữ trạng thái thế giới game
 */
@Entity(tableName = "world_states", indices = [
    Index(value = ["sceneId"], name = "idx_world_state_scene_id"),
    Index(value = ["ttlSeconds"], name = "idx_ttl_world_states")
])
data class WorldState(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    var sceneId: String = "",
    var townHallLevel: Int = 0,
    var gold: Long = 0,
    var oil: Long = 0,
    var darkElixir: Long = 0,
    var gems: Long = 0,
    var busyBuilders: Int = 0,
    var idleBuilders: Int = 0,
    var buildTime: Long = 0,
    var hasNewEvent: Boolean = false,
    var repairScreenId: Int = 0,
    var repairTime: Long = 0,
    var lastUpdateDate: Date? = null,
    var creationDate: Date? = null,
    var ttlSeconds: Long = 1800, // 30 phút
    var nextReadTime: Long = 0,
    var realWaitSeconds: Long = 0,
    var updateAfterAction: Boolean = false
)
