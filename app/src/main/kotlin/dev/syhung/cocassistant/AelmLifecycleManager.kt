package dev.syhung.cocassistant

import android.content.Context
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

data class AelmTestCase(
    val id: String,
    val inputs: JSONObject = JSONObject(),
    val expectedOutputs: JSONObject = JSONObject()
)

data class AelmTestReport(
    val artifactType: String,
    val artifactId: String,
    val version: Int,
    val passed: Int,
    val total: Int,
    val failures: List<String>
) {
    val successful: Boolean get() = total > 0 && passed == total && failures.isEmpty()
}

/**
 * AELM (Agent Evolution Lifecycle Manager).
 *
 * It coordinates AI authoring, immutable version creation, deterministic sandbox tests,
 * and evidence-backed release. Model output is always treated as untrusted JSON data.
 * The sandbox intentionally exposes only test.* APIs; production side effects are never
 * invoked while evaluating a draft.
 */
class AelmLifecycleManager(context: Context) {
    private val appContext = context.applicationContext
    private val skills = AgentSkillRegistry(appContext)
    private val workflows = CaslWorkflowRegistry(appContext)
    private val cadss = CadssStore(appContext)

    suspend fun createSkillDraft(
        apiKey: String,
        goal: String,
        referenceImage: android.graphics.Bitmap? = null
    ): SkillDraftOutcome = GeminiSkillAuthoring.createDraft(
        apiKey = apiKey,
        goal = goal,
        registry = skills,
        referenceImage = referenceImage,
        availableApiNames = sandboxApiNames()
    )

    suspend fun reviseSkillDraft(
        apiKey: String,
        skillId: String,
        baseVersion: Int,
        changeRequest: String
    ): SkillDraftOutcome {
        if (changeRequest.isBlank()) return SkillDraftOutcome.Failed("Yêu cầu sửa Skill không được để trống.")
        val base = skills.find(skillId, baseVersion)
            ?: return SkillDraftOutcome.Failed("Không tìm thấy phiên bản Skill gốc.")
        val nextVersion = baseVersion + 1
        val apiInventory = sandboxApiNames().joinToString(", ")
        val prompt = """
            Bạn là AELM Skill Maintainer. Tạo phiên bản Skill mới dựa trên manifest và CASL script bên dưới.
            Chỉ thực hiện yêu cầu thay đổi, giữ nguyên phần không liên quan.
            Manifest gốc:
            ${base.toJson().put("script", JSONObject(base.scriptJson.ifBlank { "{}" })).toString()}
            Yêu cầu:
            $changeRequest

            Trả về đúng một JSON object, các trường manifest gồm id, name, description, category,
            version, inputs, outputs, dependencies, capabilities, instructions, script, source,
            confidence, minAgentVersion, maxAgentVersion, dependencyVersions.
            Bắt buộc id=$skillId và version=$nextVersion.
            script phải là CASL JSON object có format=CASL, schemaVersion=1, kind=skill,
            id=$skillId, version=$nextVersion và steps.
            Chỉ dùng set, output, call, skill, if, repeat, wait, assert, log, return.
            Skill dependency phải pin version. Chỉ gọi API trong allow-list sau: $apiInventory.
            Không tạo Kotlin/Java/JavaScript/shell, reflection hoặc mã thực thi tùy ý.
            Không tự đánh dấu VERIFIED. Phiên bản mới luôn là Draft.
        """.trimIndent()
        return runCatching {
            val raw = GeminiClient.generate(apiKey, prompt)
            val parsed = extractJson(raw)
            val script = parsed.optJSONObject("script")
                ?: throw IllegalArgumentException("Skill mới thiếu CASL script.")
            require(parsed.optString("id") == skillId && parsed.optInt("version") == nextVersion) {
                "Skill mới phải giữ ID và tăng đúng một phiên bản."
            }
            require(script.optString("format") == "CASL" &&
                script.optInt("schemaVersion", -1) == CaslScriptRuntime.SCHEMA_VERSION &&
                script.optString("kind") == "skill" &&
                script.optString("id") == skillId &&
                script.optInt("version", 0) == nextVersion) {
                "Manifest và CASL script không khớp ID/version."
            }
            parsed.put("script", script.toString())
            val candidate = AgentSkill.fromJson(parsed)
                ?: throw IllegalArgumentException("Skill mới không vượt qua schema validation.")
            CaslScriptRuntime(CaslApiRegistry(), { _, _ -> null }).validateProgramDocument(script)
            require(candidate.dependencies.all { dependency ->
                val required = candidate.dependencyVersions[dependency]
                val found = skills.find(dependency)
                found != null && found.status == SkillLifecycle.VERIFIED &&
                    (required == null || found.version >= required)
            }) { "Skill mới có dependency chưa VERIFIED hoặc không tương thích." }
            require(skills.saveDraft(candidate.copy(status = SkillLifecycle.DRAFT, source = "aelm_revision"))) {
                "Không thể lưu phiên bản Skill Draft mới."
            }
            SkillDraftOutcome.Created(candidate.copy(status = SkillLifecycle.DRAFT, source = "aelm_revision"), emptyList())
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            SkillDraftOutcome.Failed(error.message ?: "Không thể sửa Skill.")
        }
    }

