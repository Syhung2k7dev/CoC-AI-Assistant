package dev.syhung.cocassistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

import dev.syhung.cocassistant.NavigationRouter
import dev.syhung.cocassistant.WorldStateManager

/**
 * Bounded observe-decide-act-verify loop.
 * Gemini can select a pinned VERIFIED Skill, but cannot emit coordinates or native gestures.
 * All device actions remain behind ActionExecutor and the user's explicit start action.
 */
object CentralAgentLoop {
    private const val MAX_SESSION_RUN_MS = 30 * 60_000L
    private const val MAX_SKILL_RUN_MS = 90_000L
    private const val MAX_SKILL_STEPS = 120
    private const val GAME_PACKAGE = "com.supercell.clashofclans"

    // Add this property to CentralAgentLoop
    var currentSceneProvider: () -> String = { "UNKNOWN" }

    suspend fun run(
        context: Context,
        apiKey: String,
        goal: String,
        registry: AgentSkillRegistry,
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
        
        if (verifiedSkills.isEmpty()) {
            onStatus("Chưa có Skill VERIFIED. Bắt đầu nghiên cứu có giới hạn để tạo bản nháp...")
            return when (val outcome = AutonomousSkillResearchLoop.researchNewGoal(
                context = context,
                apiKey = apiKey,
                goal = goal,
                registry = registry,
                onStatus = onStatus
            )) {
                is SkillDraftOutcome.Created -> {
                    val dependencies = outcome.missingDependencies.joinToString(", ").ifBlank { "không có" }
                    "Đã nghiên cứu và lưu Skill nháp '${outcome.skill.name}' (v${outcome.skill.version}). " +
                        "Skill chưa được chạy tự động vì cần được xem xét, kiểm thử và xác minh trước. " +
                        "Phụ thuộc chưa có: $dependencies."
                }
                is SkillDraftOutcome.Failed -> "Nghiên cứu chưa tạo được Skill: ${outcome.reason}"
            }
        }
        onStatus("Đã tải ${verifiedSkills.size} Skill đã xác minh. Bắt đầu phiên làm việc.")
        val inventory = verifiedSkills.joinToString("\n") {
            "id=${it.id}; version=${it.version}; name=${it.name}; description=${it.description}; " +
                "capabilities=${it.capabilities}; inputs=${it.inputs}; outputs=${it.outputs}; " +
                "instructions=${it.instructions.take(900)}"
        }

        val visionLearning = VisionLearningManager(context)
        visionLearning.migrateLegacyTemplates(CadssStore(context).getRecords("template_memory"))
        val driftDetector = VisualDriftDetector(context)
        
        // Cập nhật trạng thái
        SystemStatusRegistry.isVisionReady = true
        SystemStatusRegistry.isGeminiReady = apiKey.isNotBlank()
        
        // Sử dụng Orchestrator thay vì Researcher cũ
        val recoveryOrchestrator = dev.syhung.cocassistant.recovery.RecoveryOrchestrator(context, apiKey)

        
        val wsScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val worldState = WorldStateManager(context, wsScope)
        
        val sessionStartedAt = SystemClock.elapsedRealtime()
        var cycle = 0
        var previousAction = "Chưa có hành động nào."
        var previousVerification = "not_applicable"
        var previousEvidence = ""
        var totalSkillRuns = 0
        var currentScene = GameScene.UNKNOWN
        currentSceneProvider = { currentScene.name }
        val executionNotes = mutableListOf<String>()

        while (true) {
            cycle++
            val sessionExpired = SystemClock.elapsedRealtime() - sessionStartedAt >= MAX_SESSION_RUN_MS
            if (sessionExpired && previousAction == "Chưa có hành động nào.") {
                return "Agent đã hết ngân sách thời gian phiên (30 phút) trước khi có hành động. Nhật ký: Không có thao tác."
            }
            onStatus("Vòng $cycle · Đang quan sát màn hình CoC…")
            val screenshot = captureScreen()
                ?: error("Không chụp được màn hình. Agent đã dừng, không thực hiện thao tác.")
            val ocrBitmap = screenshot.copy(Bitmap.Config.ARGB_8888, false)
            val recognizedText = try {
                recognize(ocrBitmap)
            } finally {
                if (!ocrBitmap.isRecycled) ocrBitmap.recycle()
            }.orEmpty().take(6000)

            // ── Scene Detection ──────────────────────────────────────────────
            val sceneResult = SceneDetector.detect(recognizedText, screenshot)
            currentScene = sceneResult.scene
            onStatus("Vòng $cycle · Scene: ${currentScene.displayName} (${(sceneResult.confidence * 100).toInt()}%)")

            val suggestedLabel = sceneResult.scene.displayName.ifBlank {
                recognizedText.lineSequence().map { it.trim() }
                    .firstOrNull { it.isNotBlank() }?.take(100).orEmpty()
            }
            val visionObservation = withContext(Dispatchers.IO) { runCatching {
                visionLearning.observeForAgent(
                    bitmap = screenshot,
                    suggestedLabel = suggestedLabel,
                    metadata = JSONObject()
                        .put("source", "central_agent")
                        .put("cycle", cycle)
                        .put("scene", currentScene.name)
                        .put("ocrLength", recognizedText.length)
                )
            }.getOrNull() }
            val visionConfidence = when (val obs = visionObservation) {
                is VisionObservationResult.Known -> obs.template.optDouble("confidence", 0.5).toFloat()
                else -> sceneResult.confidence
            }
            driftDetector.record(visionConfidence)
            val driftSummary = driftDetector.summary()

            // ── Self-Healing Logic (Recovery) ────────────────────────────────
            val driftState = driftDetector.evaluate()
            if (driftState is DriftState.DRIFT) {
                onStatus("Cảnh báo: Phát hiện Drift (${driftState.avgConfidence}). Đang cố gắng phục hồi (Tier 1-3)...")
                val result = recoveryOrchestrator.handle(screenshot, currentScene.name)
                
                when (result) {
                    is dev.syhung.cocassistant.recovery.RecoveryResult.Handled -> {
                        onStatus("Phục hồi thành công (Tier 1). Tiếp tục.")
                        driftDetector.reset()
                    }
                    is dev.syhung.cocassistant.recovery.RecoveryResult.Branch -> {
                        onStatus("Phục hồi thành công (Tier 2 - Branching).")
                        driftDetector.reset()
                    }
                    is dev.syhung.cocassistant.recovery.RecoveryResult.Critical -> {
                        onStatus("Phục hồi thất bại (Critical). Kích hoạt tự học (Research Loop)...")
                        AutonomousSkillResearchLoop.researchNewGoal(context, apiKey, goal, registry, onStatus)
                    }
                }
            }

            // ── World Context (Mốc 1) ────────────────────────────────────────
            val worldContext: JSONObject = withContext(Dispatchers.IO) {
                val village = worldState.getVillageResources("home")
                val timers = worldState.getActiveBuildingTimers("home")
                val globalStrategy = worldState.getGlobalContext("strategy", "Chưa có chiến lược hệ thống.")
                val globalSafety = worldState.getGlobalContext("safety_rules", "Cấm chạm nút mua Gem, mua hàng, rời Clan.")
                val recentLogs = worldState.getRecentExecutionLogs(5)
                val logSummary = recentLogs.joinToString("; ") { "${it.optString("skillId")}=>${it.optString("result")}" }
                JSONObject()
                    .put("village", village)
                    .put("activeTimers", timers.optJSONArray("items")?.length() ?: timers.optInt("count", 0))
                    .put("strategy", globalStrategy)
                    .put("safetyRules", globalSafety)
                    .put("recentLogs", logSummary)
            }
            val villageJson = worldContext.optJSONObject("village")
            val villageSummary = if (villageJson != null && villageJson.optBoolean("available", true)) {
                "TH${villageJson.optInt("townHallLevel", 0)} | Vàng ${villageJson.optLong("gold", 0)} | Dầu ${villageJson.optLong("elixir", 0)} | Tím ${villageJson.optLong("darkElixir", 0)} | Gem ${villageJson.optInt("gems", 0)} | Thợ xây ${villageJson.optInt("builderBusy", 0)}/${villageJson.optInt("builderCount", 0)} | Timer đang chạy ${worldContext.optInt("activeTimers", 0)}"
            } else "Chưa có dữ liệu làng."
            val worldContextSummary = """
                CHIẾN THUẬC: ${worldContext.optString("strategy")}
                QUY TẮC AN TOÀN: ${worldContext.optString("safetyRules")}
                LÀNG CHÍNH: $villageSummary
                LỊCH SỬ GẦN ĐÂY: ${worldContext.optString("recentLogs")}
            """.trimIndent()

            val visionMemorySummary = when (val observation = visionObservation) {
                is VisionObservationResult.Known ->
                    "KNOWN_TEMPLATE: ${observation.template.optString("label")}; " +
                        "distance=${observation.distance}/64; " +
                        "confidence=${observation.template.optDouble("confidence", 0.5)}"
                is VisionObservationResult.NeedsReview ->
                    "CANDIDATE_NOT_YET_TRUSTED: sightings=${observation.candidate.optInt("sightings", 1)}/3; " +
                        "distance=${observation.distance}; labelHint=${observation.candidate.optString("suggestedLabel")}"
                null -> "LOCAL_VISION_MEMORY_UNAVAILABLE: use current screenshot and OCR only; do not invent a match."
            }

            val prompt = """
                $goal

                Bạn là bộ điều phối quyết định của CoC Agent. Chỉ trả về MỘT JSON object,
                không Markdown, theo schema:
                {
                  "decision":"execute_skill|done|ask_user|stop",
                  "skillId":"",
                  "version":0,
                  "targetScene":"HOME_VILLAGE|BUILDER_BASE|CLAN_CAPITAL|UNKNOWN",
                  "reason":"",
                  "verification":"not_applicable|success|failed|uncertain",
                  "verificationEvidence":"",
                  "preconditionsSatisfied":false,
                  "preconditionEvidence":"",
                  "userQuestion":""
                }

                SCENE HIỆN TẠI (do SceneDetector phân tích local):
                scene=${currentScene.name} | displayName="${currentScene.displayName}"
                confidence=${sceneResult.confidence} | isPlayableVillage=${currentScene.isPlayableVillage}
                sceneDetails=${sceneResult.details}
                detectedKeywords=${sceneResult.detectedKeywords.joinToString(",")}

                NGỮ CẢNH THẾ GIỚI (từ World State):
                $worldContextSummary

                BẰNG CHỨNG HIỆN TẠI (OCR):
                ${recognizedText.ifBlank { "(Không nhận diện được chữ; không được suy đoán trạng thái.)" }}

                VISION MEMORY CỤC BỘ DO AGENT QUẢN LÝ (pHash/OpenCV):
                $visionMemorySummary

                TRẠNG THÁI NHẬN DIỆN HÌNH ẢNH (Drift Detector):
                $driftSummary
                Nếu có VISUAL_DRIFT, ưu tiên dừng hoặc yêu cầu tái nghiên cứu trước khi hành động.

                Candidate mới chỉ được tin cậy sau ba lần quan sát cục bộ tương tự; không suy diễn candidate thành template đã xác minh.

                HÀNH ĐỘNG VỪA THỰC HIỆN:
                $previousAction
                KẾT QUẢ XÁC MINH HÀNH ĐỘNG TRƯỚC:
                verification=$previousVerification
                evidence=$previousEvidence

                DANH SÁCH SKILL DUY NHẤT ĐƯỢC PHÉP CHỌN:
                $inventory

                QUY TẮC BẮT BUỘC:
                - Chỉ chọn chính xác id và version có trong danh sách trên.
                - Chỉ chọn Skill nếu scene hiện tại phù hợp với Skill. Nếu scene=UNKNOWN hoặc LOADING_SCREEN, chọn stop.
                - Chỉ chọn Skill nếu điều kiện trước phù hợp với OCR. preconditionEvidence phải là trích dẫn liên tục, nguyên văn từ OCR hiện tại; không diễn giải lại.
                - Khi xác minh hành động trước, verificationEvidence cũng phải là trích dẫn liên tục, nguyên văn từ OCR mới; không có trích dẫn khớp thì chọn uncertain/stop.
                - Không tạo tọa độ, tap, swipe, mã, API hoặc lệnh; không được tự mô tả thao tác ngoài Skill.
                - Sau hành động trước, phải xác minh bằng màn hình mới. Nếu không có bằng chứng rõ ràng,
                  đặt verification=uncertain và decision=stop. Nếu thất bại, đặt verification=failed và stop.
                - Nếu hành động trước thành công, đặt verification=success và nêu bằng chứng OCR cụ thể.
                - Nếu chưa cần hành động, chọn done. Nếu thiếu thông tin cần người dùng, chọn ask_user.
                - Không tuyên bố hoàn thành nếu không có bằng chứng quan sát được.
                - Không có giới hạn cứng về tổng số lần chạy Skill. Chỉ chọn một Skill mỗi vòng, tiếp tục cho đến khi hoàn tất, cần người dùng, không chắc chắn hoặc hết ngân sách phiên 30 phút.
                - Ngân sách phiên đã hết: $sessionExpired. Khi true, chỉ xác minh hành động vừa chạy rồi chọn done/ask_user/stop; tuyệt đối không bắt đầu Skill mới.
                - Với decision khác execute_skill, để skillId rỗng và version=0.
            """.trimIndent()

            val raw = try {
                GeminiClient.generate(apiKey, prompt, screenshot)
            } finally {
                screenshot.recycle()
            }
            val plan = parsePlan(raw)
            val decision = plan.optString("decision")
            val reason = plan.optString("reason").take(600)
            if (previousAction != "Chưa có hành động nào.") {
                val verificationQuote = plan.optString("verificationEvidence").trim()
                if (plan.optString("verification") != "success" ||
                    !containsOcrQuote(recognizedText, verificationQuote)
                ) {
                    return "Đã dừng sau hành động trước vì không có bằng chứng OCR khớp để xác minh. $reason"
                }
                previousVerification = "success"
                previousEvidence = verificationQuote.take(800)
            }
            when (decision) {
                "done" -> return "Agent kết thúc: $reason\nTổng số lần chạy Skill: $totalSkillRuns\n\nNhật ký: ${executionNotes.joinToString(" → ").ifBlank { "Không có thao tác." }}"
                "ask_user" -> return "Agent cần bạn xác nhận thêm: ${plan.optString("userQuestion").ifBlank { reason }}"
                "stop" -> return "Agent chủ động dừng: $reason"
                "execute_skill" -> {
                    val preconditionQuote = plan.optString("preconditionEvidence").trim()
                    check(plan.optBoolean("preconditionsSatisfied", false) &&
                        containsOcrQuote(recognizedText, preconditionQuote)
                    ) { "Điều kiện trước không có trích dẫn OCR khớp; từ chối chạy Skill." }
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
                    if (sessionExpired ||
                        SystemClock.elapsedRealtime() - sessionStartedAt >= MAX_SESSION_RUN_MS
                    ) {
                        return "Đã hết ngân sách thời gian phiên. Agent đã xác minh hành động trước và không khởi chạy Skill mới. Nhật ký: ${executionNotes.joinToString(" → ")}"
                    }

                    // ── Điều hướng đến cảnh mục tiêu trước khi chạy Skill ─────────────
                    val targetSceneName = plan.optString("targetScene", currentScene.name)
                    val targetScene = GameScene.valueOf(targetSceneName)
                    if (targetScene != currentScene) {
                        val navAction = NavigationRouter.route(currentScene, targetScene, sceneResult)
                        when (navAction) {
                            is NavigationAction.TapCoordinate -> {
                                val displayMetrics = context.resources.displayMetrics
                                val (px, py) = HumanizedGestureBuilder.normalizedToPixel(
                                    navAction.normalizedX,
                                    navAction.normalizedY,
                                    displayMetrics.widthPixels,
                                    displayMetrics.heightPixels
                                )
                                val step = AutomationStep.Tap(px.toFloat(), py.toFloat(), 150L)
                                if (!CoCAccessibilityService.performSingleStep(step)) {
                                    return "Không thực hiện được thao tác điều hướng (tap tại ${navAction.normalizedX},${navAction.normalizedY}): ${navAction.reason}"
                                }
                                onStatus("Vòng $cycle · Điều hướng: ${navAction.reason}")
                                delay(1_200L)
                                // Sau khi điều hướng, quét lại màn hình để cập nhật scene
                                continue
                            }
                            is NavigationAction.WaitForScene -> {
                                onStatus("Vòng $cycle · Đang chờ chuyển cảnh: ${navAction.expectedScene.displayName}")
                                delay(navAction.timeoutMs)
                                continue
                            }
                            is NavigationAction.RequestUserAction -> {
                                return "Cần can thiệp người dùng: ${navAction.reason}"
                            }
                            NavigationAction.AlreadyAtTarget -> { /* tiếp tục chạy Skill */ }
                            else -> { /* SendKey hoặc các hành động khác không xử lý tại đây */ }
                        }
                    }

                    onStatus("Vòng $cycle · Đang chạy Skill đã xác minh: ${skill.name}")
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
                    totalSkillRuns++
                    executionNotes.add("$skillId@$version (${result.executedSteps} bước)")
                    onStatus("Skill đã chạy · Đang chụp lại màn hình để xác minh…")
                    delay(1_600L)
                }
                else -> error("Gemini trả decision không hợp lệ; Agent đã dừng để tránh thao tác ngoài kế hoạch.")
            }
        }
    }

    private fun containsOcrQuote(ocr: String, quote: String): Boolean {
        if (quote.length < 3) return false
        fun normalize(value: String) = value.replace(Regex("\\s+"), " ").trim()
        return normalize(ocr).contains(normalize(quote), ignoreCase = true)
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
            when (val result = ActionExecutor.executeAwait(context, skills, request, fromState = currentSceneProvider(), userApproved = true)) {
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
            when (val result = ActionExecutor.executeAwait(context, skills, request, fromState = currentSceneProvider(), userApproved = true)) {
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
            when (val result = ActionExecutor.executeAwait(context, skills, request, fromState = currentSceneProvider(), userApproved = true)) {
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
