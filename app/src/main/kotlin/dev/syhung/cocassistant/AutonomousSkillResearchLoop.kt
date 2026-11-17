package dev.syhung.cocassistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Autonomous observe -> reason -> act -> observe loop dedicated to researching a Skill.
 * Every cycle sends a fresh CoC screenshot to Gemini and persists evidence before continuing.
 */
object AutonomousSkillResearchLoop {
    private const val GAME_PACKAGE = "com.supercell.clashofclans"
    private const val MAX_CYCLES = 120
    private const val MAX_SESSION_MS = 30 * 60_000L
    private const val MAX_ACTIONS = 200
    private const val MAX_IMAGE_CROPS_PER_CYCLE = 12

    data class Report(
        val total: Int,
        val succeeded: Int,
        val failures: List<Pair<String, String>>
    )

    suspend fun researchExisting(
        context: Context,
        apiKey: String,
        skillId: String,
        registry: AgentSkillRegistry,
        onStatus: (String) -> Unit = {}
    ): SkillDraftOutcome {
        val current = registry.find(skillId)
            ?: return SkillDraftOutcome.Failed("Không tìm thấy Skill cần nghiên cứu.")
        if (current.status != SkillLifecycle.RESEARCH_REQUIRED) {
            return SkillDraftOutcome.Failed("Chỉ Skill đang ở trạng thái Chưa nghiên cứu mới được đưa vào vòng nghiên cứu.")
        }
        if (!registry.setLifecycle(current.id, SkillLifecycle.RESEARCHING, current.version)) {
            return SkillDraftOutcome.Failed("Không thể khóa Skill cho phiên nghiên cứu.")
        }
        return try {
            runSession(
                context, apiKey, current.description, current, registry, onStatus
            )
        } catch (cancelled: CancellationException) {
            registry.setLifecycle(current.id, SkillLifecycle.RESEARCH_REQUIRED, current.version)
            throw cancelled
        } catch (error: Exception) {
            registry.setLifecycle(current.id, SkillLifecycle.RESEARCH_REQUIRED, current.version)
            SkillDraftOutcome.Failed(error.message ?: "Phiên nghiên cứu thất bại.")
        }
    }

    suspend fun researchNewGoal(
        context: Context,
        apiKey: String,
        goal: String,
        registry: AgentSkillRegistry,
        onStatus: (String) -> Unit = {}
    ): SkillDraftOutcome {
        if (goal.isBlank()) return SkillDraftOutcome.Failed("Mục tiêu nghiên cứu không được để trống.")
        return try {
            runSession(context, apiKey, goal, null, registry, onStatus)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SkillDraftOutcome.Failed(error.message ?: "Phiên nghiên cứu thất bại.")
        }
    }

    suspend fun researchPendingBatch(
        context: Context,
        apiKey: String,
        registry: AgentSkillRegistry,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): Report {
        if (apiKey.isBlank()) return Report(0, 0, listOf("API Key" to "Chưa có Gemini API Key."))
        val pending = registry.all().groupBy { it.id }.values
            .mapNotNull { versions -> versions.maxByOrNull { it.version } }
            .filter { it.status == SkillLifecycle.RESEARCH_REQUIRED }
            .sortedWith(compareBy<AgentSkill> { it.category }.thenBy { it.name })
        var succeeded = 0
        val failures = mutableListOf<Pair<String, String>>()
        pending.forEachIndexed { index, skill ->
            val result = researchExisting(context, apiKey, skill.id, registry) { stage ->
                onProgress(index, pending.size, "${skill.name}: $stage")
            }
            when (result) {
                is SkillDraftOutcome.Created -> succeeded++
                is SkillDraftOutcome.Failed -> failures += skill.name to result.reason
            }
            onProgress(index + 1, pending.size, skill.name)
        }
        return Report(pending.size, succeeded, failures)
    }

