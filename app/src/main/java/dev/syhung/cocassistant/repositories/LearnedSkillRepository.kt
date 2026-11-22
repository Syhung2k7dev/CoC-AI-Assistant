package dev.syhung.cocassistant.repositories

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.syhung.cocassistant.models.LearnedSkill

@Dao
interface LearnedSkillRepository {
    @Insert
    suspend fun insert(skill: LearnedSkill): Long

    @Update
    suspend fun update(skill: LearnedSkill)

    @Delete
    suspend fun delete(skill: LearnedSkill)

    @Query("SELECT * FROM learned_skills WHERE sceneId = :sceneId ORDER BY version DESC")
    suspend fun findAllBySceneId(sceneId: String): List<LearnedSkill>

    @Query("SELECT * FROM learned_skills WHERE LOWER(substr(caslScript, 1, :categoryLength)) = LOWER(:categoryName) ORDER BY version DESC")
    suspend fun findAllByName(categoryName: String, categoryLength: Int): List<LearnedSkill>

    @Query("SELECT * FROM learned_skills WHERE id = :id")
    suspend fun findByID(id: Long): LearnedSkill?

    @Query("SELECT * FROM learned_skills WHERE sceneId = :oldSceneId ORDER BY version DESC")
    suspend fun findByOldSceneId(oldSceneId: String): List<LearnedSkill>

    @Query("DELETE FROM learned_skills WHERE sceneId = :oldSceneId")
    suspend fun deleteByOldSceneId(oldSceneId: String)

    @Query("SELECT * FROM learned_skills WHERE version = :version ORDER BY skillId ASC")
    suspend fun findByVersion(version: Int): List<LearnedSkill>

    @Query("UPDATE learned_skills SET selfHealWarningCount = :warningCount WHERE id = :id AND selfHealWarningCount >= 2")
    suspend fun updateSelfHealWarning(warningCount: Int, id: Long)
}
