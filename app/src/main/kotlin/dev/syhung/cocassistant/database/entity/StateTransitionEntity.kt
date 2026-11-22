package dev.syhung.cocassistant.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "state_transitions")
data class StateTransitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromState: String,
    val actionType: String,
    val toState: String,
    val isSuccess: Boolean,
    val skillId: String? = null,
    val args: String? = null,
    val featureDescriptor: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)