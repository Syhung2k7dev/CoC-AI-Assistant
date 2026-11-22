package dev.syhung.cocassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class SkillActionCapability {
    OBSERVE_SCREEN, READ_TEXT, ANALYZE_IMAGE, TAP, SWIPE, WAIT, VERIFY_STATE
}

enum class SkillLifecycle {
    RESEARCH_REQUIRED, RESEARCHING, RESEARCHED, DRAFT, TESTING, VERIFIED, EXPERIMENTAL, DISABLED
}

/** A Skill is validated data and a workflow contract, never executable source code. */
data class AgentSkill(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val version: Int = 1,
    val status: SkillLifecycle = SkillLifecycle.DRAFT,
    val inputs: List<String> = emptyList(),
    val outputs: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val capabilities: Set<SkillActionCapability> = emptySet(),
    val instructions: String = "",
    val scriptJson: String = "",
    val source: String = "user_or_agent",
    val confidence: Float = 0f,
    val minAgentVersion: Int = 1,
    val maxAgentVersion: Int? = null,
    val dependencyVersions: Map<String, Int> = emptyMap(),
    val testPassed: Int = 0,
    val testTotal: Int = 0,
    val testedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun validate(): Boolean =
        id.matches(Regex("[a-z0-9][a-z0-9._-]{1,79}")) &&
            name.isNotBlank() && name.length <= 100 &&
            description.isNotBlank() && description.length <= 500 &&
            category.isNotBlank() && category.length <= 60 &&
            version >= 1 && confidence in 0f..1f &&
            minAgentVersion >= 1 && (maxAgentVersion == null || maxAgentVersion >= minAgentVersion) &&
            dependencyVersions.size <= 40 && dependencyVersions.values.all { it >= 1 } &&
            testPassed >= 0 && testTotal >= 0 && testPassed <= testTotal &&
            inputs.size <= 40 && outputs.size <= 40 &&
            dependencies.size <= 40 && instructions.length <= 12_000 &&
            scriptJson.length <= 32_000 && (scriptJson.isBlank() || runCatching {
                val script = JSONObject(scriptJson)
                script.optString("format") == "CASL" &&
                    script.optInt("schemaVersion", -1) == CaslScriptRuntime.SCHEMA_VERSION &&
                    script.optString("kind") == "skill" &&
                    script.optJSONArray("steps") != null
            }.getOrDefault(false))

    fun supportsAgentVersion(agentVersion: Int): Boolean =
        agentVersion >= minAgentVersion && (maxAgentVersion == null || agentVersion <= maxAgentVersion)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("description", description)
        .put("category", category).put("version", version).put("status", status.name)
        .put("inputs", JSONArray(inputs)).put("outputs", JSONArray(outputs))
        .put("dependencies", JSONArray(dependencies))
        .put("capabilities", JSONArray(capabilities.map { it.name }))
        .put("instructions", instructions).put("script", scriptJson).put("source", source)
        .put("confidence", confidence.toDouble())
        .put("minAgentVersion", minAgentVersion)
        .put("maxAgentVersion", maxAgentVersion ?: JSONObject.NULL)
        .put("dependencyVersions", JSONObject().apply { dependencyVersions.forEach { (id, required) -> put(id, required) } })
        .put("testPassed", testPassed).put("testTotal", testTotal)
        .put("testedAt", testedAt ?: JSONObject.NULL)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(json: JSONObject): AgentSkill? = runCatching {
            AgentSkill(
                id = json.getString("id"),
                name = json.getString("name"),
                description = json.getString("description"),
                category = json.getString("category"),
                version = json.optInt("version", 1),
                status = runCatching { SkillLifecycle.valueOf(json.optString("status", "DRAFT")) }
                    .getOrDefault(SkillLifecycle.DRAFT),
                inputs = json.optJSONArray("inputs").toStringList(),
                outputs = json.optJSONArray("outputs").toStringList(),
                dependencies = json.optJSONArray("dependencies").toStringList(),
                capabilities = json.optJSONArray("capabilities").toStringList()
                    .mapNotNull { runCatching { SkillActionCapability.valueOf(it) }.getOrNull() }.toSet(),
                instructions = json.optString("instructions", ""),
                scriptJson = json.optString("script", ""),
                source = json.optString("source", "unknown"),
                confidence = json.optDouble("confidence", 0.0).toFloat(),
                minAgentVersion = json.optInt("minAgentVersion", 1),
                maxAgentVersion = if (json.isNull("maxAgentVersion")) null else json.optInt("maxAgentVersion"),
                dependencyVersions = json.optJSONObject("dependencyVersions").toIntMap(),
                testPassed = json.optInt("testPassed", 0),
                testTotal = json.optInt("testTotal", 0),
                testedAt = if (json.isNull("testedAt")) null else json.optLong("testedAt"),
                updatedAt = json.optLong("updatedAt", 0L)
            ).let { it.takeIf(AgentSkill::validate) }
        }.getOrNull()

        private fun JSONObject?.toIntMap(): Map<String, Int> {
            if (this == null) return emptyMap()
            val result = linkedMapOf<String, Int>()
            keys().forEach { key -> result[key] = optInt(key, 0) }
            return result
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
        }
    }
}

