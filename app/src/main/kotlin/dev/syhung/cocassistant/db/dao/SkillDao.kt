package dev.syhung.cocassistant.db.dao

import dev.syhung.cocassistant.db.entity.SkillEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SkillDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSkill(skill: SkillEntity)

    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun deleteSkill(id: String)

    @Query("SELECT * FROM skills")
    fun getAllSkills(): List<SkillEntity>

    @Query("SELECT * FROM skills WHERE id = :id")
    fun getSkillById(id: String): SkillEntity?
}