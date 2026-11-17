package dev.syhung.cocassistant

import kotlinx.coroutines.CancellationException

import org.json.JSONObject

sealed class SkillDraftOutcome {
    data class Created(val skill: AgentSkill, val missingDependencies: List<String>) : SkillDraftOutcome()
    data class Failed(val reason: String) : SkillDraftOutcome()
}

/** Gemini-assisted research-to-Draft flow. Model output is parsed as data and never executed. */
object GeminiSkillAuthoring {
    suspend fun createDraft(
        apiKey: String,
        goal: String,
        registry: AgentSkillRegistry,
        availableApiNames: List<String> = emptyList()
    ): SkillDraftOutcome {
        if (goal.isBlank()) return SkillDraftOutcome.Failed("Mục tiêu nghiên cứu không được để trống.")
        val inventory = registry.all().joinToString("\n") {
            "${it.id} | ${it.category} | ${it.description} | ${it.status.name}"
        }
        val apiInventory = availableApiNames.distinct().sorted().joinToString("\n")
            .ifBlank { "(chưa có API adapter được đăng ký)" }
        val prompt = """
            Bạn là Skill Researcher của CoC AI Agent.
            Hãy nghiên cứu yêu cầu và đề xuất đúng MỘT Skill khai báo mới hoặc một phiên bản cải tiến.
            Mục tiêu: $goal

            Skill hiện có:
            $inventory

            API adapter allow-list:
            $apiInventory

            Trả về duy nhất một JSON object, không Markdown, theo các trường:
            id, name, description, category, version, inputs, outputs, dependencies,
            capabilities, instructions, script, source, confidence, minAgentVersion,
            maxAgentVersion, dependencyVersions.
            capabilities chỉ được dùng: OBSERVE_SCREEN, READ_TEXT, ANALYZE_IMAGE, TAP, SWIPE, WAIT, VERIFY_STATE.
            dependencies chỉ được tham chiếu Skill ID có trong danh sách.
            Không tạo mã Kotlin, Java, shell, lệnh hệ thống hoặc nội dung thực thi tùy ý.
            Không tự đánh dấu Verified. Kết quả sẽ luôn được lưu ở Draft để kiểm thử.
            instructions là mô tả ngắn. script là CASL JSON AST hoàn chỉnh, kind=skill,
            id trùng id Skill, version trùng version Skill, schemaVersion=1.
            script.steps chỉ dùng các op: set, output, call, skill, if, repeat, wait, assert, log, return.
            Mỗi call phải gọi tên API trong allow-list; nếu danh sách trống thì không được dùng call. API action.tap/action.swipe/action.wait chỉ chạy khi Skill đã VERIFIED, capability tương ứng được khai báo và Accessibility đã được người dùng bật.
            Điều kiện hỗ trợ all / any / not và exists, not_exists, equals, not_equals, contains, starts_with, ends_with, greater_than, less_than, greater_or_equal, less_or_equal, in, not_in. Không tạo mã Kotlin, Java, JavaScript, shell, reflection hoặc nội dung thực thi tùy ý.
        """.trimIndent()
        return runCatching {
            val raw = GeminiClient.generate(apiKey, prompt)
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            require(start >= 0 && end > start) { "Gemini không trả về JSON Skill hợp lệ." }
            val parsed = JSONObject(raw.substring(start, end + 1))
            val script = parsed.optJSONObject("script")
                ?: error("Skill Draft phải có CASL script.")
            require(script.optString("format") == "CASL" &&
                script.optInt("schemaVersion", -1) == CaslScriptRuntime.SCHEMA_VERSION &&
                script.optString("kind") == "skill") { "CASL script không đúng schema." }
            val skill = AgentSkill.fromJson(parsed)
                ?: error("Skill không vượt qua kiểm tra schema.")
            require(script.optString("id") == skill.id && script.optInt("version", 0) == skill.version) {
                "ID/version trong Skill Manifest và CASL script không khớp."
            }
            CaslScriptRuntime(CaslApiRegistry(), { _, _ -> null }).validateProgramDocument(script)
            val draft = skill.copy(
                status = SkillLifecycle.DRAFT,
                scriptJson = script.toString(),
                source = "gemini_research"
            )
            require(registry.saveDraft(draft)) {
                "Không thể lưu Skill. Hãy tăng version nếu ID đã tồn tại."
            }
            val known = registry.all().map { it.id }.toSet()
            SkillDraftOutcome.Created(draft, draft.dependencies.filterNot(known::contains))
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            SkillDraftOutcome.Failed(error.message ?: "Không thể tạo Skill Draft.")
        }
    }