/** Local Skill Library. New AI-authored Skills are always persisted as Draft. */
class AgentSkillRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("cadss_store_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<AgentSkill> {
        val array = JSONArray(prefs.getString(KEY_SKILLS, "[]"))
        return (0 until array.length()).mapNotNull {
            AgentSkill.fromJson(array.optJSONObject(it) ?: JSONObject())
        }
    }

    @Synchronized
    fun find(id: String, version: Int? = null): AgentSkill? =
        all().filter { it.id == id && (version == null || it.version == version) }
            .maxByOrNull { it.version }

    @Synchronized
    fun findVerifiedScript(id: String, version: Int? = null): JSONObject? {
        val records = all()
        val skill = records.filter {
            it.id == id && (version == null || it.version == version)
        }.maxByOrNull { it.version } ?: return null
        if (!isDependencyGraphVerified(skill, records, linkedSetOf())) return null
        return runCatching { JSONObject(skill.scriptJson) }.getOrNull()
    }

    private fun isDependencyGraphVerified(
        skill: AgentSkill,
        records: List<AgentSkill>,
        visiting: MutableSet<String>
    ): Boolean {
        if (skill.status != SkillLifecycle.VERIFIED || skill.scriptJson.isBlank()) return false
        val node = "${skill.id}@${skill.version}"
        if (!visiting.add(node)) return false
        val ready = skill.dependencies.all { dependency ->
            val minimumVersion = skill.dependencyVersions[dependency]
            val target = records.asSequence()
                .filter { it.id == dependency && it.status == SkillLifecycle.VERIFIED }
                .filter { minimumVersion == null || it.version >= minimumVersion }
                .maxByOrNull { it.version }
            target != null && isDependencyGraphVerified(target, records, visiting)
        }
        visiting.remove(node)
        return ready
    }

    @Synchronized
    fun markCatalogForResearch(catalogSkill: AgentSkill): Boolean {
        val current = all().toMutableList()
        val index = current.indexOfLast { it.id == catalogSkill.id && it.source == "builtin_catalog" }
        if (index < 0) return false
        val old = current[index]
        val nextStatus = if (old.status == SkillLifecycle.DRAFT && old.scriptJson.isBlank()) {
            SkillLifecycle.RESEARCH_REQUIRED
        } else old.status
        current[index] = old.copy(
            name = catalogSkill.name,
            description = catalogSkill.description,
            category = catalogSkill.category,
            status = nextStatus,
            updatedAt = System.currentTimeMillis()
        )
        persist(current)
        return true
    }

    @Synchronized
    fun saveCatalogSkill(skill: AgentSkill): Boolean {
        if (!skill.validate() || skill.source != "builtin_catalog" || skill.scriptJson.isNotBlank()) return false
        return saveNewVersion(skill.copy(status = SkillLifecycle.RESEARCH_REQUIRED))
    }

    @Synchronized
    fun removeCatalogSkills(ids: Set<String>): Int {
        val current = all()
        val retained = current.filterNot { it.id in ids && it.source == "builtin_catalog" }
        val removed = current.size - retained.size
        if (removed > 0) persist(retained)
        return removed
    }

    @Synchronized
    fun saveDraft(skill: AgentSkill): Boolean {
        if (!skill.validate()) return false
        return saveNewVersion(skill.copy(status = SkillLifecycle.DRAFT))
    }

    @Synchronized
    fun saveResearched(skill: AgentSkill): Boolean {
        if (!skill.validate() || skill.scriptJson.isBlank() || skill.source != "gemini_skill_research") return false
        return saveNewVersion(skill.copy(status = SkillLifecycle.RESEARCHED))
    }

    private fun saveNewVersion(skill: AgentSkill): Boolean {
        val current = all().toMutableList()
        if (current.any { it.id == skill.id && it.version == skill.version }) return false
        val highestVersion = current.filter { it.id == skill.id }.maxOfOrNull { it.version } ?: 0
        if (skill.version <= highestVersion) return false
        current.add(skill.copy(updatedAt = System.currentTimeMillis()))
        persist(current)
        return true
    }

    @Synchronized
    fun setLifecycle(id: String, next: SkillLifecycle, version: Int? = null): Boolean {
        val current = all().toMutableList()
        val index = current.indexOfLast { it.id == id && (version == null || it.version == version) }
        if (index < 0) return false
        val old = current[index]
        val allowed = when (next) {
            SkillLifecycle.RESEARCH_REQUIRED -> old.status == SkillLifecycle.RESEARCHING
            SkillLifecycle.RESEARCHING -> old.status == SkillLifecycle.RESEARCH_REQUIRED
            SkillLifecycle.RESEARCHED -> old.status == SkillLifecycle.RESEARCHING
            SkillLifecycle.TESTING -> old.status == SkillLifecycle.DRAFT ||
                old.status == SkillLifecycle.RESEARCHED || old.status == SkillLifecycle.EXPERIMENTAL
            SkillLifecycle.VERIFIED -> false
            SkillLifecycle.EXPERIMENTAL -> old.status == SkillLifecycle.TESTING || old.status == SkillLifecycle.VERIFIED
            SkillLifecycle.DISABLED -> true
            SkillLifecycle.DRAFT -> old.status == SkillLifecycle.DISABLED
        }
        if (!allowed) return false
        current[index] = old.copy(status = next, updatedAt = System.currentTimeMillis())
        persist(current)
        return true
    }

    @Synchronized
    fun completeTest(id: String, passed: Int, total: Int): Boolean {
        if (total <= 0 || passed != total || passed < 0) return false
        val current = all().toMutableList()
        val index = current.indexOfLast { it.id == id && it.status == SkillLifecycle.TESTING }
        if (index < 0) return false
        val skill = current[index]
        if (skill.scriptJson.isBlank()) return false
        val verifiedById = current
            .filter { it.status == SkillLifecycle.VERIFIED }
            .groupBy { it.id }
            .mapValues { (_, versions) -> versions.maxByOrNull { it.version } }
        if (skill.dependencies.any { dependency ->
                val found = verifiedById[dependency]
                found == null ||
                    (skill.dependencyVersions[dependency]?.let { found.version < it } == true)
            }) return false
        current[index] = skill.copy(
            status = SkillLifecycle.VERIFIED,
            testPassed = passed,
            testTotal = total,
            testedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        persist(current)
        return true
    }

    private fun persist(skills: List<AgentSkill>) {
        prefs.edit().putString(KEY_SKILLS, JSONArray().apply {
            skills.forEach { put(it.toJson()) }
        }.toString()).apply()
    }

    companion object { private const val KEY_SKILLS = "collection:skill_registry" }
}

