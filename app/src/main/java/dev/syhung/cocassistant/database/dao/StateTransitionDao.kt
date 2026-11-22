package dev.syhung.cocassistant.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.syhung.cocassistant.database.entity.StateTransitionEntity

@Dao
interface StateTransitionDao {
    @Insert
    suspend fun insert(transition: StateTransitionEntity)

    @Query("SELECT * FROM state_transitions ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecentTransitions(): List<StateTransitionEntity>
    
    @Query("SELECT * FROM state_transitions WHERE fromState = :state")
    suspend fun getTransitionsFrom(state: String): List<StateTransitionEntity>

    @Query("SELECT * FROM state_transitions")
    suspend fun getAllTransitions(): List<StateTransitionEntity>
}
