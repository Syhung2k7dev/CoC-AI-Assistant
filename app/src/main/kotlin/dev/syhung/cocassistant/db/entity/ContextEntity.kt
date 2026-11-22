package dev.syhung.cocassistant.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contexts")
data class ContextEntity(
    @PrimaryKey val key: String,
    val value: String,
    val category: String, // Layer: SYSTEM, AGENT, WORLD_STATE, SKILL
    val updatedAt: Long = System.currentTimeMillis()
)

