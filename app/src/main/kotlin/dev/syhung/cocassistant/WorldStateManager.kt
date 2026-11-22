package dev.syhung.cocassistant

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import dev.syhung.cocassistant.db.AppDatabase
import dev.syhung.cocassistant.db.entity.ContextEntity

class WorldStateManager(private val context: Context, private val scope: CoroutineScope) {
    private val db = AppDatabase.getDatabase(context)
    private val contextDao = db.contextDao()

    fun getVillageResources(mode: String): JSONObject {
        // Implementation: Fetch from Room
        // Placeholder returning basic structure
        return JSONObject()
            .put("gold", 0)
            .put("elixir", 0)
            .put("darkElixir", 0)
    }

    fun getActiveBuildingTimers(mode: String): JSONObject {
        return JSONObject()
    }

    fun getGlobalContext(key: String, default: String): String {
        return default
    }

    fun getRecentExecutionLogs(count: Int): List<JSONObject> {
        return emptyList()
    }

    fun updateState(key: String, value: JSONObject) {
        scope.launch {
            // Fix: Adding 'category' field for ContextEntity
            contextDao.insertOrUpdate(ContextEntity(key = key, value = value.toString(), category = "WORLD_STATE"))
        }
    }
}
