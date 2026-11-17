package dev.syhung.cocassistant

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

enum class WorkflowRunState { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

/** Append-only execution history; stores metadata and traces, never prompts or credentials. */
class WorkflowRunStore(context: Context) {
    private val prefs = context.getSharedPreferences("cadss_store_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun append(record: JSONObject) {
        val items = JSONArray(prefs.getString(KEY_RUNS, "[]"))
        items.put(JSONObject(record.toString()))
        val bounded = JSONArray()
        val start = (items.length() - MAX_RECORDS).coerceAtLeast(0)
        for (i in start until items.length()) bounded.put(items.getJSONObject(i))
        prefs.edit().putString(KEY_RUNS, bounded.toString()).apply()
    }

    @Synchronized
    fun update(runId: String, state: WorkflowRunState, details: JSONObject = JSONObject()): Boolean {
        val items = JSONArray(prefs.getString(KEY_RUNS, "[]") ?: "[]")
        for (i in items.length() - 1 downTo 0) {
            val old = items.optJSONObject(i) ?: continue
            if (old.optString("runId") != runId) continue
            val current = runCatching {
                WorkflowRunState.valueOf(old.optString("state"))
            }.getOrDefault(WorkflowRunState.FAILED)
            val allowed = when (current) {
                WorkflowRunState.QUEUED -> state in setOf(
                    WorkflowRunState.RUNNING,
                    WorkflowRunState.FAILED,
                    WorkflowRunState.CANCELLED
                )
                WorkflowRunState.RUNNING -> state in setOf(
                    WorkflowRunState.SUCCEEDED,
                    WorkflowRunState.FAILED,
                    WorkflowRunState.CANCELLED
                )
                WorkflowRunState.SUCCEEDED,
                WorkflowRunState.FAILED,
                WorkflowRunState.CANCELLED -> false
            }
            if (!allowed) return false
            val updated = JSONObject(old.toString())
                .put("state", state.name)
                .put("updatedAt", System.currentTimeMillis())
            val reserved = setOf("runId", "workflowId", "workflowVersion", "state", "createdAt", "updatedAt")
            details.keys().forEach { key ->
                if (key !in reserved) updated.put(key, details.opt(key))
            }
            items.put(i, updated)
            prefs.edit().putString(KEY_RUNS, items.toString()).apply()
            return true
        }
        return false
    }

    @Synchronized
    fun all(limit: Int = 50): List<JSONObject> {
        val items = JSONArray(prefs.getString(KEY_RUNS, "[]"))
        val start = (items.length() - limit.coerceIn(1, MAX_RECORDS)).coerceAtLeast(0)
        return (start until items.length()).mapNotNull { items.optJSONObject(it)?.let { row -> JSONObject(row.toString()) } }.reversed()
    }

