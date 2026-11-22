package dev.syhung.cocassistant.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_profile")
data class AccountProfileEntity(
    @PrimaryKey val accountId: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String? = null,
    val settings: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)