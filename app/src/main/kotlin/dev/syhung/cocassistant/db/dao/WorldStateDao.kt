package dev.syhung.cocassistant.db.dao

import dev.syhung.cocassistant.db.entity.WorldStateEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorldStateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWorldState(worldState: WorldStateEntity)

    @Query("DELETE FROM world_state WHERE worldId = :worldId")
    suspend fun deleteWorldState(worldId: String)

    @Query("SELECT * FROM world_state")
    fun getAllWorldStates(): List<WorldStateEntity>

    @Query("SELECT * FROM world_state WHERE worldId = :worldId")
    fun getWorldStateById(worldId: String): WorldStateEntity?
}