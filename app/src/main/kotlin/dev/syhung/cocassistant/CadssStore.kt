package dev.syhung.cocassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * CADSS local document store.
 *
 * Collections remain the shared persistence boundary used by Skill, Workflow and run registries.
 * Backup schema v2 stores JSON arrays as arrays (not JSON-encoded strings) while importing v1
 * backups for backward compatibility.
 */
class CadssStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cadss_store_v1", Context.MODE_PRIVATE)

    fun putText(key: String, value: String) {
        validateKey(key)
        require(value.length <= MAX_TEXT_CHARS) { "CADSS text vượt giới hạn kích thước." }
        prefs.edit().putString("text:$key", value).apply()
    }

    fun getText(key: String, fallback: String = ""): String {
        validateKey(key)
        return prefs.getString("text:$key", fallback) ?: fallback
    }

    @Synchronized
    fun addRecord(collection: String, record: JSONObject) {
        validateKey(collection)
        require(record.toString().length <= MAX_RECORD_CHARS) { "CADSS record vượt giới hạn kích thước." }
        val records = JSONArray(prefs.getString("collection:$collection", "[]") ?: "[]")
        require(records.length() < MAX_RECORDS_PER_COLLECTION) { "CADSS collection đã đạt giới hạn record." }
        records.put(JSONObject(record.toString()))
        prefs.edit().putString("collection:$collection", records.toString()).apply()
    }

    @Synchronized
    fun getRecords(collection: String): List<JSONObject> {
        validateKey(collection)
        val array = JSONArray(prefs.getString("collection:$collection", "[]") ?: "[]")
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let { row -> JSONObject(row.toString()) } }
    }

    fun count(collection: String): Int = getRecords(collection).size

    @Synchronized
    fun exportDocument(): JSONObject {
        val collections = JSONObject()
        prefs.all.keys.filter { it.startsWith(COLLECTION_PREFIX) }.sorted().forEach { key ->
            val name = key.removePrefix(COLLECTION_PREFIX)
            validateKey(name)
            collections.put(name, JSONArray(prefs.getString(key, "[]") ?: "[]"))
        }
        val texts = JSONObject()
        prefs.all.filterKeys { it.startsWith(TEXT_PREFIX) }.toSortedMap().forEach { (key, value) ->
            val name = key.removePrefix(TEXT_PREFIX)
            validateKey(name)
            texts.put(name, value?.toString() ?: "")
        }
        val document = JSONObject()
            .put("format", "CADSS")
            .put("schemaVersion", SCHEMA_VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("texts", texts)
            .put("collections", collections)
        require(document.toString().length <= MAX_BACKUP_CHARS) { "CADSS backup vượt giới hạn kích thước." }
        return document
    }

    /**
     * Validates the complete backup before touching preferences, then replaces only CADSS keys
     * in one SharedPreferences editor transaction. Version 1 used JSON-encoded collection strings.
     */
    @Synchronized
    fun importDocument(document: JSONObject) {
        require(document.toString().length <= MAX_BACKUP_CHARS) { "CADSS backup vượt giới hạn kích thước." }
        require(document.optString("format") == "CADSS") { "Định dạng bản sao lưu không được hỗ trợ." }
        val version = document.optInt("schemaVersion", 0)
        require(version in MIN_SUPPORTED_SCHEMA_VERSION..SCHEMA_VERSION) {
            "Phiên bản cấu trúc dữ liệu không được hỗ trợ: $version"
        }

        val staged = linkedMapOf<String, String>()
        val texts = document.optJSONObject("texts") ?: JSONObject()
        require(texts.length() <= MAX_COLLECTIONS) { "CADSS backup có quá nhiều text entries." }
        texts.keys().forEach { key ->
            validateKey(key)
            val value = texts.optString(key, "")
            require(value.length <= MAX_TEXT_CHARS) { "CADSS text '$key' vượt giới hạn." }
            staged[TEXT_PREFIX + key] = value
        }

        val collections = document.optJSONObject("collections") ?: JSONObject()
        require(collections.length() <= MAX_COLLECTIONS) { "CADSS backup có quá nhiều collections." }
        collections.keys().forEach { name ->
            validateKey(name)
            val raw = collections.opt(name)
            val records = when (raw) {
                is JSONArray -> JSONArray(raw.toString())
                is String -> JSONArray(raw)
                else -> throw IllegalArgumentException("CADSS collection '$name' không phải array.")
            }
            require(records.length() <= MAX_RECORDS_PER_COLLECTION) {
                "CADSS collection '$name' vượt giới hạn record."
            }
            val normalized = JSONArray()
            for (index in 0 until records.length()) {
                val record = records.optJSONObject(index)
                    ?: throw IllegalArgumentException("CADSS record '$name[$index]' phải là object.")
                require(record.toString().length <= MAX_RECORD_CHARS) {
                    "CADSS record '$name[$index]' vượt giới hạn kích thước."
                }
                normalized.put(JSONObject(record.toString()))
            }
            staged[COLLECTION_PREFIX + name] = normalized.toString()
        }

        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(TEXT_PREFIX) || it.startsWith(COLLECTION_PREFIX) }
            .forEach(editor::remove)
        staged.forEach { (key, value) -> editor.putString(key, value) }
        editor.apply()
    }

    private fun validateKey(key: String) {
        require(key.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}"))) { "CADSS key không hợp lệ." }
    }

    companion object {
        const val SCHEMA_VERSION = 2
        private const val MIN_SUPPORTED_SCHEMA_VERSION = 1
        private const val TEXT_PREFIX = "text:"
        private const val COLLECTION_PREFIX = "collection:"
        private const val MAX_COLLECTIONS = 100
        private const val MAX_RECORDS_PER_COLLECTION = 5_000
        private const val MAX_RECORD_CHARS = 64_000
        private const val MAX_TEXT_CHARS = 256_000
        private const val MAX_BACKUP_CHARS = 2_000_000
    }
}
