package dev.syhung.cocassistant.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

package dev.syhung.cocassistant.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recovery_patterns")
data class RecoveryPatternEntity(
    @PrimaryKey val sceneName: String,
    val actionType: String, // "TAP", "SWIPE"
    val jsonArgs: String,   // Lưu json dạng {"x": 500, "y": 500}
    val lastUsed: Long,
    val isApproved: Boolean = false
)

