package dev.syhung.cocassistant.db.dao

import dev.syhung.cocassistant.db.entity.WorldLayoutAnchorEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorldLayoutAnchorDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWorldLayoutAnchor(anchor: WorldLayoutAnchorEntity)

    @Query("DELETE FROM world_layout_anchor WHERE anchorId = :anchorId")
    suspend fun deleteWorldLayoutAnchor(anchorId: String)

    @Query("SELECT * FROM world_layout_anchor")
    fun getAllWorldLayoutAnchors(): List<WorldLayoutAnchorEntity>

    @Query("SELECT * FROM world_layout_anchor WHERE anchorId = :anchorId")
    fun getWorldLayoutAnchorById(anchorId: String): WorldLayoutAnchorEntity?
}