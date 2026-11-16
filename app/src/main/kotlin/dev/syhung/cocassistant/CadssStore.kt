package dev.syhung.cocassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** CADSS: versioned, local-first document store. */
class CadssStore(context: Context) {
    private val prefs = context.getSharedPreferences("cadss_store_v1", Context.MODE_PRIVATE)

    fun putText(key: String, value: String) { prefs.edit().putString("text:$key", value).apply() }
    fun getText(key: String, fallback: String = ""): String = prefs.getString("text:$key", fallback) ?: fallback

    fun addRecord(collection: String, record: JSONObject) {
        val records = JSONArray(prefs.getString("collection:$collection", "[]"))
        records.put(record)
        prefs.edit().putString("collection:$collection", records.toString()).apply()
    }

    fun getRecords(collection: String): List<JSONObject> {
        val array = JSONArray(prefs.getString("collection:$collection", "[]"))
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    fun count(collection: String): Int = getRecords(collection).size

    fun exportDocument(): JSONObject {
        val collections = JSONObject()
        val names = prefs.all.keys.filter { it.startsWith("collection:") }
        names.forEach { key -> collections.put(key.removePrefix("collection:"), prefs.getString(key, "[]")) }
        val texts = JSONObject()
        prefs.all.filterKeys { it.startsWith("text:") }.forEach { (key, value) ->
            texts.put(key.removePrefix("text:"), value?.toString() ?: "")
        }
        return JSONObject()
            .put("format", "CADSS")
            .put("schemaVersion", SCHEMA_VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("texts", texts)
            .put("collections", collections)
    }

    fun importDocument(document: JSONObject) {
        require(document.optString("format") == "CADSS") { "Unsupported backup format" }
        val version = document.optInt("schemaVersion", 0)
        require(version in 1..SCHEMA_VERSION) { "Unsupported schema version: $version" }
        val editor = prefs.edit()
        val texts = document.optJSONObject("texts") ?: JSONObject()
        texts.keys().forEach { key -> editor.putString("text:$key", texts.optString(key)) }
        val collections = document.optJSONObject("collections") ?: JSONObject()
        collections.keys().forEach { key ->
            val raw = collections.optString(key, "[]")
            JSONArray(raw)
            editor.putString("collection:$key", raw)
        }
        editor.apply()
    }

    companion object { const val SCHEMA_VERSION = 1 }
}
