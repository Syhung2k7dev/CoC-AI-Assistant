package dev.syhung.cocassistant.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.syhung.cocassistant.database.entity.ManualActionEntity

@Dao
interface ManualActionDao {
    @Insert
    suspend fun insertAction(action: ManualActionEntity): Long

    @Insert
    suspend fun insertActions(actions: List<ManualActionEntity>)

    @Update
    suspend fun updateAction(action: ManualActionEntity)

    @Query("DELETE FROM manual_actions WHERE id = :id")
    suspend fun deleteAction(id: Long)

    @Query("DELETE FROM manual_actions WHERE scriptId = :scriptId")
    suspend fun deleteActionsForScript(scriptId: Long)

    @Query("SELECT * FROM manual_actions WHERE scriptId = :scriptId ORDER BY `order` ASC")
    suspend fun getActionsForScript(scriptId: Long): List<ManualActionEntity>

    @Query("SELECT * FROM manual_actions WHERE id = :id")
    suspend fun getActionById(id: Long): ManualActionEntity?
}
