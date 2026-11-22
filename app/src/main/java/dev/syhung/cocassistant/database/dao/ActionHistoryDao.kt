package dev.syhung.cocassistant.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.syhung.cocassistant.database.entity.ActionHistoryEntity

@Dao
interface ActionHistoryDao {
    @Insert
    suspend fun insert(history: ActionHistoryEntity)

    @Query("SELECT * FROM action_history ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecentHistory(): List<ActionHistoryEntity>

    @Query("DELETE FROM action_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM action_history WHERE id NOT IN (SELECT id FROM action_history ORDER BY timestamp DESC LIMIT 20)")
    suspend fun pruneOldHistory()

    @Transaction
    suspend fun insertAndPrune(history: ActionHistoryEntity) {
        insert(history)
        pruneOldHistory()
    }
}
