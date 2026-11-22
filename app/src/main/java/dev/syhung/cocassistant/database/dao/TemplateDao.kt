package dev.syhung.cocassistant.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.syhung.cocassistant.database.entity.TemplateEntity

@Dao
interface TemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TemplateEntity)

    @Query("SELECT * FROM vision_templates")
    suspend fun getAll(): List<TemplateEntity>

    @Query("SELECT * FROM vision_templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateById(id: String): TemplateEntity?

    @Query("DELETE FROM vision_templates WHERE id = :id")
    suspend fun delete(id: String)
}
