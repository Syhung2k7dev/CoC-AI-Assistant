package dev.syhung.cocassistant

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import dev.syhung.cocassistant.db.AppDatabase
import dev.syhung.cocassistant.db.entity.ContextEntity

/**
 * Room-backed access to world/session context.
 *
 * Missing observations are represented as unavailable, never as zero-valued
 * resources. This prevents the planner from mistaking unknown data for a real
 * in-game state.
 */
class WorldStateManager(
    context: Context,
    private val scope: CoroutineScope
) {
    private val db = AppDatabase.getDatabase(context.applicationContext)
    private val contextDao = db.contextDao()

    suspend fun getVillageResources(mode: String): JSONObject {
        val normalizedMode = normalizeMode(mode)
        return readJson(
            listOf("world_state.village_resources.$normalizedMode", "village_resources"),
            JSONObject().put("available", false).put("mode", normalizedMode)
        )
    }

    suspend fun getActiveBuildingTimers(mode: String): JSONObject {
        val normalizedMode = normalizeMode(mode)
        return readJson(
            listOf("world_state.building_timers.$normalizedMode", "building_timers"),
            JSONObject()
                .put("available", false)
                .put("mode", normalizedMode)
                .put("count", 0)
                .put("items", JSONArray())
        )
    }

    suspend fun getGlobalContext(key: String, default: String): String {
        val normalizedKey = key.trim()
        if (normalizedKey.isEmpty() || normalizedKey.length > 120) return default
        return withContext(Dispatchers.IO) {
            contextDao.getValue("system_context.$normalizedKey")
                ?: contextDao.getValue(normalizedKey)
                ?: default
        }
    }

    suspend fun getRecentExecutionLogs(count: Int): List<JSONObject> {
        if (count <= 0) return emptyList()
        return withContext(Dispatchers.IO) {
            db.executionLogDao().getAllExecutionLogs()
                .sortedByDescending { it.timestamp }
                .take(count.coerceAtMost(100))
                .map { log ->
                    JSONObject()
                        .put("id", log.id)
                        .put("skillId", log.skillId)
                        .put("actionType", log.actionType)
                        .put("result", if (log.success) "success" else "failed")
                        .put("durationMs", log.durationMs)
                        .put("timestamp", log.timestamp)
                        .put("errorDetails", log.errorDetails ?: JSONObject.NULL)
                }
        }
    }

    fun updateState(key: String, value: JSONObject) {
        val normalizedKey = key.trim()
        require(normalizedKey.isNotEmpty() && normalizedKey.length <= 160) {
            "World-state key không hợp lệ."
        }
        val safeValue = JSONObject(value.toString())
        scope.launch(Dispatchers.IO) {
            contextDao.insertOrUpdate(
                ContextEntity(
                    key = normalizedKey,
                    value = safeValue.toString(),
                    category = "WORLD_STATE"
                )
            )
        }
    }

    suspend fun saveState(key: String, value: JSONObject) {
        val normalizedKey = key.trim()
        require(normalizedKey.isNotEmpty() && normalizedKey.length <= 160) {
            "World-state key không hợp lệ."
        }
        val safeValue = JSONObject(value.toString())
        withContext(Dispatchers.IO) {
            contextDao.insertOrUpdate(
                ContextEntity(
                    key = normalizedKey,
                    value = safeValue.toString(),
                    category = "WORLD_STATE"
                )
            )
        }
    }

    private suspend fun readJson(keys: List<String>, fallback: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            for (key in keys) {
                val raw = contextDao.getValue(key) ?: continue
                val parsed = runCatching { JSONObject(raw) }.getOrNull()
                if (parsed != null) return@withContext parsed
            }
            JSONObject(fallback.toString())
        }

    private fun normalizeMode(mode: String): String =
        mode.trim().lowercase().takeIf { it.matches(Regex("[a-z0-9_-]{1,40}")) }
            ?: "unknown"
}
