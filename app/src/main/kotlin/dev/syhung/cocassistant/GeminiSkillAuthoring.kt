package dev.syhung.cocassistant

import android.graphics.Bitmap
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
        referenceImage: Bitmap? = null,
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
            Nếu có ảnh tham chiếu, dùng ảnh đó làm dữ liệu quan sát và phân biệt điều đã thấy với suy luận.
            instructions là mô tả ngắn. script là CASL JSON AST hoàn chỉnh, kind=skill,
            id trùng id Skill, version trùng version Skill, schemaVersion=1.
            script.steps chỉ dùng các op: set, output, call, skill, if, repeat, wait, assert, log, return.
            Mỗi call phải gọi tên API trong allow-list; nếu danh sách trống thì không được dùng call. API action.tap/action.swipe/action.wait chỉ chạy khi Skill đã VERIFIED, capability tương ứng được khai báo và Accessibility đã được người dùng bật.
            Điều kiện hỗ trợ all / any / not và exists, not_exists, equals, not_equals, contains, starts_with, ends_with, greater_than, less_than, greater_or_equal, less_or_equal, in, not_in. Không tạo mã Kotlin, Java, JavaScript, shell, reflection hoặc nội dung thực thi tùy ý.
        """.trimIndent()
        return runCatching {
            val raw = GeminiClient.generate(apiKey, prompt, referenceImage)
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
            SkillDraftOutcome.Failed(error.message ?: "Không thể tạo Skill Draft.")
        }
    }
}
