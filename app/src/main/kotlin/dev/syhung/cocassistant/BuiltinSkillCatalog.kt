package dev.syhung.cocassistant
import org.json.JSONArray
import org.json.JSONObject

/** Initial declarative catalog. Entries describe capabilities; they are not executable workflows. */
object BuiltinSkillCatalog {
    private data class Definition(
        val id: String, val name: String, val category: String, val description: String,
        val inputs: List<String> = emptyList(), val outputs: List<String> = emptyList(),
        val dependencies: List<String> = emptyList(),
        val capabilities: Set<SkillActionCapability> = emptySet()
    )

    private val definitions = listOf(
        Definition("screen.observe", "Quan sát màn hình", "observation", "Phân tích trạng thái màn hình hiện tại.", outputs = listOf("screen_state"), capabilities = setOf(SkillActionCapability.OBSERVE_SCREEN)),
        Definition("screen.capture", "Chụp màn hình", "observation", "Yêu cầu ảnh chụp màn hình theo quyền Android.", outputs = listOf("bitmap"), capabilities = setOf(SkillActionCapability.OBSERVE_SCREEN)),
        Definition("vision.crop", "Cắt vùng ảnh", "vision", "Cắt vùng ảnh bằng tọa độ chuẩn hóa.", inputs = listOf("bitmap", "normalized_bounds"), outputs = listOf("crop"), capabilities = setOf(SkillActionCapability.ANALYZE_IMAGE)),
        Definition("vision.ocr", "Nhận diện văn bản", "vision", "Đọc văn bản và vị trí văn bản từ ảnh.", inputs = listOf("bitmap"), outputs = listOf("text_regions"), dependencies = listOf("screen.capture"), capabilities = setOf(SkillActionCapability.READ_TEXT)),
        Definition("vision.template", "So khớp mẫu hình ảnh", "vision", "So khớp vùng ảnh với mẫu đã xác minh.", inputs = listOf("crop", "template_id"), outputs = listOf("match_score", "bounds"), capabilities = setOf(SkillActionCapability.ANALYZE_IMAGE)),
        Definition("action.tap", "Chạm màn hình", "primitive", "Chạm vào tọa độ chuẩn hóa.", inputs = listOf("normalized_x", "normalized_y", "duration_ms"), capabilities = setOf(SkillActionCapability.TAP)),
        Definition("action.swipe", "Vuốt màn hình", "primitive", "Vuốt từ điểm A đến điểm B.", inputs = listOf("start_x", "start_y", "end_x", "end_y", "duration_ms"), capabilities = setOf(SkillActionCapability.SWIPE)),
        Definition("time.wait", "Chờ theo thời gian", "primitive", "Chờ theo đồng hồ Workflow Engine.", inputs = listOf("duration_ms"), outputs = listOf("elapsed_ms"), capabilities = setOf(SkillActionCapability.WAIT))
    )

    private val retiredSchedulerSkills = setOf("account.schedule", "scheduler.schedule", "scheduler.cancel", "agent.scheduler")

    fun all(): List<AgentSkill> = definitions.map { item ->
        AgentSkill(
            id = item.id, name = item.name, description = item.description,
            category = item.category, version = 1, status = SkillLifecycle.RESEARCH_REQUIRED,
            inputs = item.inputs, outputs = item.outputs, dependencies = item.dependencies,
            capabilities = item.capabilities, source = "builtin_catalog", confidence = 0f
        )
    }

    fun install(registry: AgentSkillRegistry): Int {
        registry.removeCatalogSkills(retiredSchedulerSkills)
        var added = 0
        all().forEach {
            if (registry.find(it.id) == null && registry.saveCatalogSkill(it)) added++
            else registry.markCatalogForResearch(it)
        }
        return added
    }

    fun bootstrap(registry: AgentSkillRegistry) {
        val primitives = all().filter { it.category == "primitive" || it.id == "screen.capture" }
        primitives.forEach { skill ->
            val existing = registry.find(skill.id)
            if (existing == null || existing.status != SkillLifecycle.VERIFIED) {
                // Save as DRAFT then verify
                val draft = skill.copy(
                    scriptJson = JSONObject()
                        .put("format", "CASL")
                        .put("schemaVersion", 1)
                        .put("kind", "skill")
                        .put("id", skill.id)
                        .put("version", 1)
                        .put("steps", JSONArray())
                        .toString()
                )
                if (registry.saveCatalogSkill(draft)) {
                   registry.completeTest(skill.id, 1, 1)
                }
            }
        }
    }
}
