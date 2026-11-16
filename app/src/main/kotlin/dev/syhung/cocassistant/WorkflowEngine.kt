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
    fun update(runId: String, state: WorkflowRunState, details: JSONObject = JSONObject()) {
        val items = JSONArray(prefs.getString(KEY_RUNS, "[]"))
        for (i in items.length() - 1 downTo 0) {
            val old = items.optJSONObject(i) ?: continue
            if (old.optString("runId") == runId) {
                val updated = JSONObject(old.toString())
                    .put("state", state.name)
                    .put("updatedAt", System.currentTimeMillis())
                details.keys().forEach { key -> updated.put(key, details.opt(key)) }
                items.put(i, updated)
                prefs.edit().putString(KEY_RUNS, items.toString()).apply()
                return
            }
        }
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
            val runtime = CaslScriptRuntime(apiRegistry, skills::findVerifiedScript)
            val result = withTimeout(MAX_RUN_TIME_MS) {
                runtime.execute(program, inputs, userApproved = true)
            }
            runs.update(runId, WorkflowRunState.SUCCEEDED, JSONObject()
                .put("finishedAt", System.currentTimeMillis())
                .put("durationMs", System.currentTimeMillis() - startedAt)
                .put("executedSteps", result.executedSteps)
                .put("trace", JSONArray(result.trace.take(100))))
            result
        } catch (cancelled: CancellationException) {
            runs.update(runId, WorkflowRunState.CANCELLED, JSONObject().put("finishedAt", System.currentTimeMillis()))
            throw cancelled
        } catch (error: Exception) {
            runs.update(runId, WorkflowRunState.FAILED, JSONObject()
                .put("finishedAt", System.currentTimeMillis())
                .put("errorType", error::class.java.simpleName)
                .put("error", (error.message ?: "Workflow thất bại").take(300)))
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
        return registry
    }

    companion object { const val MAX_RUN_TIME_MS = 10 * 60 * 1000L }
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
        validateSchedule(workflowId, version, inputs)
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
        validateSchedule(workflowId, version, inputs)
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

    private fun validateSchedule(id: String, version: Int, inputs: JSONObject) {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]{1,79}")))
        require(version > 0)
        require(inputs.toString().length <= 8_000)
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