    /** Researches one catalog Skill and stores the researched result as a new immutable version. */
    suspend fun researchExistingSkill(
        apiKey: String,
        skillId: String,
        registry: AgentSkillRegistry,
        availableApiNames: List<String> = emptyList()
    ): SkillDraftOutcome {
        if (apiKey.isBlank()) return SkillDraftOutcome.Failed("Chưa có Gemini API Key.")
        val current = registry.find(skillId)
            ?: return SkillDraftOutcome.Failed("Không tìm thấy Skill cần nghiên cứu.")
        if (current.status == SkillLifecycle.VERIFIED) {
            return SkillDraftOutcome.Failed("Skill đã được xác minh; hãy tạo phiên bản cải tiến riêng.")
        }
        if (current.status == SkillLifecycle.RESEARCHING) {
            return SkillDraftOutcome.Failed("Skill này đang được nghiên cứu.")
        }
        val inventory = registry.all().joinToString("\\n") {
            "${it.id} | ${it.name} | ${it.status.name} | ${it.description}"
        }
        val allowList = availableApiNames.distinct().sorted().joinToString(", ")
            .ifBlank { "(không có API adapter thực thi được đăng ký)" }
        val prompt = """
            Bạn là nhà nghiên cứu kỹ năng của CoC Agent. Hãy nghiên cứu Skill được cung cấp,
            mô tả quy trình UI theo từng bước, điều kiện trước/sau, cách nhận diện trạng thái,
            lỗi có thể gặp và cách xác minh kết quả. Không được bịa tọa độ hoặc khẳng định
            đã thử nghiệm trên thiết bị. Nếu thiếu ảnh/bằng chứng, ghi rõ cần quan sát thêm.
            Skill cần nghiên cứu: ${current.id}
            Tên: ${current.name}
            Mục tiêu: ${current.description}
            Đầu vào: ${current.inputs}
            Đầu ra: ${current.outputs}
            Capability: ${current.capabilities}
            Phiên bản mới bắt buộc: ${current.version + 1}
            Danh mục Skill: $inventory
            API allow-list: $allowList

            Trả về đúng một JSON Skill manifest gồm id,name,description,category,version,
            inputs,outputs,dependencies,capabilities,instructions,script,source,confidence,
            minAgentVersion,maxAgentVersion,dependencyVersions.
            id phải giữ nguyên ${current.id}; version phải là ${current.version + 1}.
            instructions phải có: mục đích, điều kiện đầu vào, các bước quan sát/thao tác,
            trạng thái mong đợi sau mỗi bước, cách xác minh, điều kiện dừng an toàn và giới hạn.
            script phải là CASL JSON kind=skill, schemaVersion=1, cùng id/version.
            Chỉ dùng operation CASL đã hỗ trợ và API nằm trong allow-list; không tạo API mới.
            Không tự đánh dấu VERIFIED. Không tạo Kotlin, Java, JavaScript, shell hay mã tùy ý.
        """.trimIndent()
        registry.setLifecycle(current.id, SkillLifecycle.RESEARCHING, current.version)
        return runCatching {
            val raw = GeminiClient.generate(apiKey, prompt)
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            require(start >= 0 && end > start) { "Gemini không trả về hồ sơ nghiên cứu JSON." }
            val parsed = JSONObject(raw.substring(start, end + 1))
            val script = parsed.optJSONObject("script") ?: error("Thiếu CASL script.")
            val researched = AgentSkill.fromJson(parsed) ?: error("Manifest Skill không hợp lệ.")
            require(researched.instructions.length >= 80) { "Hồ sơ nghiên cứu thiếu quy trình thao tác và xác minh chi tiết." }
            require(researched.id == current.id && researched.version == current.version + 1) {
                "Gemini đã thay đổi ID hoặc version không đúng."
            }
            require(script.optString("format") == "CASL" &&
                script.optInt("schemaVersion", -1) == CaslScriptRuntime.SCHEMA_VERSION &&
                script.optString("kind") == "skill" &&
                script.optString("id") == researched.id &&
                script.optInt("version", 0) == researched.version) {
                "CASL script không khớp manifest."
            }
            CaslScriptRuntime(CaslApiRegistry(), { _, _ -> null }).validateProgramDocument(script)
            val saved = researched.copy(
                status = SkillLifecycle.RESEARCHED,
                scriptJson = script.toString(),
                source = "gemini_skill_research"
            )
            require(registry.saveResearched(saved)) { "Không thể lưu phiên bản nghiên cứu mới." }
            registry.setLifecycle(current.id, SkillLifecycle.RESEARCH_REQUIRED, current.version)
            val known = registry.all().map { it.id }.toSet()
            SkillDraftOutcome.Created(saved, saved.dependencies.filterNot(known::contains))
        }.getOrElse { error ->
            registry.setLifecycle(current.id, SkillLifecycle.RESEARCH_REQUIRED, current.version)
            if (error is CancellationException) throw error
            SkillDraftOutcome.Failed(error.message ?: "Nghiên cứu Skill thất bại.")
        }
    }

}
