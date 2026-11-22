package dev.syhung.cocassistant.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import dev.syhung.cocassistant.database.entity.RecoveryPatternEntity

@Dao
interface RecoveryPatternDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pattern: RecoveryPatternEntity)
    @Update
    suspend fun update(pattern: RecoveryPatternEntity)

    @Query("SELECT * FROM recovery_patterns")
    suspend fun getAllPatterns(): List<RecoveryPatternEntity>

    @Delete
    suspend fun delete(pattern: RecoveryPatternEntity)


    @Query("SELECT * FROM recovery_patterns WHERE isApproved = 1 AND sceneName = :sceneName")
    suspend fun getApprovedPattern(sceneName: String): RecoveryPatternEntity?

    @Query("SELECT * FROM recovery_patterns WHERE isApproved = 1")
    suspend fun getApprovedPatterns(): List<RecoveryPatternEntity>
}
