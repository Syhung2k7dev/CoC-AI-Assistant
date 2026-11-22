package dev.syhung.cocassistant.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "research_record")
data class ResearchRecordEntity(
    @PrimaryKey val id: Long = 0,
    val title: String,
    val description: String?,
    val status: String, // PENDING, IN_PROGRESS, COMPLETED, FAILED
    val researchType: String,
    val dataJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)