package dev.syhung.cocassistant.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.syhung.cocassistant.db.entity.ContextEntity

@Dao
interface ContextDao {
    @Query("SELECT value FROM contexts WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(context: ContextEntity)
}
