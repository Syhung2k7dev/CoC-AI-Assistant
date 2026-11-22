package dev.syhung.cocassistant.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vision_templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val label: String,
    val imageData: ByteArray,
    val createdAt: Long = System.currentTimeMillis()
)