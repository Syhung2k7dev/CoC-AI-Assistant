package dev.syhung.cocassistant.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.syhung.cocassistant.db.entity.ArtifactEntity

@Dao
interface ArtifactDao {
    @Query("SELECT * FROM artifacts WHERE category = :category")
    suspend fun getArtifactsByCategory(category: String): List<ArtifactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artifact: ArtifactEntity)
}
