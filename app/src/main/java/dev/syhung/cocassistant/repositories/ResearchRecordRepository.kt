package dev.syhung.cocassistant.repositories

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.syhung.cocassistant.models.ResearchRecord
import java.util.Date

@Dao
interface ResearchRecordRepository {
    @Insert
    suspend fun insert(record: ResearchRecord): Long

    @Update
    suspend fun update(record: ResearchRecord)

    @Delete
    suspend fun delete(record: ResearchRecord)

    @Query("SELECT * FROM research_records WHERE skillId = :skillId ORDER BY startTime DESC")
    suspend fun findBySkillID(skillId: Long): List<ResearchRecord>

    @Query("SELECT * FROM research_records WHERE skillId = :skillId ORDER BY startTime DESC LIMIT 1")
    suspend fun findNewestResearch(skillId: Long): ResearchRecord?

    @Query("SELECT * FROM research_records WHERE researchResult = 'SUCCESS' ORDER BY startTime DESC LIMIT :limit")
    suspend fun findSuccessRecords(limit: Int): List<ResearchRecord>

    @Query("SELECT * FROM research_records WHERE violationReason IS NOT NULL ORDER BY startTime DESC LIMIT :limit")
    suspend fun findViolations(limit: Int): List<ResearchRecord>

    @Query("DELETE FROM research_records WHERE startTime < :cutoffDate")
    suspend fun cleanupOldRecords(cutoffDate: Date)

    @Query("""
        INSERT INTO research_records 
        (skillId, researchType, oldCaslScript, newProposedCaslScript, researchSteps, researchResult, problemDescription, knowledgeSource, screenshotPath, startTime, endTime, consecutiveFailCount) 
        VALUES (:skillId, :researchType, :oldCaslScript, :newProposedCaslScript, :researchSteps, :researchResult, :problemDescription, :knowledgeSource, :screenshotPath, :startTime, :endTime, :consecutiveFailCount)
    """)
    suspend fun createNewResearch(
        skillId: Long,
        researchType: String,
        oldCaslScript: String,
        newProposedCaslScript: String,
        researchSteps: Int,
        researchResult: String,
        problemDescription: String,
        knowledgeSource: String,
        screenshotPath: String,
        startTime: Date,
        endTime: Date,
        consecutiveFailCount: Int
    ): Long
}
