package dev.syhung.cocassistant.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learned_skills")
data class LearnedSkillEntity(
    @PrimaryKey val id: String,
    val skillId: String,
    val learnedAt: Long = System.currentTimeMillis(),
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val lastUsedAt: Long = System.currentTimeMillis()
)