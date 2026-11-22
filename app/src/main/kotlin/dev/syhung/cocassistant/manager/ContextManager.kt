package dev.syhung.cocassistant.manager

import dev.syhung.cocassistant.db.AppDatabase
import dev.syhung.cocassistant.db.entity.ContextEntity

object ContextManager {
    enum class Layer { SYSTEM, AGENT, WORLD_STATE, SKILL }

    private lateinit var database: AppDatabase

    fun init(db: AppDatabase) {
        database = db
    }

    suspend fun saveContext(key: String, value: String, layer: Layer) {
        database.contextDao().insertOrUpdate(
            ContextEntity(key, value, layer.name)
        )
    }

    suspend fun getContext(key: String): String? {
        return database.contextDao().getValue(key)
    }
}
