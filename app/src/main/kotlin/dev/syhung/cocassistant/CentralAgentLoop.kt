package dev.syhung.cocassistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Bounded observe-decide-act-verify loop.
 * Gemini can select a pinned VERIFIED Skill, but cannot emit coordinates or native gestures.
 * All device actions remain behind ActionExecutor and the user's explicit start action.
 */
object CentralAgentLoop {
    private const val MAX_CYCLES = 6
    private const val MAX_SKILL_ACTIONS = 5
    private const val MAX_SKILL_RUN_MS = 90_000L
    private const val MAX_SKILL_STEPS = 120
    private const val GAME_PACKAGE = "com.supercell.clashofclans"

    suspend fun run(
        context: Context,
        apiKey: String,
        goal: String,
        registry: AgentSkillRegistry,
        shareScreenshot: Boolean,
        onStatus: (String) -> Unit
    ): String {
        require(apiKey.isNotBlank()) { "Chưa có Gemini API Key." }
        require(goal.isNotBlank()) { "Mục tiêu không được để trống." }
        check(CoCAccessibilityService.isConnected()) {
            "Chưa kết nối Trợ năng. Hãy bật dịch vụ trong Cài đặt Android."
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(GAME_PACKAGE)
            ?: error("Không tìm thấy Clash of Clans trên thiết bị.")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        delay(2_200L)

        val verifiedSkills = registry.all()
            .filter { it.status == SkillLifecycle.VERIFIED && registry.findVerifiedScript(it.id, it.version) != null }
        require(verifiedSkills.isNotEmpty()) {
            "Chưa có Skill VERIFIED có CASL script. Hãy nghiên cứu, kiểm thử và xác minh Skill trước."
        }
        val inventory = verifiedSkills.joinToString("\n") {
            "id=${it.id}; version=${it.version}; name=${it.name}; description=${it.description}; " +
                "capabilities=${it.capabilities}; inputs=${it.inputs}; outputs=${it.outputs}; " +
                "instructions=${it.instructions.take(900)}"
        }

        var previousAction = "Chưa có hành động nào."
        var previousVerification = "not_applicable"
        var previousEvidence = ""
        val skillRuns = linkedMapOf<String, Int>()
        var totalSkillRuns = 0
        val executionNotes = mutableListOf<String>()

        for (cycle in 1..MAX_CYCLES) {
            onStatus("Vòng $cycle/$MAX_CYCLES · Đang quan sát màn hình CoC…")
            val screenshot = captureScreen()
                ?: error("Không chụp được màn hình. Agent đã dừng, không thực hiện thao tác.")
            val ocrBitmap = screenshot.copy(Bitmap.Config.ARGB_8888, false)
            val recognizedText = try {
                recognize(ocrBitmap)
            } finally {
                if (!ocrBitmap.isRecycled) ocrBitmap.recycle()
            }.orEmpty().take(6000)

            val prompt = """
                $goal

                Bạn là bộ điều phối quyết định của CoC Agent. Chỉ trả về MỘT JSON object,
                không Markdown, theo schema:
                {
                  "decision":"execute_skill|done|ask_user|stop",
                  "skillId":"",
                  "version":0,
                  "reason":"",
                  "verification":"not_applicable|success|failed|uncertain",
                  "verificationEvidence":"",
                  "userQuestion":""
                }

                BẰNG CHỨNG HIỆN TẠI (OCR):
                ${recognizedText.ifBlank { "(Không nhận diện được chữ; không được suy đoán trạng thái.)" }}

                HÀNH ĐỘNG VỪA THỰC HIỆN:
                $previousAction
                KẾT QUẢ XÁC MINH HÀNH ĐỘNG TRƯỚC:
                verification=$previousVerification
                evidence=$previousEvidence

                DANH SÁCH SKILL DUY NHẤT ĐƯỢC PHÉP CHỌN:
                $inventory

                QUY TẮC BẮT BUỘC:
                - Chỉ chọn chính xác id và version có trong danh sách trên.
                - Chỉ chọn Skill nếu điều kiện trước của nó phù hợp với bằng chứng màn hình.
                - Không tạo tọa độ, tap, swipe, mã, API hoặc lệnh; không được tự mô tả thao tác ngoài Skill.
                - Sau hành động trước, phải xác minh bằng màn hình mới. Nếu không có bằng chứng rõ ràng,
                  đặt verification=uncertain và decision=stop. Nếu thất bại, đặt verification=failed và stop.
                - Nếu hành động trước thành công, đặt verification=success và nêu bằng chứng OCR cụ thể.
                - Nếu chưa cần hành động, chọn done. Nếu thiếu thông tin cần người dùng, chọn ask_user.
                - Không tuyên bố hoàn thành nếu không có bằng chứng quan sát được.
                - Một lần chỉ chọn một Skill; tối đa $MAX_SKILL_ACTIONS lần chạy Skill trong phiên. Ưu tiên an toàn, dừng khi không chắc chắn.
                - Với decision khác execute_skill, để skillId rỗng và version=0.
            """.trimIndent()

            val raw = try {
                GeminiClient.generate(apiKey, prompt, if (shareScreenshot) screenshot else null)
            } finally {
                screenshot.recycle()
            }
            val plan = parsePlan(raw)
            val decision = plan.optString("decision")
            val reason = plan.optString("reason").take(600)
            if (previousAction != "Chưa có hành động nào." &&
                (plan.optString("verification") != "success" || plan.optString("verificationEvidence").isBlank())
            ) {
                return "Đã dừng sau hành động trước vì chưa xác minh được kết quả. $reason"
            }
            when (decision) {
                "done" -> return "Agent kết thúc: $reason\n\nNhật ký: ${executionNotes.joinToString(" → ").ifBlank { "Không có thao tác." }}"
                "ask_user" -> return "Agent cần bạn xác nhận thêm: ${plan.optString("userQuestion").ifBlank { reason }}"
                "stop" -> return "Agent chủ động dừng: $reason"
                "execute_skill" -> {
                    val skillId = plan.optString("skillId")
                    val version = plan.optInt("version", 0)
                    val skill = registry.find(skillId, version)
                        ?: error("AI chọn Skill không tồn tại đúng phiên bản: $skillId@$version")
                    check(skill.status == SkillLifecycle.VERIFIED) {
                        "Từ chối thực thi $skillId@$version vì Skill chưa VERIFIED."
                    }
                    val script = registry.findVerifiedScript(skillId, version)
                        ?: error("Không lấy được CASL script đã xác minh của $skillId@$version.")
                    check(script.optString("kind") == "skill" &&
                        script.optString("id") == skillId && script.optInt("version") == version
                    ) { "CASL script không khớp ID/version đã pin." }
                    check(totalSkillRuns < MAX_SKILL_ACTIONS) { "Agent đã đạt giới hạn $MAX_SKILL_ACTIONS lần chạy Skill; dừng để giữ an toàn." }
                    val runCount = (skillRuns[skillId] ?: 0) + 1
                    check(runCount <= 2) { "Agent chặn lặp Skill $skillId quá hai lần trong một phiên." }
                    skillRuns[skillId] = runCount

                    onStatus("Vòng $cycle/$MAX_CYCLES · Đang chạy Skill đã xác minh: ${skill.name}")
                    val runtime = CaslScriptRuntime(
                        apiRegistry = createApiRegistry(context),
                        skillScriptProvider = registry::findVerifiedScript,
                        maxSteps = MAX_SKILL_STEPS,
                        maxDepth = 10,
                        maxRepeat = 10,
                        maxTotalWaitMs = 45_000L
                    )
                    val result = withTimeout(MAX_SKILL_RUN_MS) {
                        runtime.execute(script, JSONObject(), userApproved = true)
                    }
                    previousAction = "Đã chạy Skill $skillId@$version; số bước CASL=${result.executedSteps}; outputs=${result.outputs}"
                    previousVerification = "pending"
                    previousEvidence = ""
                    executionNotes.add("$skillId@$version (${result.executedSteps} bước)")
                    onStatus("Skill đã chạy · Đang chụp lại màn hình để xác minh…")
                    delay(1_600L)
                }
                else -> error("Gemini trả decision không hợp lệ; Agent đã dừng để tránh thao tác ngoài kế hoạch.")
            }
        }
        return "Agent đã chạm giới hạn $MAX_CYCLES vòng và tự dừng. Nhật ký: ${executionNotes.joinToString(" → ")}"
    }

    private fun parsePlan(raw: String): JSONObject {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "Gemini không trả về JSON kế hoạch hợp lệ." }
        val plan = JSONObject(raw.substring(start, end + 1))
        require(plan.optString("decision") in setOf("execute_skill", "done", "ask_user", "stop")) {
            "Decision của Agent không hợp lệ."
        }
        require(plan.optString("verification", "not_applicable") in
            setOf("not_applicable", "success", "failed", "uncertain")) { "Trạng thái xác minh không hợp lệ." }
        return plan
    }

