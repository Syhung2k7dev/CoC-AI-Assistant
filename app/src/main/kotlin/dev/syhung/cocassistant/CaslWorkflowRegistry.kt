package dev.syhung.cocassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class WorkflowLifecycle { DRAFT, TESTING, VERIFIED, DISABLED }

/** Workflow programs are first-class CADSS records, separate from Skill manifests. */
class CaslWorkflowRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("cadss_store_v1", Context.MODE_PRIVATE)
    private val skillRegistry = AgentSkillRegistry(context)

    @Synchronized
    fun all(): List<JSONObject> {
        val records = JSONArray(prefs.getString(KEY_WORKFLOWS, "[]"))
        return (0 until records.length()).mapNotNull { records.optJSONObject(it) }
    }

    @Synchronized
    fun find(id: String, version: Int? = null): JSONObject? =
        all().filter {
            it.optString("id") == id &&
                (version == null || it.optInt("version") == version)
        }.maxByOrNull { it.optInt("version") }

    @Synchronized
    fun findVerified(id: String, version: Int? = null): JSONObject? =
        find(id, version)?.takeIf {
            it.optString("lifecycle") == WorkflowLifecycle.VERIFIED.name
        }

    @Synchronized
    fun saveDraft(program: JSONObject): Boolean {
        if (!validate(program)) return false
        val current = all().toMutableList()
        val id = program.optString("id")
        val version = program.optInt("version")
        if (current.any { it.optString("id") == id && it.optInt("version") >= version }) return false
        val stored = JSONObject(program.toString())
            .put("lifecycle", WorkflowLifecycle.DRAFT.name)
            .put("updatedAt", System.currentTimeMillis())
        current.add(stored)
        persist(current)
        return true
    }

    @Synchronized
    fun setLifecycle(id: String, version: Int, next: WorkflowLifecycle): Boolean {
        val current = all().toMutableList()
        val index = current.indexOfFirst {
            it.optString("id") == id && it.optInt("version") == version
        }
        if (index < 0) return false
        val old = current[index]
        val previous = runCatching {
            WorkflowLifecycle.valueOf(old.optString("lifecycle", "DRAFT"))
        }.getOrDefault(WorkflowLifecycle.DRAFT)
        val allowed = when (next) {
            WorkflowLifecycle.TESTING -> previous == WorkflowLifecycle.DRAFT
            WorkflowLifecycle.VERIFIED -> false
            WorkflowLifecycle.DISABLED -> true
            WorkflowLifecycle.DRAFT -> previous == WorkflowLifecycle.DISABLED
        }
        if (!allowed) return false
        current[index] = JSONObject(old.toString())
            .put("lifecycle", next.name)
            .put("updatedAt", System.currentTimeMillis())
        persist(current)
        return true
    }

    @Synchronized
    fun completeTest(id: String, version: Int, passed: Int, total: Int): Boolean {
        if (total <= 0 || passed != total) return false
        val current = all().toMutableList()
        val index = current.indexOfFirst {
            it.optString("id") == id && it.optInt("version") == version
        }
        if (index < 0 || current[index].optString("lifecycle") != WorkflowLifecycle.TESTING.name) return false
        val requiredSkills = collectSkillVersions(current[index].optJSONArray("steps") ?: JSONArray())
        if (requiredSkills.any { (skillId, skillVersion) ->
                val skill = skillRegistry.find(skillId, skillVersion)
                skill == null || skill.status != SkillLifecycle.VERIFIED || skill.scriptJson.isBlank()
            }) return false
        current[index] = JSONObject(current[index].toString())
            .put("lifecycle", WorkflowLifecycle.VERIFIED.name)
            .put("testPassed", passed)
            .put("testTotal", total)
            .put("testedAt", System.currentTimeMillis())
        persist(current)
        return true
    }

    private fun collectSkillVersions(steps: JSONArray): Map<String, Int> {
        val found = linkedMapOf<String, Int>()
        for (index in 0 until steps.length()) {
            val step = steps.optJSONObject(index) ?: continue
            when (step.optString("op")) {
                "skill" -> {
                    val id = step.optString("id")
                    val version = step.optInt("version", 0)
                    if (id.isNotBlank() && version > 0) found[id] = version
                }
                "if" -> {
                    found.putAll(collectSkillVersions(step.optJSONArray("then") ?: JSONArray()))
                    found.putAll(collectSkillVersions(step.optJSONArray("else") ?: JSONArray()))
                }
                "repeat" -> found.putAll(collectSkillVersions(step.optJSONArray("steps") ?: JSONArray()))
            }
        }
        return found
    }

    private fun validate(program: JSONObject): Boolean = runCatching {
        require(program.optString("format") == "CASL")
        require(program.optInt("schemaVersion", -1) == CaslScriptRuntime.SCHEMA_VERSION)
        require(program.optString("kind") == "workflow")
        require(program.optString("id").matches(Regex("[a-z0-9][a-z0-9._-]{1,79}")))
        require(program.optInt("version", 0) >= 1)
        require(program.toString().length <= 128_000)
        CaslScriptRuntime(CaslApiRegistry(), { _, _ -> null })
            .validateProgramDocument(program)
    }.isSuccess

    private fun persist(records: List<JSONObject>) {
        prefs.edit().putString(KEY_WORKFLOWS, JSONArray().apply {
            records.forEach { put(it) }
        }.toString()).apply()
    }

    companion object { private const val KEY_WORKFLOWS = "collection:workflow_registry" }
}
