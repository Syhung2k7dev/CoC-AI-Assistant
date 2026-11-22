package dev.syhung.cocassistant.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artifacts")
data class ArtifactEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val key: String,
    val path: String,
    val category: String, // e.g., "SCREENSHOT", "TEMPLATE"
    val timestamp: Long = System.currentTimeMillis()
)