    suspend fun createWorkflowDraft(apiKey: String, goal: String): WorkflowDraftOutcome =
        GeminiWorkflowAuthoring.createDraft(
            apiKey, goal, workflows, skills, workflowApiNames()
        )

    suspend fun reviseWorkflowDraft(
        apiKey: String,
        workflowId: String,
        baseVersion: Int,
        changeRequest: String
    ): WorkflowDraftOutcome = GeminiWorkflowAuthoring.reviseDraft(
        apiKey, workflowId, baseVersion, changeRequest, workflows, skills, workflowApiNames()
    )

    suspend fun testSkill(
        skillId: String,
        version: Int,
        cases: List<AelmTestCase>
    ): AelmTestReport {
        validateCases(cases)
        val skill = skills.find(skillId, version)
            ?: throw CaslRuntimeException("Không tìm thấy Skill $skillId@$version.")
        require(skill.status == SkillLifecycle.DRAFT) { "Chỉ Skill Draft mới được kiểm thử phát hành." }
        require(skills.all().none { it.id == skillId && it.status == SkillLifecycle.TESTING }) {
            "Đã có một phiên bản Skill khác đang ở trạng thái TESTING."
        }
        requireDependenciesReady(skill)
        val program = JSONObject(skill.scriptJson)
        require(program.optString("id") == skill.id && program.optInt("version", 0) == skill.version) {
            "CASL script không khớp Skill manifest ID/version."
        }
        CaslScriptRuntime(CaslApiRegistry(), { _, _ -> null }).validateProgramDocument(program)
        cadss.removeRecord("aelm_test_receipts", "receiptKey", receiptKey("skill", skillId, version))
        val failures = mutableListOf<String>()
        var passed = 0
        cases.forEach { test ->
            try {
                val runtime = CaslScriptRuntime(
                    apiRegistry = sandboxRegistry(),
                    skillScriptProvider = { dependencyId, dependencyVersion ->
                        skills.findVerifiedScript(dependencyId, dependencyVersion)
                    }
                )
                val result = runtime.execute(program, test.inputs, userApproved = true)
                assertExpected(test.expectedOutputs, result.outputs)
                passed++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failures += "${test.id}: ${(error.message ?: error::class.java.simpleName).take(240)}"
            }
        }
        val report = AelmTestReport("skill", skillId, version, passed, cases.size, failures)
        if (report.successful) saveReceipt(report, skill.toJson())
        return report
    }

    suspend fun testWorkflow(
        workflowId: String,
        version: Int,
        cases: List<AelmTestCase>
    ): AelmTestReport {
        validateCases(cases)
        val program = workflows.find(workflowId, version)
            ?: throw CaslRuntimeException("Không tìm thấy Workflow $workflowId@$version.")
        require(program.optString("lifecycle") == WorkflowLifecycle.DRAFT.name) {
            "Chỉ Workflow Draft mới được kiểm thử phát hành."
        }
        require(program.optString("id") == workflowId && program.optInt("version", 0) == version) {
            "CASL program không khớp Workflow ID/version."
        }
        CaslScriptRuntime(CaslApiRegistry(), { _, _ -> null }).validateProgramDocument(program)
        cadss.removeRecord("aelm_test_receipts", "receiptKey", receiptKey("workflow", workflowId, version))
        val failures = mutableListOf<String>()
        var passed = 0
        cases.forEach { test ->
            try {
                val runtime = CaslScriptRuntime(
                    apiRegistry = sandboxRegistry(),
                    skillScriptProvider = { dependencyId, dependencyVersion ->
                        skills.findVerifiedScript(dependencyId, dependencyVersion)
                    }
                )
                val result = runtime.execute(program, test.inputs, userApproved = true)
                assertExpected(test.expectedOutputs, result.outputs)
                passed++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failures += "${test.id}: ${(error.message ?: error::class.java.simpleName).take(240)}"
            }
        }
        val report = AelmTestReport("workflow", workflowId, version, passed, cases.size, failures)
        if (report.successful) saveReceipt(report, program)
        return report
    }

