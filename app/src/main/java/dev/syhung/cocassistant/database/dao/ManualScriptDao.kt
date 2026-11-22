package dev.syhung.cocassistant.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.syhung.cocassistant.database.entity.ManualScriptEntity
import dev.syhung.cocassistant.database.entity.ManualActionEntity

@Dao
interface ManualScriptDao {
    @Insert
    suspend fun insertScript(script: ManualScriptEntity): Long

    @Insert
    suspend fun insertAction(action: ManualActionEntity)

    @Update
    suspend fun updateAction(action: ManualActionEntity)

    @Query("SELECT * FROM manual_scripts")
    suspend fun getAllScripts(): List<ManualScriptEntity>

    @Query("SELECT * FROM manual_actions WHERE scriptId = :scriptId ORDER BY `order` ASC")
    suspend fun getActionsForScript(scriptId: Long): List<ManualActionEntity>
}