    private suspend fun runSession(
        context: Context,
        apiKey: String,
        goal: String,
        target: AgentSkill?,
        registry: AgentSkillRegistry,
        onStatus: (String) -> Unit
    ): SkillDraftOutcome {
        require(apiKey.isNotBlank()) { "Chưa có Gemini API Key." }
        require(CoCAccessibilityService.isConnected()) {
            "Chưa kết nối Accessibility Service. Hãy bật dịch vụ trong Cài đặt Android."
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(GAME_PACKAGE)
            ?: error("Không tìm thấy Clash of Clans trên thiết bị.")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        delay(5_000L)

        val sessionId = UUID.randomUUID().toString()
        val db = SkillResearchDatabase(context)
        val cadss = CadssStore(context)
        db.startSession(sessionId, target?.id, goal)
        val started = SystemClock.elapsedRealtime()
        var cycle = 0
        var actionCount = 0
        var previousAction = JSONObject().put("type", "none")
        var previousActionResult = JSONObject().put("status", "not_started")
        var previousObservationId: String? = null
        var latestManifest: JSONObject? = null
        var latestCasl: JSONObject? = null
        var lastAnalysis = ""

        try {
            while (cycle < MAX_CYCLES &&
                SystemClock.elapsedRealtime() - started < MAX_SESSION_MS
            ) {
                cycle++
                onStatus("Phiên ${sessionId.take(8)} · vòng $cycle · đang chụp màn hình CoC")
                val screenshot = captureScreen()
                    ?: error("Không chụp được màn hình CoC. Phiên nghiên cứu đã dừng.")
                val observationId = UUID.randomUUID().toString()
                val phash = withContext(Dispatchers.Default) { VisionProcessor.perceptualHash(screenshot) }
                val ocr = try { recognize(screenshot).orEmpty().take(12_000) }
                    catch (_: Exception) { "" }
                val screenAsset = withContext(Dispatchers.IO) {
                    VisionMemory.save(context, screenshot, phash).also { asset ->
                        val mediaRecord = JSONObject(asset.toString())
                            .put("sessionId", sessionId)
                            .put("observationId", observationId)
                            .put("kind", "screenshot")
                        cadss.addRecord("research_media", mediaRecord)
                        db.recordMedia(sessionId, observationId, mediaRecord, "screenshot")
                    }
                }
                val prompt = buildResearchPrompt(
                    goal, target, cycle, ocr, previousAction, previousActionResult,
                    latestManifest, latestCasl, db, sessionId
                )
                val raw = try {
                    withTimeout(60_000L) { GeminiClient.generate(apiKey, prompt, screenshot) }
                } finally {
                    screenshot.recycle()
                }
                val response = parseJson(raw)
                if (previousObservationId != null) {
                    val verification = response.optJSONObject("previousActionVerification") ?: JSONObject()
                    previousActionResult = JSONObject(previousActionResult.toString())
                        .put("verification", verification.optString("status", "uncertain"))
                        .put("verificationEvidence", verification.optString("evidence").take(1_000))
                        .put("verificationReason", verification.optString("reason").take(500))
                    db.updateObservationActionResult(previousObservationId!!, previousActionResult)
                }
                val previousActionType = previousAction.optString("type")
                val previousVerificationStatus = previousActionResult.optString("verification")
                lastAnalysis = response.optString("analysis").take(5_000)
                val screenKey = response.optString("screenKey", "unknown_screen").take(100)
                val cropAssets = saveRequestedCrops(
                    context, response.optJSONArray("cropRects"), screenAsset.optString("id"),
                    sessionId, observationId, cadss, db
                )
                response.optJSONObject("manifest")?.let { latestManifest = JSONObject(it.toString()) }
                response.optJSONObject("casl")?.let { latestCasl = JSONObject(it.toString()) }
                response.optJSONObject("layout")?.let {
                    db.upsertLayout(sessionId, observationId, it)
                }
                response.optJSONArray("procedure")?.let {
                    db.replaceProcedure(sessionId, observationId, it)
                }
                response.optJSONArray("strategies")?.let { items ->
                    for (i in 0 until items.length()) items.optJSONObject(i)?.let {
                        db.upsertStrategy(sessionId, observationId, it)
                    }
                }
                response.optJSONArray("calculations")?.let { items ->
                    for (i in 0 until items.length()) items.optJSONObject(i)?.let {
                        db.upsertCalculation(sessionId, observationId, it)
                    }
                }
                response.optJSONArray("datasets")?.let { items ->
                    for (i in 0 until items.length()) items.optJSONObject(i)?.let {
                        db.upsertDataset(sessionId, it)
                    }
                }

                val action = response.optJSONObject("nextAction") ?: JSONObject().put("type", "stop")
                val actionType = action.optString("type", "stop")
                val executedAction = JSONObject(action.toString())
                val executedResult = JSONObject()
                when (actionType) {
                    "tap" -> {
                        require(CoCAccessibilityService.isGameForeground(GAME_PACKAGE)) {
                            "CoC không còn ở foreground; từ chối thao tác ngoài game."
                        }
                        actionCount++
                        require(actionCount <= MAX_ACTIONS) { "Đã đạt giới hạn an toàn $MAX_ACTIONS thao tác trong một phiên." }
                        val x = action.optInt("x", -1)
                        val y = action.optInt("y", -1)
                        require(x in 0..1000 && y in 0..1000) { "Tọa độ tap phải được chuẩn hóa trong 0..1000." }
                        val metrics = context.resources.displayMetrics
                        val step = AutomationStep.Tap(
                            (metrics.widthPixels * x / 1000f).coerceIn(0f, metrics.widthPixels.toFloat()),
                            (metrics.heightPixels * y / 1000f).coerceIn(0f, metrics.heightPixels.toFloat()),
                            action.optLong("durationMs", 100L).coerceIn(50L, 1_500L)
                        )
                        val ok = CoCAccessibilityService.performSingleStep(step)
                        executedResult.put("status", if (ok) "dispatched" else "rejected")
                        require(ok) { "Android từ chối thao tác tap." }
                        delay(1_200L)
                    }
                    "swipe" -> {
                        require(CoCAccessibilityService.isGameForeground(GAME_PACKAGE)) {
                            "CoC không còn ở foreground; từ chối thao tác ngoài game."
                        }
                        actionCount++
                        require(actionCount <= MAX_ACTIONS) { "Đã đạt giới hạn an toàn $MAX_ACTIONS thao tác trong một phiên." }
                        val x = action.optInt("x", -1); val y = action.optInt("y", -1)
                        val endX = action.optInt("endX", -1); val endY = action.optInt("endY", -1)
                        require(listOf(x, y, endX, endY).all { it in 0..1000 }) {
                            "Tọa độ swipe phải được chuẩn hóa trong 0..1000."
                        }
                        val metrics = context.resources.displayMetrics
                        val step = AutomationStep.Swipe(
                            metrics.widthPixels * x / 1000f, metrics.heightPixels * y / 1000f,
                            metrics.widthPixels * endX / 1000f, metrics.heightPixels * endY / 1000f,
                            action.optLong("durationMs", 350L).coerceIn(100L, 2_000L)
                        )
                        val ok = CoCAccessibilityService.performSingleStep(step)
                        previousActionResult = JSONObject().put("status", if (ok) "dispatched" else "rejected")
                        require(ok) { "Android từ chối thao tác swipe." }
                        delay(1_200L)
                    }
                    "wait" -> {
                        val duration = action.optLong("durationMs", 1_000L).coerceIn(250L, 10_000L)
                        delay(duration)
                        executedResult.put("status", "waited").put("durationMs", duration)
                    }
                    "observe" -> {
                        delay(500L)
                        executedResult.put("status", "observed_without_gesture")
                    }
                    "done" -> executedResult.put("status", "research_complete_requested")
                    "stop" -> executedResult.put("status", "stopped").put("reason", action.optString("reason"))
                    "ask_user" -> executedResult.put("status", "needs_user").put("question", action.optString("question"))
                    else -> error("AI trả về loại hành động không hợp lệ: $actionType")
                }

                db.recordObservation(
                    observationId, sessionId, cycle, screenKey,
                    screenAsset.optString("original"), cropAssets,
                    phash, ocr, lastAnalysis, action, previousActionResult
                )
                db.updateSession(sessionId, target?.id ?: latestManifest?.optString("id"), "RUNNING", cycle, lastAnalysis)
                onStatus("Vòng $cycle · ${actionType.uppercase()} · đã lưu ảnh, layout, procedure và dữ liệu")
                if (actionType == "stop" || actionType == "ask_user") {
                    val reason = if (actionType == "ask_user") action.optString("question") else action.optString("reason")
                    db.updateSession(sessionId, target?.id ?: latestManifest?.optString("id"), "STOPPED", cycle, reason)
                    return SkillDraftOutcome.Failed(reason.ifBlank { "AI đã dừng phiên nghiên cứu." })
                }

                if (actionType == "done") {
                    if (previousObservationId != null && previousActionType in setOf("tap", "swipe")) {
                        require(previousVerificationStatus == "success") {
                            "Chưa có bằng chứng xác minh thành công cho thao tác cuối; không thể kết thúc nghiên cứu."
                        }
                    }
                    require(response.optBoolean("researchComplete", false)) {
                        "AI đề nghị kết thúc nhưng chưa xác nhận researchComplete."
                    }
                    val manifest = latestManifest ?: error("Nghiên cứu chưa tạo Skill manifest.")
                    val casl = latestCasl ?: error("Nghiên cứu chưa tạo CASL script.")
                    val skill = buildResearchedSkill(manifest, casl, target, registry)
                    CaslScriptRuntime(CaslApiRegistry(), { _, _ -> null }).validateProgramDocument(casl)
                    db.saveSkillDocument(sessionId, skill.toJson(), casl)
                    require(registry.saveResearched(skill)) {
                        "Không thể lưu phiên bản Skill đã nghiên cứu."
                    }
                    target?.let { registry.setLifecycle(it.id, SkillLifecycle.RESEARCH_REQUIRED, it.version) }
                    db.updateSession(sessionId, skill.id, "COMPLETED", cycle, "Research complete: ${skill.name}")
                    return SkillDraftOutcome.Created(skill, skill.dependencies.filterNot { dep ->
                        registry.all().any { it.id == dep }
                    })
                }
            }
            error(
                if (cycle >= MAX_CYCLES) "Đã đạt giới hạn $MAX_CYCLES vòng quan sát; phiên được lưu để tiếp tục sau."
                else "Đã hết ngân sách 30 phút; phiên được lưu để tiếp tục sau."
            )
        } catch (cancelled: CancellationException) {
            db.updateSession(sessionId, target?.id, "STOPPED", cycle, "Người dùng đã dừng phiên.")
            throw cancelled
        } catch (error: Exception) {
            db.updateSession(sessionId, target?.id ?: latestManifest?.optString("id"), "FAILED", cycle,
                (error.message ?: "Lỗi nghiên cứu").take(1_000))
            throw error
        } finally {
            db.close()
        }
    }

    private fun buildResearchPrompt(
        goal: String,
        target: AgentSkill?,
        cycle: Int,
        ocr: String,
        previousAction: JSONObject,
        previousResult: JSONObject,
        manifest: JSONObject?,
        casl: JSONObject?,
        db: SkillResearchDatabase,
        sessionId: String
    ): String {
        val databaseSnapshot = db.exportJson().toString().take(60_000)
        return """
            You are the autonomous CoC Skill Research Agent. Research goal: $goal
            Target Skill: ${target?.id ?: "(discover a new Skill)"}; current version: ${target?.version ?: 0}
            Session: $sessionId; cycle: $cycle
            Previous action: $previousAction
            Previous action result: $previousResult
            Current OCR evidence (may be empty; never invent text):
            $ocr
            FULL PERSISTED RESEARCH DATABASE (truncated only if above 60k chars):
            $databaseSnapshot
            Current evolving manifest: ${manifest?.toString() ?: "not created"}
            Current evolving CASL: ${casl?.toString() ?: "not created"}

            Analyze the attached CURRENT screenshot. This is a live observation, not a static image prompt.
            Each response must update knowledge based only on visible evidence and prior persisted observations.
            Continue observing and exploring until the skill is sufficiently understood. Do not stop merely
            because you can describe a plausible workflow. When uncertain, request another observation.
            You control the research loop by returning nextAction; after tap/swipe/wait the app will capture
            a new screenshot and send it in the next cycle.

            Return exactly one JSON object:
            {
              "analysis":"what is visibly known, inferred, and unknown",
              "screenKey":"stable_screen_identifier",
              "previousActionVerification":{"status":"success|failed|uncertain","evidence":"","reason":""},
              "nextAction":{"type":"observe|tap|swipe|wait|done|ask_user|stop",
                "x":0,"y":0,"endX":0,"endY":0,"durationMs":500,"reason":"","question":""},
              "researchComplete":false,
              "manifest":{"id":"","name":"","description":"","category":"","version":1,
                "inputs":[],"outputs":[],"dependencies":[],"capabilities":[],"instructions":"",
                "source":"gemini_skill_research","confidence":0.0,"minAgentVersion":1,
                "maxAgentVersion":null,"dependencyVersions":{}},
              "casl":{"format":"CASL","schemaVersion":1,"kind":"skill","id":"","version":1,"steps":[]},
              "layout":{"screenKey":"stable_screen_identifier","screenName":"","confidence":0.0,
                "components":[{"key":"","label":"","type":"","x":0,"y":0,"width":0,"height":0,
                  "anchor":"","properties":{}}]},
              "cropRects":[{"x":0,"y":0,"width":1000,"height":1000,"label":""}],
              "procedure":[{"id":"","title":"","preconditions":{},"action":{},"postconditions":{},"status":"OBSERVED"}],
              "strategies":[{"key":"","kind":"attack|farming|upgrade|navigation|other","confidence":0.0,"content":{}}],
              "calculations":[{"name":"","formula":"","inputs":{},"result":{}}],
              "datasets":[{"name":"","schema":{},"records":[]}]
            }

            Mandatory rules:
            - First assess previousActionVerification from the newly attached screenshot. If previous action was tap/swipe, never omit this assessment; quote visible evidence or mark uncertain/failed.
            - Choose exactly one nextAction each cycle. Coordinates use normalized 0..1000 screen space.
            - Tap/swipe only visible in-game UI controls. Do not interact with Android system UI, external apps,
              purchases, paid offers, account linking, clan chat, or destructive confirmation dialogs.
              If such a screen appears, choose observe or stop and explain.
            - Never invent a coordinate, label, game state, formula, result, layout, or strategy. Mark unknowns.
            - A component coordinate is a normalized bounding box (x,y,width,height), not a guessed pixel location.
            - cropRects are optional normalized rectangles; only request crops that contain useful visual detail.
            - Record every discovered screen layout, UI component, action, precondition, postcondition and result.
            - Procedure steps must describe a reproducible sequence with conditions and observable outcomes.
            - Store strategies and calculations as structured JSON data. Formula is descriptive math, never executable code.
            - Create or update custom datasets whenever a domain needs its own structured records. Each dataset must have a stable name, JSON schema and records; use the database snapshot to merge rather than discard previous evidence.
            - You may read and revise the entire research database snapshot in every cycle. Return complete updates to layouts, procedure, strategies, calculations and datasets so the app can persist them transactionally.
            - CASL must be declarative JSON only, schemaVersion=1, kind=skill, id/version equal manifest.
              Only supported CASL operations: set, output, call, skill, if, repeat, wait, assert, log, return.
              Calls must use only this API allow-list: ${WorkflowEngine.AVAILABLE_API_NAMES.joinToString(", ")}.
              Do not generate Kotlin, Java, JavaScript, shell, reflection, or arbitrary source code.
            - Do not claim a step is tested or verified unless this session executed it and a later screenshot
              demonstrated its postcondition. Preserve evidence links in procedure records.
            - Keep the same Skill ID during this session. Existing Skill version must be ${(target?.version ?: 0) + 1}.
            - Set researchComplete=true and nextAction.type=done only when manifest, full procedure,
              layout knowledge, relevant strategy/calculation data, and CASL are complete enough to reproduce
              the requested Skill. Otherwise continue with observe/tap/swipe/wait.
            - Never mark the Skill VERIFIED. It will be stored as RESEARCHED and must pass separate tests.
        """.trimIndent()
    }

    private fun buildResearchedSkill(
        sourceManifest: JSONObject,
        casl: JSONObject,
        target: AgentSkill?,
        registry: AgentSkillRegistry
    ): AgentSkill {
        val manifest = JSONObject(sourceManifest.toString())
        val id = target?.id ?: manifest.optString("id")
        val expectedVersion = target?.let { it.version + 1 } ?: 1
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]{1,79}"))) { "Skill ID do AI không hợp lệ." }
        require(manifest.optInt("version", 0) == expectedVersion) { "Skill version không đúng phiên nghiên cứu." }
        require(manifest.optString("id") == id) { "Skill ID đã thay đổi giữa phiên nghiên cứu." }
        require(casl.optString("format") == "CASL" &&
            casl.optInt("schemaVersion", -1) == CaslScriptRuntime.SCHEMA_VERSION &&
            casl.optString("kind") == "skill" &&
            casl.optString("id") == id &&
            casl.optInt("version", 0) == expectedVersion
        ) { "CASL manifest không khớp Skill ID/version." }
        manifest.put("script", casl.toString())
            .put("source", "gemini_skill_research")
            .put("status", SkillLifecycle.RESEARCHED.name)
        val skill = AgentSkill.fromJson(manifest) ?: error("Skill manifest không hợp lệ.")
        require(skill.instructions.length >= 80) { "Skill procedure/instructions còn quá sơ sài." }
        require(skill.scriptJson.isNotBlank()) { "Thiếu CASL script." }
        require(registry.all().none { it.id == skill.id && it.version == skill.version }) {
            "Skill version đã tồn tại; không ghi đè dữ liệu cũ."
        }
        return skill.copy(status = SkillLifecycle.RESEARCHED, source = "gemini_skill_research")
    }

