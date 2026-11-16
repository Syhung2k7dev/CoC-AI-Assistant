package dev.syhung.cocassistant

import org.json.JSONArray
import org.json.JSONObject

sealed class WorkflowDraftOutcome {
    data class Created(val workflow: JSONObject, val missingSkills: List<String>) : WorkflowDraftOutcome()
    data class Failed(val reason: String) : WorkflowDraftOutcome()
}

/** Gemini authors versioned Workflow CASL documents; drafts are data and are never executed here. */
object GeminiWorkflowAuthoring {
    suspend fun createDraft(
        apiKey: String,
        goal: String,
        workflows: CaslWorkflowRegistry,
        skills: AgentSkillRegistry,
        availableApiNames: List<String> = emptyList()
    ): WorkflowDraftOutcome {
        if (goal.isBlank()) return WorkflowDraftOutcome.Failed("Mục tiêu Workflow không được để trống.")
        val skillInventory = skills.all()
            .filter { it.status == SkillLifecycle.VERIFIED && it.scriptJson.isNotBlank() }
            .joinToString("\n") { it.id + "@" + it.version + ": " + it.description }
        val apiInventory = availableApiNames.distinct().sorted().joinToString("\n")
            .ifBlank { "(chưa có API adapter được đăng ký)" }
        val refKey = "$" + "ref"
        val prompt = """
            Bạn là Workflow Architect của CoC AI Agent.
            Hãy tạo đúng một Workflow CASL có thể kiểm tra và lưu thành Draft.
            Mục tiêu người dùng: $goal

            Skill đã Verified và có script:
            $skillInventory

            API adapter allow-list hiện có:
            $apiInventory

            Trả về một JSON object duy nhất với format=CASL, schemaVersion=1,
            id, version, kind=workflow, inputs, outputs, dependencies, steps.
            Chỉ dùng operations: set, call, skill, if, repeat, wait, assert, log, return.
            Chỉ gọi Skill ID trong danh sách; mỗi operation skill phải pin version cụ thể.
            Chỉ gọi API name trong allow-list; nếu danh sách trống thì không được dùng call.
            Điều kiện hỗ trợ: exists, equals, not_equals, contains, greater_than, less_than, in.
            Tham chiếu biến bằng object có khóa $refKey, ví dụ {"$refKey":"vars.observation"}.
            Không tạo mã Kotlin/Java/JavaScript/shell, reflection hoặc lệnh tùy ý.
            Không tự đánh dấu Verified. Workflow luôn được lưu ở Draft để kiểm thử.
            Thiết kế có nhánh lỗi và log; không giả định API thành công nếu chưa kiểm tra kết quả.
        """.trimIndent()
        return runCatching {
            val raw = GeminiClient.generate(apiKey, prompt)
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            require(start >= 0 && end > start) { "Gemini không trả về CASL Workflow JSON." }
            val program = JSONObject(raw.substring(start, end + 1))
            require(program.optString("format") == "CASL" &&
                program.optInt("schemaVersion", -1) == CaslScriptRuntime.SCHEMA_VERSION &&
                program.optString("kind") == "workflow") { "Workflow không đúng CASL schema." }
            val known = skills.all().filter { it.status == SkillLifecycle.VERIFIED }
                .associateBy { it.id }
            val refs = collectSkillRefs(program.optJSONArray("steps") ?: JSONArray())
            val missing = refs.filter { (id, version) -> known[id]?.version != version }.keys.toList()
            require(workflows.saveDraft(program)) {
                "Không thể lưu Workflow. Hãy kiểm tra ID, version hoặc CASL AST."
            }
            WorkflowDraftOutcome.Created(program, missing)
        }.getOrElse { error ->
            WorkflowDraftOutcome.Failed(error.message ?: "Không thể tạo Workflow Draft.")
        }
    }

    private fun collectSkillRefs(steps: JSONArray): Map<String, Int> {
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
                    found.putAll(collectSkillRefs(step.optJSONArray("then") ?: JSONArray()))
                    found.putAll(collectSkillRefs(step.optJSONArray("else") ?: JSONArray()))
                }
                "repeat" -> found.putAll(collectSkillRefs(step.optJSONArray("steps") ?: JSONArray()))
            }
        }
        return found
    }
}