    fun releaseSkill(skillId: String, version: Int): Boolean {
        val skill = skills.find(skillId, version) ?: return false
        if (skill.status != SkillLifecycle.DRAFT || !hasValidReceipt("skill", skillId, version, skill.toJson())) {
            return false
        }
        if (!requireSingleTestingSkill(skillId)) return false
        if (!skills.setLifecycle(skillId, SkillLifecycle.TESTING, version)) return false
        val total = receiptTotal("skill", skillId, version)
        val released = skills.completeTest(skillId, total, total)
        if (!released) skills.setLifecycle(skillId, SkillLifecycle.EXPERIMENTAL, version)
        return released
    }

    fun releaseWorkflow(workflowId: String, version: Int): Boolean {
        val program = workflows.find(workflowId, version) ?: return false
        if (program.optString("lifecycle") != WorkflowLifecycle.DRAFT.name ||
            !hasValidReceipt("workflow", workflowId, version, program)) return false
        if (!workflows.setLifecycle(workflowId, version, WorkflowLifecycle.TESTING)) return false
        val total = receiptTotal("workflow", workflowId, version)
        val released = workflows.completeTest(workflowId, version, total, total)
        if (!released) workflows.setLifecycle(workflowId, version, WorkflowLifecycle.DISABLED)
        return released
    }

    private fun requireDependenciesReady(skill: AgentSkill) {
        val latestVerified = skills.all()
            .filter { it.status == SkillLifecycle.VERIFIED }
            .groupBy { it.id }
            .mapValues { (_, versions) -> versions.maxByOrNull { it.version } }
        skill.dependencies.forEach { dependency ->
            val found = latestVerified[dependency]
                ?: throw CaslRuntimeException("Thiếu Skill dependency VERIFIED: $dependency")
            val required = skill.dependencyVersions[dependency]
            if (required != null && found.version < required) {
                throw CaslRuntimeException("Skill dependency chưa đạt version yêu cầu: $dependency")
            }
        }
    }

    private fun requireSingleTestingSkill(skillId: String): Boolean =
        skills.all().none { it.id == skillId && it.status == SkillLifecycle.TESTING }