    private suspend fun captureScreen(): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            val accepted = CoCAccessibilityService.captureScreen { bitmap ->
                if (continuation.isActive) continuation.resume(bitmap)
                else bitmap?.recycle()
            }
            if (!accepted && continuation.isActive) continuation.resume(null)
        }

    private suspend fun recognize(bitmap: Bitmap): String? =
        suspendCancellableCoroutine { continuation ->
            LocalOcrProcessor.recognize(bitmap) { text ->
                if (continuation.isActive) continuation.resume(text)
            }
        }

    private fun createApiRegistry(context: Context): CaslApiRegistry {
        val registry = CaslApiRegistry()
        val skills = AgentSkillRegistry(context)
        val store = CadssStore(context)
        registry.register("action.tap") { args, executionContext ->
            val id = executionContext.optString("skillId")
            val version = executionContext.optInt("skillVersion", 0)
            val request = SkillActionRequest(
                skillId = id,
                capability = SkillActionCapability.TAP,
                normalizedX = args.optInt("x", -1),
                normalizedY = args.optInt("y", -1),
                durationMs = if (args.has("durationMs")) args.optLong("durationMs") else null,
                skillVersion = version
            )
            when (val result = ActionExecutor.executeAwait(context, skills, request, userApproved = true)) {
                is ActionDispatchResult.Accepted -> JSONObject().put("accepted", true).put("skillId", result.skillId)
                is ActionDispatchResult.Rejected -> error(result.reason)
            }
        }
        registry.register("action.swipe") { args, executionContext ->
            val id = executionContext.optString("skillId")
            val version = executionContext.optInt("skillVersion", 0)
            val request = SkillActionRequest(
                skillId = id,
                capability = SkillActionCapability.SWIPE,
                normalizedX = args.optInt("x", -1),
                normalizedY = args.optInt("y", -1),
                endNormalizedX = args.optInt("endX", -1),
                endNormalizedY = args.optInt("endY", -1),
                durationMs = args.optLong("durationMs", -1L),
                skillVersion = version
            )
            when (val result = ActionExecutor.executeAwait(context, skills, request, userApproved = true)) {
                is ActionDispatchResult.Accepted -> JSONObject().put("accepted", true).put("skillId", result.skillId)
                is ActionDispatchResult.Rejected -> error(result.reason)
            }
        }
        registry.register("action.wait") { args, executionContext ->
            val request = SkillActionRequest(
                skillId = executionContext.optString("skillId"),
                capability = SkillActionCapability.WAIT,
                durationMs = args.optLong("durationMs", -1L),
                skillVersion = executionContext.optInt("skillVersion", 0)
            )
            when (val result = ActionExecutor.executeAwait(context, skills, request, userApproved = true)) {
                is ActionDispatchResult.Accepted -> JSONObject().put("completed", true)
                is ActionDispatchResult.Rejected -> error(result.reason)
            }
        }
        registry.register("memory.read") { args, _ ->
            val collection = args.optString("collection")
            require(collection.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
            val key = args.optString("key")
            val records = store.getRecords(collection).filter { key.isBlank() || it.optString("key") == key }
            JSONObject().put("records", JSONArray(records))
        }
        registry.register("memory.write") { args, _ ->
            val collection = args.optString("collection")
            require(collection.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
            val record = args.optJSONObject("record") ?: error("memory.write cần record object.")
            store.addRecord(collection, JSONObject(record.toString()))
            JSONObject().put("written", true)
        }
        registry.register("memory.append") { args, _ ->
            val collection = args.optString("collection", "workflow_events")
            require(collection.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
            val event = args.optJSONObject("event") ?: error("memory.append cần event object.")
            store.addRecord(collection, JSONObject(event.toString()).put("recordedAt", System.currentTimeMillis()))
            JSONObject().put("appended", true)
        }
        registry.register("system.time") { _, _ -> JSONObject().put("epochMillis", System.currentTimeMillis()) }
        registry.register("vision.feedback") { args, executionContext ->
            val skillId = executionContext.optString("skillId")
            val skillVersion = executionContext.optInt("skillVersion", 0)
            require(skillId.isNotBlank() && skillVersion > 0) {
                "Vision feedback chỉ được ghi từ Skill đã pin version."
            }
            val updated = VisionLearningManager(context).recordOutcome(
                args.optString("templateId"),
                args.optBoolean("success", false)
            ) ?: error("Không tìm thấy VERIFIED Vision template.")
            JSONObject()
                .put("updated", true)
                .put("templateId", args.optString("templateId"))
                .put("status", updated.optString("status"))
                .put("confidence", updated.optDouble("confidence", 0.0))
        }
        registry.register("vision.match") { args, _ ->
            val match = VisionLearningManager(context).findNearest(args.optString("hash"))
            if (match == null) JSONObject().put("matched", false).put("distance", -1)
            else JSONObject().put("matched", true)
                .put("templateId", match.first.optString("id"))
                .put("label", match.first.optString("label"))
                .put("distance", match.second)
        }
        return registry
    }
}
