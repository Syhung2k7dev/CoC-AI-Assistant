package dev.syhung.cocassistant.repositories

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.syhung.cocassistant.models.WorldLayoutAnchor

@Dao
interface AnchorRepository {
    @Insert
    suspend fun insert(anchor: WorldLayoutAnchor): Long

    @Update
    suspend fun update(anchor: WorldLayoutAnchor)

    @Delete
    suspend fun delete(anchor: WorldLayoutAnchor)

    @Query("DELETE FROM world_layout_anchors WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM world_layout_anchors WHERE sceneId = :sceneId")
    suspend fun findBySceneId(sceneId: String): List<WorldLayoutAnchor>

    @Query("SELECT * FROM world_layout_anchors WHERE LOWER(category) = LOWER(:categoryName) AND LOWER(label) = LOWER(:label) LIMIT 1")
    suspend fun findByName(categoryName: String, label: String): WorldLayoutAnchor?

    @Query("SELECT * FROM world_layout_anchors WHERE LOWER(category) = LOWER(:categoryName) AND x BETWEEN :x1 AND :x2 ORDER BY abs(x - :targetX) + abs(y - :targetY) LIMIT 1")
    suspend fun findNearestByCategory(categoryName: String, x1: Float, x2: Float, targetX: Float, targetY: Float): WorldLayoutAnchor?

    @Query("DELETE FROM world_layout_anchors WHERE sceneId = :sceneId")
    suspend fun cleanUpScene(sceneId: String)

    @Query("DELETE FROM world_layout_anchors WHERE existsInCurrentUI = 0 AND sceneId = :sceneId")
    suspend fun cleanupInvalidAnchors(sceneId: String)

    @Query("SELECT * FROM world_layout_anchors WHERE LOWER(category) = LOWER(:categoryName) AND label = :label LIMIT 1")
    suspend fun findByLabel(categoryName: String, label: String): WorldLayoutAnchor?

    @Query("SELECT * FROM world_layout_anchors ORDER BY abs(x - :targetX) + abs(y - :targetY) LIMIT 1")
    suspend fun findNearestByCoords(targetX: Float, targetY: Float): WorldLayoutAnchor?
}
