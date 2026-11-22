package dev.syhung.cocassistant.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "world_layout_anchor")
data class WorldLayoutAnchorEntity(
    @PrimaryKey val anchorId: String,
    val worldId: String,
    val anchorType: String,
    val x: Float,
    val y: Float,
    val metadata: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)