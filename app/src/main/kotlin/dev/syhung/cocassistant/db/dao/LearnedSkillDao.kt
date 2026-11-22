package dev.syhung.cocassistant.db.dao

import dev.syhung.cocassistant.db.entity.LearnedSkillEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LearnedSkillDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLearnedSkill(learnedSkill: LearnedSkillEntity)

    @Query("DELETE FROM learned_skills WHERE id = :id")
    suspend fun deleteLearnedSkill(id: String)

    @Query("SELECT * FROM learned_skills")
    fun getAllLearnedSkills(): List<LearnedSkillEntity>

    @Query("SELECT * FROM learned_skills WHERE id = :id")
    fun getLearnedSkillById(id: String): LearnedSkillEntity?
}