    private suspend fun saveRequestedCrops(
        context: Context,
        rectangles: JSONArray?,
        sourceAssetId: String,
        sessionId: String,
        observationId: String,
        cadss: CadssStore,
        db: SkillResearchDatabase
    ): JSONArray {
        val saved = JSONArray()
        if (rectangles == null) return saved
        val source = cadss.getRecords("research_media").firstOrNull {
            it.optString("id") == sourceAssetId
        } ?: return saved
        val original = VisionMemory.resolve(context, source.optString("original")) ?: return saved
        val bitmap = android.graphics.BitmapFactory.decodeFile(original.absolutePath) ?: return saved
        try {
            for (i in 0 until minOf(rectangles.length(), MAX_IMAGE_CROPS_PER_CYCLE)) {
                val rect = rectangles.optJSONObject(i) ?: continue
                val x = rect.optInt("x", -1); val y = rect.optInt("y", -1)
                val width = rect.optInt("width", -1); val height = rect.optInt("height", -1)
                if (x !in 0..999 || y !in 0..999 || width !in 1..1000 || height !in 1..1000) continue
                val left = (bitmap.width * x / 1000).coerceIn(0, bitmap.width - 1)
                val top = (bitmap.height * y / 1000).coerceIn(0, bitmap.height - 1)
                val right = (left + bitmap.width * width / 1000).coerceIn(left + 1, bitmap.width)
                val bottom = (top + bitmap.height * height / 1000).coerceIn(top + 1, bitmap.height)
                val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                try {
                    val hash = VisionProcessor.perceptualHash(crop)
                    val asset = withContext(Dispatchers.IO) { VisionMemory.save(context, crop, hash) }
                    val mediaRecord = JSONObject(asset.toString())
                        .put("sessionId", sessionId).put("observationId", observationId)
                        .put("kind", "crop").put("label", rect.optString("label"))
                    cadss.addRecord("research_media", mediaRecord)
                    db.recordMedia(sessionId, observationId, mediaRecord, "crop")
                    saved.put(mediaRecord)
                } finally { if (crop !== bitmap && !crop.isRecycled) crop.recycle() }
            }
        } finally { bitmap.recycle() }
        return saved
    }

    private suspend fun captureScreen(): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            val accepted = CoCAccessibilityService.captureScreen { bitmap ->
                if (continuation.isActive) continuation.resume(bitmap) else bitmap?.recycle()
            }
            if (!accepted && continuation.isActive) continuation.resume(null)
        }

    private suspend fun recognize(bitmap: Bitmap): String? =
        suspendCancellableCoroutine { continuation ->
            LocalOcrProcessor.recognize(bitmap) { text ->
                if (continuation.isActive) continuation.resume(text)
            }
        }

    private fun parseJson(raw: String): JSONObject {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "Gemini không trả về JSON nghiên cứu hợp lệ." }
        return JSONObject(raw.substring(start, end + 1))
    }
}