    companion object {
        private const val KEY_RUNS = "collection:workflow_runs"
        private const val MAX_RECORDS = 250
    }
}

/**
 * Orchestrates only immutable VERIFIED workflow versions. Skill calls are resolved to
 * VERIFIED, version-pinned scripts by AgentSkillRegistry through the shared CASL runtime.
 */
class WorkflowEngine(
    private val context: Context,
    private val configureTrustedApis: (CaslApiRegistry) -> Unit = {}
) {
    private val workflows = CaslWorkflowRegistry(context)
    private val skills = AgentSkillRegistry(context)
    private val skillLoader = SkillLoader(skills)
    private val runs = WorkflowRunStore(context)

    suspend fun execute(
        workflowId: String,
        version: Int,
        inputs: JSONObject = JSONObject(),
        approved: Boolean
    ): CaslExecutionResult {
        require(approved) { "Workflow cần được người dùng xác nhận trước khi chạy." }
        require(workflowId.matches(Regex("[a-z0-9][a-z0-9._-]{1,79}")))
        require(version > 0)
        require(inputs.toString().length <= 16_000) { "Dữ liệu đầu vào Workflow vượt giới hạn." }
        val program = workflows.findVerified(workflowId, version)
            ?: throw CaslRuntimeException("Không tìm thấy phiên bản Workflow VERIFIED: $workflowId@$version")
        val runId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        runs.append(JSONObject()
            .put("runId", runId)
            .put("workflowId", workflowId)
            .put("workflowVersion", version)
            .put("state", WorkflowRunState.QUEUED.name)
            .put("createdAt", startedAt)
            .put("updatedAt", startedAt))
        runs.update(runId, WorkflowRunState.RUNNING)
        return try {
            val apiRegistry = createApiRegistry()
            configureTrustedApis(apiRegistry)
            val runtime = CaslScriptRuntime(apiRegistry, skillLoader::loadScript)
            val result = withTimeout(MAX_RUN_TIME_MS) {
                runtime.execute(program, inputs, userApproved = true)
            }
            runs.update(runId, WorkflowRunState.SUCCEEDED, JSONObject()
                .put("finishedAt", System.currentTimeMillis())
                .put("durationMs", System.currentTimeMillis() - startedAt)
                .put("executedSteps", result.executedSteps)
                .put("trace", JSONArray(result.trace.take(100))))
            result
        } catch (timeout: TimeoutCancellationException) {
            runs.update(runId, WorkflowRunState.FAILED, JSONObject()
                .put("finishedAt", System.currentTimeMillis())
                .put("errorType", "TIMEOUT")
                .put("error", "Workflow vượt quá thời gian chạy tối đa."))
            throw timeout
        } catch (cancelled: CancellationException) {
            runs.update(runId, WorkflowRunState.CANCELLED, JSONObject().put("finishedAt", System.currentTimeMillis()))
            throw cancelled
        } catch (error: Exception) {
            runs.update(runId, WorkflowRunState.FAILED, JSONObject()
                .put("finishedAt", System.currentTimeMillis())
                .put("errorType", error::class.java.simpleName)
                .put("error", "Workflow execution failed."))
            throw error
        }
    }

    fun recentRuns(limit: Int = 50): List<JSONObject> = runs.all(limit)

    private fun createApiRegistry(): CaslApiRegistry {
        val registry = CaslApiRegistry()
        val store = CadssStore(context)
        registry.register("memory.read") { args, _ ->
            val collection = args.optString("collection")
            require(collection.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
            val key = args.optString("key")
            val records = store.getRecords(collection)
            val selected = if (key.isBlank()) records else records.filter { it.optString("key") == key }
            JSONObject().put("records", JSONArray(selected))
        }
        registry.register("memory.write") { args, _ ->
            val collection = args.optString("collection")
            require(collection.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
            val record = args.optJSONObject("record") ?: throw CaslRuntimeException("memory.write cần record object.")
            store.addRecord(collection, JSONObject(record.toString()))
            JSONObject().put("written", true).put("collection", collection)
        }
        registry.register("memory.append") { args, _ ->
            val collection = args.optString("collection", "workflow_events")
            require(collection.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
            val event = args.optJSONObject("event") ?: throw CaslRuntimeException("memory.append cần event object.")
            store.addRecord(collection, JSONObject(event.toString()).put("recordedAt", System.currentTimeMillis()))
            JSONObject().put("appended", true)
        }
        registry.register("system.time") { _, _ ->
            JSONObject().put("epochMillis", System.currentTimeMillis())
        }
        registry.register("vision.match") { args, _ ->
            val hash = args.optString("hash")
            val match = VisionLearningManager(context).findNearest(hash)
            if (match == null) {
                JSONObject().put("matched", false).put("distance", -1)
            } else {
                JSONObject()
                    .put("matched", true)
                    .put("templateId", match.first.optString("id"))
                    .put("label", match.first.optString("label"))
                    .put("distance", match.second)
            }
        }
        registry.register("vision.feedback") { args, executionContext ->
            val skillId = executionContext.optString("skillId")
            val skillVersion = executionContext.optInt("skillVersion", 0)
            if (skillId.isBlank() || skillVersion < 1) {
                throw CaslRuntimeException("Vision feedback chỉ được ghi từ Skill đã pin version.")
            }
            val templateId = args.optString("templateId")
            val successful = args.optBoolean("success", false)
            val updated = VisionLearningManager(context).recordOutcome(templateId, successful)
                ?: throw CaslRuntimeException("Không tìm thấy VERIFIED Vision template.")
            JSONObject()
                .put("updated", true)
                .put("templateId", templateId)
                .put("status", updated.optString("status"))
                .put("confidence", updated.optDouble("confidence", 0.0))
        }
        registry.register("action.tap") { args, executionContext ->
            val skillId = executionContext.optString("skillId")
            val skillVersion = executionContext.optInt("skillVersion", 0)
            if (skillId.isBlank() || skillVersion < 1) throw CaslRuntimeException("Native action chỉ được gọi bên trong Skill đã pin version.")
            val request = SkillActionRequest(
                skillId = skillId,
                capability = SkillActionCapability.TAP,
                normalizedX = args.optInt("x", -1),
                normalizedY = args.optInt("y", -1),
                durationMs = if (args.has("durationMs")) args.optLong("durationMs") else null,
                skillVersion = skillVersion
            )
            when (val outcome = ActionExecutor.executeAwait(context, skills, request, userApproved = true)) {
                is ActionDispatchResult.Accepted -> JSONObject().put("accepted", true).put("skillId", outcome.skillId)
                is ActionDispatchResult.Rejected -> throw CaslRuntimeException(outcome.reason)
            }
        }
        registry.register("action.swipe") { args, executionContext ->
            val skillId = executionContext.optString("skillId")
            val skillVersion = executionContext.optInt("skillVersion", 0)
            if (skillId.isBlank() || skillVersion < 1) throw CaslRuntimeException("Native action chỉ được gọi bên trong Skill đã pin version.")
            val request = SkillActionRequest(
                skillId = skillId,
                capability = SkillActionCapability.SWIPE,
                normalizedX = args.optInt("x", -1),
                normalizedY = args.optInt("y", -1),
                endNormalizedX = args.optInt("endX", -1),
                endNormalizedY = args.optInt("endY", -1),
                durationMs = args.optLong("durationMs", -1L),
                skillVersion = skillVersion
            )
            when (val outcome = ActionExecutor.executeAwait(context, skills, request, userApproved = true)) {
                is ActionDispatchResult.Accepted -> JSONObject().put("accepted", true).put("skillId", outcome.skillId)
                is ActionDispatchResult.Rejected -> throw CaslRuntimeException(outcome.reason)
            }
        }
        registry.register("action.wait") { args, executionContext ->
            val skillId = executionContext.optString("skillId")
            val skillVersion = executionContext.optInt("skillVersion", 0)
            if (skillId.isBlank() || skillVersion < 1) throw CaslRuntimeException("Native action chỉ được gọi bên trong Skill đã pin version.")
            val request = SkillActionRequest(
                skillId = skillId,
                capability = SkillActionCapability.WAIT,
                durationMs = args.optLong("durationMs", -1L),
                skillVersion = skillVersion
            )
            when (val outcome = ActionExecutor.executeAwait(context, skills, request, userApproved = true)) {
                is ActionDispatchResult.Accepted -> JSONObject().put("completed", true).put("skillId", outcome.skillId)
                is ActionDispatchResult.Rejected -> throw CaslRuntimeException(outcome.reason)
            }
        }
        return registry
    }

    companion object {
        const val MAX_RUN_TIME_MS = 10 * 60 * 1000L
        val AVAILABLE_API_NAMES = listOf(
            "memory.read", "memory.write", "memory.append", "system.time", "vision.match", "vision.feedback",
            "action.tap", "action.swipe", "action.wait"
        )
    }
}


data class WorkflowTestCaseResult(
    val name: String,
    val passed: Boolean,
    val reason: String
)

data class WorkflowTestReport(
    val workflowId: String,
    val version: Int,
    val passed: Int,
    val total: Int,
    val verified: Boolean,
    val cases: List<WorkflowTestCaseResult>
)

/** Runs declared workflow tests with mocked APIs; verification never invokes real device actions. */
class WorkflowTestRunner(
    context: Context,
    private val availableApiNames: Set<String> = WorkflowEngine.AVAILABLE_API_NAMES.toSet()
) {
    private val workflows = CaslWorkflowRegistry(context)
    private val skills = AgentSkillRegistry(context)

    suspend fun runAndVerify(workflowId: String, version: Int): WorkflowTestReport {
        val program = workflows.find(workflowId, version)
            ?: throw CaslRuntimeException("Không tìm thấy Workflow cần kiểm thử.")
        val lifecycle = program.optString("lifecycle", WorkflowLifecycle.DRAFT.name)
        if (lifecycle == WorkflowLifecycle.DRAFT.name) {
            require(workflows.setLifecycle(workflowId, version, WorkflowLifecycle.TESTING)) {
                "Không thể chuyển Workflow sang TESTING."
            }
        } else {
            require(lifecycle == WorkflowLifecycle.TESTING.name) {
                "Chỉ Workflow DRAFT hoặc TESTING mới được kiểm thử."
            }
        }
        val tests = program.optJSONArray("tests")
            ?: throw CaslRuntimeException("Workflow chưa khai báo tests.")
        require(tests.length() in 1..20) { "Workflow phải có từ 1 đến 20 test cases." }
        val workflowSteps = program.optJSONArray("steps") ?: JSONArray()
        require(!hasDirectNativeActionCall(workflowSteps)) {
            "Workflow chỉ được gọi action.* thông qua Skill đã VERIFIED."
        }
        val apiNames = collectApiNames(workflowSteps, linkedSetOf())
        val unavailableApis = apiNames - availableApiNames
        require(unavailableApis.isEmpty()) {
            "Workflow gọi API chưa được đăng ký: " + unavailableApis.joinToString()
        }
        val results = mutableListOf<WorkflowTestCaseResult>()
        for (index in 0 until tests.length()) {
            val test = tests.optJSONObject(index)
                ?: throw CaslRuntimeException("Test case $index không hợp lệ.")
            val name = test.optString("name", "test-$index").take(80)
            val expectedOutputs = test.optJSONObject("expectOutputs")
            val hasReturnExpectation = test.has("expectReturn")
            require(expectedOutputs != null || hasReturnExpectation) {
                "Test $name phải khai báo expectOutputs hoặc expectReturn."
            }
            val mocks = test.optJSONObject("mockApis") ?: JSONObject()
            val apiRegistry = CaslApiRegistry()
            apiNames.forEach { apiName ->
                apiRegistry.register(apiName) { _, _ -> mocks.optJSONObject(apiName) ?: JSONObject() }
            }
            try {
                val runtime = CaslScriptRuntime(
                    apiRegistry,
                    skillLoader::loadScript,
                    maxSteps = 500,
                    maxDepth = 12,
                    maxRepeat = 20,
                    maxTotalWaitMs = 1_000L
                )
                val inputs = test.optJSONObject("inputs") ?: JSONObject()
                val result = runtime.execute(program, inputs, userApproved = true)
                var mismatch: String? = null
                if (expectedOutputs != null) {
                    expectedOutputs.keys().forEach { key ->
                        if (!sameJsonValue(expectedOutputs.opt(key), result.outputs.opt(key))) {
                            mismatch = "Output '$key' không khớp."
                        }
                    }
                }
                if (hasReturnExpectation && !sameJsonValue(test.opt("expectReturn"), result.returnValue)) {
                    mismatch = "Giá trị return không khớp."
                }
                results.add(WorkflowTestCaseResult(name, mismatch == null, mismatch ?: "Đạt"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                results.add(WorkflowTestCaseResult(name, false, (error.message ?: "Test thất bại").take(240)))
            }
        }
        val passed = results.count { it.passed }
        val verified = passed == tests.length() && workflows.completeTest(workflowId, version, passed, tests.length())
        return WorkflowTestReport(workflowId, version, passed, tests.length(), verified, results)
    }

    private fun collectApiNames(steps: JSONArray, visitedSkills: MutableSet<String>): Set<String> {
        val found = linkedSetOf<String>()
        for (index in 0 until steps.length()) {
            val step = steps.optJSONObject(index) ?: continue
            when (step.optString("op")) {
                "call" -> step.optString("api").takeIf(String::isNotBlank)?.let(found::add)
                "skill" -> {
                    val id = step.optString("id")
                    val version = step.optInt("version", 0)
                    val key = "$id@$version"
                    if (id.isNotBlank() && version > 0 && visitedSkills.add(key)) {
                        val skill = skills.find(id, version)
                            ?: throw CaslRuntimeException("Không tìm thấy Skill dependency $key.")
                        val script = skillLoader.loadScript(id, version)
                            ?: throw CaslRuntimeException("Skill $key chưa VERIFIED hoặc dependency chưa sẵn sàng.")
                        val skillSteps = script.optJSONArray("steps") ?: JSONArray()
                        validateSkillApiCapabilities(skillSteps, skill.capabilities, key)
                        found.addAll(collectApiNames(skillSteps, visitedSkills))
                    }
                }
                "if" -> {
                    found.addAll(collectApiNames(step.optJSONArray("then") ?: JSONArray(), visitedSkills))
                    found.addAll(collectApiNames(step.optJSONArray("else") ?: JSONArray(), visitedSkills))
                }
                "repeat" -> found.addAll(collectApiNames(step.optJSONArray("steps") ?: JSONArray(), visitedSkills))
            }
        }
        return found
    }

    private fun hasDirectNativeActionCall(steps: JSONArray): Boolean {
        for (index in 0 until steps.length()) {
            val step = steps.optJSONObject(index) ?: continue
            when (step.optString("op")) {
                "call" -> if (step.optString("api").startsWith("action.")) return true
                "if" -> if (hasDirectNativeActionCall(step.optJSONArray("then") ?: JSONArray()) ||
                    hasDirectNativeActionCall(step.optJSONArray("else") ?: JSONArray())) return true
                "repeat" -> if (hasDirectNativeActionCall(step.optJSONArray("steps") ?: JSONArray())) return true
            }
        }
        return false
    }

    private fun validateSkillApiCapabilities(
        steps: JSONArray,
        capabilities: Set<SkillActionCapability>,
        skillRef: String
    ) {
        for (index in 0 until steps.length()) {
            val step = steps.optJSONObject(index) ?: continue
            when (step.optString("op")) {
                "call" -> {
                    val required = when (step.optString("api")) {
                        "action.tap" -> SkillActionCapability.TAP
                        "action.swipe" -> SkillActionCapability.SWIPE
                        "action.wait" -> SkillActionCapability.WAIT
                        else -> null
                    }
                    if (required != null && required !in capabilities) {
                        throw CaslRuntimeException("Skill $skillRef gọi ${step.optString("api")} nhưng không khai báo capability $required.")
                    }
                }
                "if" -> {
                    validateSkillApiCapabilities(step.optJSONArray("then") ?: JSONArray(), capabilities, skillRef)
                    validateSkillApiCapabilities(step.optJSONArray("else") ?: JSONArray(), capabilities, skillRef)
                }
                "repeat" -> validateSkillApiCapabilities(step.optJSONArray("steps") ?: JSONArray(), capabilities, skillRef)
            }
        }
    }

    private fun sameJsonValue(expected: Any?, actual: Any?): Boolean {
        if (expected == null || expected == JSONObject.NULL) return actual == null || actual == JSONObject.NULL
        if (actual == null || actual == JSONObject.NULL) return false
        if (expected is Number && actual is Number) return expected.toDouble() == actual.toDouble()
        if (expected is JSONObject && actual is JSONObject) {
            val keys = expected.keys().asSequence().toSet()
            if (keys != actual.keys().asSequence().toSet()) return false
            return keys.all { sameJsonValue(expected.opt(it), actual.opt(it)) }
        }
        if (expected is JSONArray && actual is JSONArray) {
            if (expected.length() != actual.length()) return false
            return (0 until expected.length()).all { sameJsonValue(expected.opt(it), actual.opt(it)) }
        }
        return expected == actual || expected.toString() == actual.toString()
    }
}

/** WorkManager-backed time triggers. Android may defer execution under Doze/battery limits. */
object WorkflowScheduler {
    private const val INPUT_ID = "workflow_id"
    private const val INPUT_VERSION = "workflow_version"
    private const val INPUT_JSON = "workflow_inputs"

    fun scheduleOnce(
        context: Context,
        workflowId: String,
        version: Int,
        runAtMillis: Long,
        inputs: JSONObject = JSONObject()
    ): String {
        validateSchedule(context, workflowId, version, inputs)
        val delay = (runAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val id = "workflow:$workflowId:$version:once"
        val request = OneTimeWorkRequestBuilder<WorkflowExecutionWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workData(workflowId, version, inputs))
            .addTag(id)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(id, ExistingWorkPolicy.REPLACE, request)
        return id
    }

    fun schedulePeriodic(
        context: Context,
        workflowId: String,
        version: Int,
        intervalMinutes: Long,
        inputs: JSONObject = JSONObject()
    ): String {
        validateSchedule(context, workflowId, version, inputs)
        require(intervalMinutes >= 15L) { "WorkManager yêu cầu chu kỳ tối thiểu 15 phút." }
        val id = "workflow:$workflowId:$version:periodic"
        val request = PeriodicWorkRequestBuilder<WorkflowExecutionWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setInputData(workData(workflowId, version, inputs))
            .addTag(id)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(id, ExistingPeriodicWorkPolicy.UPDATE, request)
        return id
    }

    fun cancel(context: Context, scheduleId: String) {
        require(scheduleId.startsWith("workflow:"))
        WorkManager.getInstance(context).cancelUniqueWork(scheduleId)
    }

    fun cancelForWorkflow(context: Context, workflowId: String, version: Int) {
        require(workflowId.matches(Regex("[a-z0-9][a-z0-9._-]{1,79}")) && version > 0)
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork("workflow:$workflowId:$version:once")
        manager.cancelUniqueWork("workflow:$workflowId:$version:periodic")
    }

    private fun validateSchedule(context: Context, id: String, version: Int, inputs: JSONObject) {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]{1,79}")))
        require(version > 0)
        require(inputs.toString().length <= 8_000)
        require(CaslWorkflowRegistry(context).findVerified(id, version) != null) {
            "Chỉ Workflow VERIFIED đúng phiên bản mới được lên lịch."
        }
    }

    private fun workData(id: String, version: Int, inputs: JSONObject): Data =
        Data.Builder().putString(INPUT_ID, id).putInt(INPUT_VERSION, version)
            .putString(INPUT_JSON, inputs.toString()).build()
}

class WorkflowExecutionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("workflow_id") ?: return Result.failure()
        val version = inputData.getInt("workflow_version", 0)
        val rawInputs = inputData.getString("workflow_inputs") ?: "{}"
        return try {
            val inputs = JSONObject(rawInputs)
            WorkflowEngine(applicationContext).execute(id, version, inputs, approved = true)
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (runAttemptCount < 2 && error is java.io.IOException) Result.retry() else Result.failure()
        }
    }
}