    private fun validateCases(cases: List<AelmTestCase>) {
        require(cases.isNotEmpty() && cases.size <= 25) { "Cần từ 1 đến 25 test case." }
        require(cases.map { it.id }.toSet().size == cases.size) { "Test case ID không được trùng." }
        cases.forEach {
            require(it.id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")))
            require(it.inputs.toString().length <= 16_000 && it.expectedOutputs.toString().length <= 16_000) {
                "Dữ liệu test vượt giới hạn."
            }
        }
    }

    private fun sandboxRegistry(): CaslApiRegistry {
        val memory = linkedMapOf<String, MutableList<JSONObject>>()
        return CaslApiRegistry().apply {
            register("memory.read") { arguments, _ ->
                val collection = arguments.optString("collection")
                require(collection.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
                val key = arguments.optString("key")
                val records = memory[collection].orEmpty().filter {
                    key.isBlank() || it.optString("key") == key
                }
                JSONObject().put("records", JSONArray(records.map { JSONObject(it.toString()) }))
            }
            register("memory.write") { arguments, _ ->
                val collection = arguments.optString("collection")
                require(collection.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
                val record = arguments.optJSONObject("record")
                    ?: throw CaslRuntimeException("memory.write cần record object.")
                memory.getOrPut(collection) { mutableListOf() }.add(JSONObject(record.toString()))
                JSONObject().put("written", true).put("collection", collection)
            }
            register("memory.append") { arguments, _ ->
                val collection = arguments.optString("collection", "workflow_events")
                require(collection.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
                val event = arguments.optJSONObject("event")
                    ?: throw CaslRuntimeException("memory.append cần event object.")
                memory.getOrPut(collection) { mutableListOf() }
                    .add(JSONObject(event.toString()).put("recordedAt", 1_700_000_000_000L))
                JSONObject().put("appended", true)
            }
            register("system.time") { _, _ -> JSONObject().put("epochMillis", 1_700_000_000_000L) }
            register("vision.match") { arguments, _ ->
                val hash = arguments.optString("hash")
                require(hash.matches(Regex("[0-9a-fA-F]{16}"))) {
                    "vision.match cần pHash 16 ký tự hệ thập lục phân."
                }
                JSONObject()
                    .put("matched", true)
                    .put("templateId", "sandbox-template")
                    .put("label", "Sandbox Vision Template")
                    .put("distance", 0)
            }
            register("vision.feedback") { arguments, context ->
                require(context.optString("skillId").isNotBlank() && context.optInt("skillVersion", 0) >= 1) {
                    "vision.feedback chỉ khả dụng trong Skill có ID/version."
                }
                val templateId = arguments.optString("templateId")
                require(templateId == "sandbox-template") {
                    "vision.feedback chỉ cập nhật template giả lập trong sandbox."
                }
                JSONObject()
                    .put("updated", true)
                    .put("templateId", templateId)
                    .put("status", "VERIFIED")
                    .put("confidence", if (arguments.optBoolean("success", false)) 0.75 else 0.25)
            }
            register("action.tap") { arguments, context ->
                require(arguments.optInt("x", -1) in 0..1000 && arguments.optInt("y", -1) in 0..1000)
                require(arguments.optLong("durationMs", 100L) in 50L..10_000L)
                JSONObject().put("accepted", true).put("skillId", context.optString("skillId"))
            }
            register("action.swipe") { arguments, context ->
                require(arguments.optInt("x", -1) in 0..1000 && arguments.optInt("y", -1) in 0..1000)
                require(arguments.optInt("endX", -1) in 0..1000 && arguments.optInt("endY", -1) in 0..1000)
                require(arguments.optLong("durationMs", -1L) in 50L..10_000L)
                JSONObject().put("accepted", true).put("skillId", context.optString("skillId"))
            }
            register("action.wait") { arguments, context ->
                require(arguments.optLong("durationMs", -1L) in 100L..60_000L)
                JSONObject().put("completed", true).put("skillId", context.optString("skillId"))
            }
        }
    }

    private fun workflowApiNames(): List<String> =
        sandboxApiNames().filterNot { it == "vision.feedback" }

    private fun sandboxApiNames(): List<String> =
        listOf("memory.read", "memory.write", "memory.append", "system.time", "vision.match", "vision.feedback", "action.tap", "action.swipe", "action.wait")


    private fun assertExpected(expected: JSONObject, actual: JSONObject) {
        expected.keys().forEach { key ->
            require(expected.has(key) && equivalent(expected.opt(key), actual.opt(key))) {
                "Output '$key' không khớp expected value."
            }
        }
    }

    private fun equivalent(left: Any?, right: Any?): Boolean {
        if (left === JSONObject.NULL || right === JSONObject.NULL) return left === right
        if (left is JSONObject && right is JSONObject) {
            if (left.length() != right.length()) return false
            return left.keys().asSequence().all { key -> right.has(key) && equivalent(left.opt(key), right.opt(key)) }
        }
        if (left is JSONArray && right is JSONArray) {
            if (left.length() != right.length()) return false
            return (0 until left.length()).all { equivalent(left.opt(it), right.opt(it)) }
        }
        if (left is Number && right is Number) return left.toDouble() == right.toDouble()
        return left == right
    }

    private fun saveReceipt(report: AelmTestReport, program: JSONObject) {
        val key = receiptKey(report.artifactType, report.artifactId, report.version)
        val value = JSONObject()
            .put("artifactType", report.artifactType)
            .put("artifactId", report.artifactId)
            .put("version", report.version)
            .put("passed", report.passed)
            .put("total", report.total)
            .put("programHash", sha256(program.toString()))
            .put("testedAt", System.currentTimeMillis())
        cadss.putRecord("aelm_test_receipts", "receiptKey", key, value)
    }

    private fun hasValidReceipt(type: String, id: String, version: Int, program: JSONObject): Boolean {
        val receipt = cadss.findRecord("aelm_test_receipts", "receiptKey", receiptKey(type, id, version))
            ?: return false
        return receipt.optString("artifactType") == type &&
            receipt.optString("artifactId") == id &&
            receipt.optInt("version") == version &&
            receipt.optInt("passed") == receipt.optInt("total") &&
            receipt.optInt("total") > 0 &&
            receipt.optString("programHash") == sha256(program.toString())
    }

    private fun receiptTotal(type: String, id: String, version: Int): Int =
        cadss.findRecord("aelm_test_receipts", "receiptKey", receiptKey(type, id, version))
            ?.optInt("total", 0) ?: 0

    private fun receiptKey(type: String, id: String, version: Int) = "receipt:$type:$id:$version"

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun extractJson(raw: String): JSONObject {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI không trả về JSON hợp lệ." }
        return JSONObject(raw.substring(start, end + 1))
    }
}
