package dev.syhung.cocassistant.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contexts")
data class ContextEntity(
    @PrimaryKey val key: String,
    val value: String,
    val category: String,
    val updatedAt: Long = System.currentTimeMillis()
)
