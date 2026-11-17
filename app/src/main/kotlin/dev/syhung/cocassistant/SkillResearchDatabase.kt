package dev.syhung.cocassistant

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Normalized local research database. Media bytes live in VisionMemory; this database stores
 * immutable references and relational evidence links.
 */
class SkillResearchDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sessions (session_id TEXT PRIMARY KEY, skill_id TEXT, goal TEXT NOT NULL, status TEXT NOT NULL, started_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, completed_at INTEGER, cycle_count INTEGER NOT NULL DEFAULT 0, summary TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE observations (observation_id TEXT PRIMARY KEY, session_id TEXT NOT NULL, cycle INTEGER NOT NULL, captured_at INTEGER NOT NULL, screen_key TEXT, screenshot_asset TEXT, crop_assets_json TEXT NOT NULL, phash TEXT, ocr_text TEXT NOT NULL, ai_analysis TEXT NOT NULL, action_json TEXT NOT NULL, action_result_json TEXT NOT NULL, FOREIGN KEY(session_id) REFERENCES sessions(session_id))")
        db.execSQL("CREATE INDEX idx_observations_session ON observations(session_id, cycle)")
        db.execSQL("CREATE TABLE layouts (layout_id TEXT PRIMARY KEY, session_id TEXT NOT NULL, screen_key TEXT NOT NULL, screen_name TEXT NOT NULL, version INTEGER NOT NULL, confidence REAL NOT NULL, evidence_observation_id TEXT, components_json TEXT NOT NULL, updated_at INTEGER NOT NULL, UNIQUE(session_id, screen_key, version))")
        db.execSQL("CREATE INDEX idx_layouts_screen ON layouts(session_id, screen_key)")
        db.execSQL("CREATE TABLE layout_components (component_id TEXT PRIMARY KEY, layout_id TEXT NOT NULL, component_key TEXT NOT NULL, label TEXT NOT NULL, type TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, width REAL NOT NULL, height REAL NOT NULL, anchor TEXT NOT NULL, properties_json TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_layout_components_layout ON layout_components(layout_id)")
        db.execSQL("CREATE TABLE procedure_steps (step_id TEXT PRIMARY KEY, session_id TEXT NOT NULL, step_index INTEGER NOT NULL, title TEXT NOT NULL, preconditions_json TEXT NOT NULL, action_json TEXT NOT NULL, postconditions_json TEXT NOT NULL, evidence_observation_id TEXT, status TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_procedure_session ON procedure_steps(session_id, step_index)")
        db.execSQL("CREATE TABLE strategies (strategy_id TEXT PRIMARY KEY, session_id TEXT NOT NULL, strategy_key TEXT NOT NULL, kind TEXT NOT NULL, content_json TEXT NOT NULL, confidence REAL NOT NULL, evidence_observation_id TEXT, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE calculations (calculation_id TEXT PRIMARY KEY, session_id TEXT NOT NULL, name TEXT NOT NULL, formula TEXT NOT NULL, inputs_json TEXT NOT NULL, result_json TEXT NOT NULL, evidence_observation_id TEXT, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE skill_documents (session_id TEXT PRIMARY KEY, manifest_json TEXT NOT NULL, casl_json TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE custom_datasets (dataset_id TEXT PRIMARY KEY, session_id TEXT NOT NULL, dataset_name TEXT NOT NULL, schema_json TEXT NOT NULL, records_json TEXT NOT NULL, version INTEGER NOT NULL, updated_at INTEGER NOT NULL, UNIQUE(session_id, dataset_name))")
        db.execSQL("CREATE TABLE media_assets (asset_id TEXT PRIMARY KEY, session_id TEXT NOT NULL, observation_id TEXT NOT NULL, kind TEXT NOT NULL, original_path TEXT NOT NULL, crop_path TEXT NOT NULL, metadata_json TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("PRAGMA user_version=$DATABASE_VERSION")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) onCreate(db)
    }

    @Synchronized
    fun startSession(sessionId: String, skillId: String?, goal: String) {
        val now = System.currentTimeMillis()
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO sessions(session_id, skill_id, goal, status, started_at, updated_at, cycle_count, summary) VALUES(?,?,?,?,?,?,0,'')",
            arrayOf(sessionId, skillId, goal, "RUNNING", now, now)
        )
    }

    @Synchronized
    fun updateSession(sessionId: String, skillId: String?, status: String, cycle: Int, summary: String = "") {
        writableDatabase.execSQL(
            "UPDATE sessions SET skill_id=?, status=?, updated_at=?, cycle_count=?, summary=?, completed_at=? WHERE session_id=?",
            arrayOf(skillId, status, System.currentTimeMillis(), cycle, summary,
                if (status == "COMPLETED" || status == "STOPPED" || status == "FAILED") System.currentTimeMillis() else null,
                sessionId)
        )
    }

    @Synchronized
    fun recordObservation(
        observationId: String,
        sessionId: String,
        cycle: Int,
        screenKey: String,
        screenshotAsset: String,
        cropAssets: JSONArray,
        phash: String,
        ocr: String,
        analysis: String,
        action: JSONObject,
        actionResult: JSONObject
    ) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO observations VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(observationId, sessionId, cycle, System.currentTimeMillis(), screenKey,
                screenshotAsset, cropAssets.toString(), phash, ocr, analysis, action.toString(), actionResult.toString())
        )
    }

    @Synchronized
    fun upsertLayout(sessionId: String, observationId: String, layout: JSONObject) {
        val screenKey = layout.optString("screenKey")
        if (screenKey.isBlank()) return
        val db = writableDatabase
        val version = db.rawQuery(
            "SELECT COALESCE(MAX(version),0)+1 FROM layouts WHERE session_id=? AND screen_key=?",
            arrayOf(sessionId, screenKey)
        ).use { if (it.moveToFirst()) it.getInt(0) else 1 }
        val components = layout.optJSONArray("components") ?: JSONArray()
        val id = "$sessionId:$screenKey:$version"
        db.execSQL(
            "INSERT INTO layouts VALUES(?,?,?,?,?,?,?,?,?)",
            arrayOf(id, sessionId, screenKey, layout.optString("screenName", screenKey),
                version, layout.optDouble("confidence", 0.0), observationId,
                components.toString(), System.currentTimeMillis())
        )
        for (i in 0 until components.length()) {
            val component = components.optJSONObject(i) ?: continue
            val key = component.optString("key").ifBlank { "component-$i" }
            val bounds = listOf("x", "y", "width", "height")
            val numbers = bounds.map { component.optDouble(it, 0.0).coerceIn(0.0, 1000.0) }
            db.execSQL(
                "INSERT OR REPLACE INTO layout_components VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf("$id:$key", id, key, component.optString("label"),
                    component.optString("type"), numbers[0], numbers[1], numbers[2], numbers[3],
                    component.optString("anchor"), (component.optJSONObject("properties") ?: JSONObject()).toString())
            )
        }
    }

    @Synchronized
    fun updateObservationActionResult(observationId: String, result: JSONObject) {
        writableDatabase.execSQL(
            "UPDATE observations SET action_result_json=? WHERE observation_id=?",
            arrayOf(result.toString(), observationId)
        )
    }

    @Synchronized
    fun replaceProcedure(sessionId: String, observationId: String, steps: JSONArray) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("procedure_steps", "session_id=?", arrayOf(sessionId))
            for (i in 0 until steps.length()) {
                val item = steps.optJSONObject(i) ?: continue
                val id = item.optString("id").ifBlank { "$sessionId:step:$i" }
                db.execSQL(
                    "INSERT INTO procedure_steps VALUES(?,?,?,?,?,?,?,?,?,?)",
                    arrayOf(id, sessionId, i, item.optString("title", "Step ${i + 1}"),
                        (item.optJSONObject("preconditions") ?: JSONObject()).toString(),
                        (item.optJSONObject("action") ?: JSONObject()).toString(),
                        (item.optJSONObject("postconditions") ?: JSONObject()).toString(),
                        observationId, item.optString("status", "OBSERVED"), System.currentTimeMillis())
                )
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    @Synchronized
    fun upsertStrategy(sessionId: String, observationId: String, item: JSONObject) {
        val key = item.optString("key")
        if (key.isBlank()) return
        val id = "$sessionId:$key"
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO strategies VALUES(?,?,?,?,?,?,?,?)",
            arrayOf(id, sessionId, key, item.optString("kind", "general"),
                (item.optJSONObject("content") ?: JSONObject()).toString(),
                item.optDouble("confidence", 0.0), observationId, System.currentTimeMillis())
        )
    }

    @Synchronized
    fun upsertCalculation(sessionId: String, observationId: String, item: JSONObject) {
        val name = item.optString("name")
        if (name.isBlank()) return
        val id = "$sessionId:$name"
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO calculations VALUES(?,?,?,?,?,?,?,?)",
            arrayOf(id, sessionId, name, item.optString("formula"),
                (item.optJSONObject("inputs") ?: JSONObject()).toString(),
                (item.optJSONObject("result") ?: JSONObject()).toString(),
                observationId, System.currentTimeMillis())
        )
    }

    @Synchronized
    fun saveSkillDocument(sessionId: String, manifest: JSONObject, casl: JSONObject) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO skill_documents VALUES(?,?,?,?)",
            arrayOf(sessionId, manifest.toString(), casl.toString(), System.currentTimeMillis())
        )
    }

    @Synchronized
    fun upsertDataset(sessionId: String, dataset: JSONObject) {
        val name = dataset.optString("name")
        if (name.isBlank() || !name.matches(Regex("[a-zA-Z0-9_.-]{1,80}"))) return
        val records = dataset.optJSONArray("records") ?: JSONArray()
        require(records.toString().length <= 500_000) { "Dataset quá lớn." }
        val existingVersion = writableDatabase.rawQuery(
            "SELECT COALESCE(MAX(version),0) FROM custom_datasets WHERE session_id=? AND dataset_name=?",
            arrayOf(sessionId, name)
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO custom_datasets VALUES(?,?,?,?,?,?,?)",
            arrayOf("$sessionId:$name", sessionId, name, (dataset.optJSONObject("schema") ?: JSONObject()).toString(),
                records.toString(), existingVersion + 1, System.currentTimeMillis())
        )
    }

    @Synchronized
    fun recordMedia(sessionId: String, observationId: String, asset: JSONObject, kind: String) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO media_assets VALUES(?,?,?,?,?,?,?,?)",
            arrayOf(asset.optString("id"), sessionId, observationId, kind, asset.optString("original"),
                asset.optString("crop"), asset.toString(), System.currentTimeMillis())
        )
    }

    /** Complete, versioned relational export for inclusion in the CADSS ZIP backup. */
    @Synchronized
    fun exportJson(): JSONObject {
        val db = readableDatabase
        val tables = listOf("sessions", "observations", "layouts", "layout_components", "procedure_steps", "strategies", "calculations", "skill_documents", "custom_datasets", "media_assets")
        val exported = JSONObject()
        tables.forEach { table ->
            val rows = JSONArray()
            db.rawQuery("SELECT * FROM $table", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    for (i in 0 until cursor.columnCount) {
                        if (cursor.isNull(i)) row.put(cursor.getColumnName(i), JSONObject.NULL)
                        else when (cursor.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> row.put(cursor.getColumnName(i), cursor.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT -> row.put(cursor.getColumnName(i), cursor.getDouble(i))
                            else -> row.put(cursor.getColumnName(i), cursor.getString(i))
                        }
                    }
                    rows.put(row)
                }
            }
            exported.put(table, rows)
        }
        return JSONObject().put("format", "CoC-SKILL-RESEARCH-DB")
            .put("schemaVersion", DATABASE_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("tables", exported)
    }

    @Synchronized
    fun importJson(document: JSONObject) {
        require(document.optString("format") == "CoC-SKILL-RESEARCH-DB")
        require(document.optInt("schemaVersion") in 1..DATABASE_VERSION)
        val tables = document.optJSONObject("tables") ?: error("Thiếu tables.")
        val db = writableDatabase
        val names = listOf("sessions", "observations", "layouts", "layout_components", "procedure_steps", "strategies", "calculations", "skill_documents", "custom_datasets", "media_assets")
        db.beginTransaction()
        try {
            names.forEach { table ->
                val rows = tables.optJSONArray(table) ?: JSONArray()
                db.delete(table, null, null)
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val columns = when (table) {
                        "sessions" -> listOf("session_id","skill_id","goal","status","started_at","updated_at","completed_at","cycle_count","summary")
                        "observations" -> listOf("observation_id","session_id","cycle","captured_at","screen_key","screenshot_asset","crop_assets_json","phash","ocr_text","ai_analysis","action_json","action_result_json")
                        "layouts" -> listOf("layout_id","session_id","screen_key","screen_name","version","confidence","evidence_observation_id","components_json","updated_at")
                        "layout_components" -> listOf("component_id","layout_id","component_key","label","type","x","y","width","height","anchor","properties_json")
                        "procedure_steps" -> listOf("step_id","session_id","step_index","title","preconditions_json","action_json","postconditions_json","evidence_observation_id","status","updated_at")
                        "strategies" -> listOf("strategy_id","session_id","strategy_key","kind","content_json","confidence","evidence_observation_id","updated_at")
                        "calculations" -> listOf("calculation_id","session_id","name","formula","inputs_json","result_json","evidence_observation_id","updated_at")
                        "skill_documents" -> listOf("session_id","manifest_json","casl_json","updated_at")
                        "custom_datasets" -> listOf("dataset_id","session_id","dataset_name","schema_json","records_json","version","updated_at")
                        else -> listOf("asset_id","session_id","observation_id","kind","original_path","crop_path","metadata_json","created_at")
                    }
                    val values = columns.map { column ->
                        if (!row.has(column) || row.isNull(column)) null else row.opt(column)
                    }.toTypedArray()
                    val placeholders = columns.joinToString(",") { "?" }
                    db.execSQL("INSERT OR REPLACE INTO $table(${columns.joinToString(",")}) VALUES($placeholders)", values)
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    companion object {
        private const val DATABASE_NAME = "skill_research.db"
        private const val DATABASE_VERSION = 1
    }
}
