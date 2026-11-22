package dev.syhung.cocassistant.db.dao

import dev.syhung.cocassistant.db.entity.ResearchRecordEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ResearchRecordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertResearchRecord(record: ResearchRecordEntity)

    @Query("DELETE FROM research_record WHERE id = :id")
    suspend fun deleteResearchRecord(id: Long)

    @Query("SELECT * FROM research_record")
    fun getAllResearchRecords(): List<ResearchRecordEntity>

    @Query("SELECT * FROM research_record WHERE status = :status")
    fun getResearchRecordsByStatus(status: String): List<ResearchRecordEntity>

    @Query("SELECT * FROM research_record WHERE createdAt >= :startDate AND createdAt <= :endDate")
    fun getResearchRecordsByDateRange(startDate: Long, endDate: Long): List<ResearchRecordEntity>
}