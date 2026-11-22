package dev.syhung.cocassistant.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manual_scripts")
data class ManualScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "manual_actions")
data class ManualActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scriptId: Long,
    val type: String, // CLICK, SWIPE
    val x1: Float,
    val y1: Float,
    val x2: Float? = null,
    val y2: Float? = null,
    val order: Int
)
