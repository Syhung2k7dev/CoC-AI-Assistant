package dev.syhung.cocassistant.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "world_state")
data class WorldStateEntity(
    @PrimaryKey val worldId: String,
    val stateJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)