package dev.syhung.cocassistant.repositories

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.syhung.cocassistant.models.Skill

@Dao
interface SkillRepository {

    @Insert
    suspend fun insert(skill: Skill): Long

    @Update
    suspend fun update(skill: Skill)

    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM skills WHERE sceneId = :sceneId AND status = 'ACTIVE' ORDER BY version DESC")
    suspend fun findAllBySceneId(sceneId: String): List<Skill>

    @Query("SELECT * FROM skills WHERE LOWER(name) = LOWER(:categoryName) ORDER BY version DESC")
    suspend fun findAllByName(categoryName: String): List<Skill>

    @Query("SELECT * FROM skills WHERE id = :id")
    suspend fun findByID(id: Long): Skill?

    @Query("DELETE FROM skills WHERE sceneId = :oldSceneId")
    suspend fun deleteByOldSceneId(oldSceneId: String)
}