/** Typed action request; arbitrary shell commands and generated source are not accepted. */
data class SkillActionRequest(
    val skillId: String,
    val capability: SkillActionCapability,
    val normalizedX: Int? = null,
    val normalizedY: Int? = null,
    val endNormalizedX: Int? = null,
    val endNormalizedY: Int? = null,
    val durationMs: Long? = null,
    val skillVersion: Int? = null
) {
    fun validate(): Boolean {
        if (skillId.isBlank() || (skillVersion != null && skillVersion < 1)) return false
        if (capability == SkillActionCapability.TAP) {
            return normalizedX != null && normalizedX in 0..1000 &&
                normalizedY != null && normalizedY in 0..1000 &&
                endNormalizedX == null && endNormalizedY == null &&
                (durationMs == null || durationMs in 50L..10_000L)
        }
        if (capability == SkillActionCapability.SWIPE) {
            return normalizedX != null && normalizedX in 0..1000 &&
                normalizedY != null && normalizedY in 0..1000 &&
                endNormalizedX != null && endNormalizedX in 0..1000 &&
                endNormalizedY != null && endNormalizedY in 0..1000 &&
                durationMs != null && durationMs in 50L..10_000L
        }
        if (capability == SkillActionCapability.WAIT) {
            return durationMs != null && durationMs in 100L..60_000L &&
                normalizedX == null && normalizedY == null &&
                endNormalizedX == null && endNormalizedY == null
        }
        return normalizedX == null && normalizedY == null &&
            endNormalizedX == null && endNormalizedY == null && durationMs == null
    }
}
