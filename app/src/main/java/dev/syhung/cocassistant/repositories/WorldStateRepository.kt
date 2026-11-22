package dev.syhung.cocassistant.repositories

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.syhung.cocassistant.models.WorldState

@Dao
interface WorldStateRepository {
    @Insert
    suspend fun insert(state: WorldState): Long

    @Update
    suspend fun update(state: WorldState)

    @Delete
    suspend fun delete(state: WorldState)

    @Query("DELETE FROM world_states WHERE sceneId = :sceneId")
    suspend fun deleteBySceneId(sceneId: String)

    @Query("SELECT * FROM world_states WHERE sceneId = :sceneId ORDER BY lastUpdateDate DESC LIMIT 1")
    suspend fun findBySceneId(sceneId: String): WorldState?

    @Query("SELECT * FROM world_states ORDER BY lastUpdateDate DESC LIMIT 1")
    suspend fun findAllByAccount(): WorldState?

    @Query("SELECT * FROM world_states ORDER BY lastUpdateDate DESC")
    suspend fun findAll(): List<WorldState>

    @Query("DELETE FROM world_states WHERE nextReadTime < :now AND sceneId = :sceneId")
    suspend fun cleanupExpiredByScene(sceneId: String, now: Long)

    @Query("UPDATE world_states SET nextReadTime = :nextReadTime WHERE id = :id")
    suspend fun updateNextReadTime(id: Long, nextReadTime: Long)

    @Query("SELECT * FROM world_states WHERE sceneId = (SELECT MAX(sceneId) FROM world_states)")
    suspend fun findNewestByTTL(): WorldState?

    @Query("UPDATE world_states SET nextReadTime = :now + :ttlSeconds WHERE nextReadTime > :now")
    suspend fun autoUpdateTime(now: Long, ttlSeconds: Long)

    @Query("SELECT * FROM world_states WHERE nextReadTime <= :now AND sceneId = :sceneId LIMIT 1")
    suspend fun findByTTL(now: Long, sceneId: String): WorldState?
}
