package dev.syhung.cocassistant.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val version: String? = null,
    val metadata: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)