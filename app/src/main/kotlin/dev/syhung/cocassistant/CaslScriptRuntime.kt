package dev.syhung.cocassistant

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class CaslRuntimeException(message: String) : IllegalStateException(message)

data class CaslExecutionResult(
    val outputs: JSONObject,
    val trace: List<String>,
    val executedSteps: Int
)

fun interface CaslApi {
    suspend fun invoke(arguments: JSONObject, context: JSONObject): JSONObject
}

/** Explicit allow-list of app APIs callable from CASL scripts. */
class CaslApiRegistry {
    private val handlers = linkedMapOf<String, CaslApi>()

    @Synchronized
    fun register(name: String, handler: CaslApi) {
        require(name.matches(Regex("[a-z][a-z0-9_.-]{1,79}")))
        handlers[name] = handler
    }

    suspend fun invoke(name: String, arguments: JSONObject, context: JSONObject): JSONObject {
        val handler = synchronized(this) { handlers[name] }
            ?: throw CaslRuntimeException("CASL API chưa được đăng ký: $name")
        return handler.invoke(arguments, context)
    }
}

/**
 * One interpreter executes both Workflow and Skill programs.
 * Programs are declarative CASL JSON ASTs; arbitrary Kotlin, Java, shell or reflection is never evaluated.
 */
class CaslScriptRuntime(
    private val apiRegistry: CaslApiRegistry,
    private val skillScriptProvider: (String) -> JSONObject?,
    private val maxSteps: Int = 500,
    private val maxDepth: Int = 12,
    private val maxRepeat: Int = 20,
    private val maxTotalWaitMs: Long = 120_000L
) {
    private var executedSteps = 0
    private var totalWaitMs = 0L
    private val trace = mutableListOf<String>()
    private val skillStack = mutableListOf<String>()

    suspend fun execute(
        program: JSONObject,
        inputs: JSONObject = JSONObject(),
        userApproved: Boolean = false
    ): CaslExecutionResult {
        if (!userApproved) throw CaslRuntimeException("Cần xác nhận trước khi khởi chạy Workflow.")
        executedSteps = 0
        totalWaitMs = 0L
        trace.clear()
        skillStack.clear()
        validateProgram(program)
        val context = JSONObject()
            .put("inputs", JSONObject(inputs.toString()))
            .put("vars", JSONObject())
            .put("outputs", JSONObject())
        runProgram(program, context, 0)
        return CaslExecutionResult(
            outputs = JSONObject(context.getJSONObject("outputs").toString()),
            trace = trace.toList(),
            executedSteps = executedSteps
        )
    }

    private suspend fun runProgram(program: JSONObject, context: JSONObject, depth: Int): Any? {
        if (depth > maxDepth) throw CaslRuntimeException("CASL vượt quá độ sâu gọi lồng nhau.")
        validateProgram(program)
        val id = program.optString("id", "anonymous")
        val kind = program.optString("kind")
        if (kind == "skill") {
            if (id in skillStack) throw CaslRuntimeException("Phát hiện Skill gọi đệ quy: $id")
            skillStack.add(id)
        }
        try {
            val steps = program.optJSONArray("steps")
                ?: throw CaslRuntimeException("CASL program thiếu steps.")
            return executeSteps(steps, context, depth)
        } finally {
            if (kind == "skill" && skillStack.lastOrNull() == id) skillStack.removeAt(skillStack.lastIndex)
        }
    }

    private suspend fun executeSteps(steps: JSONArray, context: JSONObject, depth: Int): Any? {
        for (index in 0 until steps.length()) {
            executedSteps++
            if (executedSteps > maxSteps) throw CaslRuntimeException("Workflow vượt giới hạn $maxSteps bước.")
            val step = steps.optJSONObject(index)
                ?: throw CaslRuntimeException("CASL step $index không phải object.")
            when (step.optString("op")) {
                "set" -> {
                    val name = step.optString("name")
                    requireName(name)
                    context.getJSONObject("vars").put(name, resolveValue(step.opt("value"), context))
                }
                "call" -> {
                    val api = step.optString("api")
                    val args = resolveObject(step.optJSONObject("args") ?: JSONObject(), context)
                    val result = apiRegistry.invoke(api, args, context)
                    val assign = step.optString("assign")
                    if (assign.isNotBlank()) context.getJSONObject("vars").put(assign, result)
                }
                "skill" -> {
                    val skillId = step.optString("id")
                    if (skillId.isBlank()) throw CaslRuntimeException("Thiếu Skill ID.")
                    val skillProgram = skillScriptProvider(skillId)
                        ?: throw CaslRuntimeException("Không tìm thấy script của Skill: $skillId")
                    val args = resolveObject(step.optJSONObject("args") ?: JSONObject(), context)
                    val skillContext = JSONObject()
                        .put("inputs", args)
                        .put("vars", JSONObject())
                        .put("outputs", JSONObject())
                    val skillResult = runProgram(skillProgram, skillContext, depth + 1)
                    val assign = step.optString("assign")
                    if (assign.isNotBlank()) {
                        context.getJSONObject("vars").put(
                            assign,
                            JSONObject(skillContext.getJSONObject("outputs").toString())
                                .put("_return", skillResult ?: JSONObject.NULL)
                        )
                    }
                }
                "if" -> {
                    val rule = JSONObject(step.toString())
                    val condition = rule.optJSONObject("condition")
                        ?: throw CaslRuntimeException("IF thiếu condition.")
                    val branch = if (CaslEngine.evaluate(
                            JSONObject().put("condition", condition).put("then", JSONObject()).put("else", JSONObject()),
                            context
                        ).passed) "then" else "else"
                    val nested = step.optJSONArray(branch) ?: JSONArray()
                    val returned = executeSteps(nested, context, depth + 1)
                    if (returned is CaslReturn) return returned.value
                }
                "repeat" -> {
                    val count = step.optInt("count", -1)
                    if (count !in 1..maxRepeat) throw CaslRuntimeException("REPEAT phải từ 1 đến $maxRepeat.")
                    val nested = step.optJSONArray("steps")
                        ?: throw CaslRuntimeException("REPEAT thiếu steps.")
                    repeat(count) {
                        val returned = executeSteps(nested, context, depth + 1)
                        if (returned is CaslReturn) return returned.value
                    }
                }
                "wait" -> {
                    val duration = step.optLong("ms", -1L)
                    if (duration !in 0L..60_000L) throw CaslRuntimeException("WAIT phải từ 0 đến 60000 ms.")
                    totalWaitMs += duration
                    if (totalWaitMs > maxTotalWaitMs) throw CaslRuntimeException("Workflow vượt tổng thời gian WAIT cho phép.")
                    delay(duration)
                }
                "assert" -> {
                    val condition = step.optJSONObject("condition")
                        ?: throw CaslRuntimeException("ASSERT thiếu condition.")
                    val passed = CaslEngine.evaluate(
                        JSONObject().put("condition", condition).put("then", JSONObject()).put("else", JSONObject()),
                        context
                    ).passed
                    if (!passed) throw CaslRuntimeException(step.optString("message", "CASL assertion thất bại."))
                }
                "log" -> trace.add(step.optString("message").take(500))
                "return" -> return CaslReturn(resolveValue(step.opt("value"), context))
                else -> throw CaslRuntimeException("CASL operation không được hỗ trợ: " + step.optString("op"))
            }
        }
        return null
    }

    private fun validateProgram(program: JSONObject) {
        require(program.optString("format") == "CASL") { "Program format phải là CASL." }
        require(program.optInt("schemaVersion", -1) == SCHEMA_VERSION) { "CASL schemaVersion không tương thích." }
        require(program.optString("kind") in setOf("workflow", "skill")) { "CASL kind phải là workflow hoặc skill." }
        require(program.optString("id").matches(Regex("[a-z0-9][a-z0-9._-]{1,79}"))) { "CASL ID không hợp lệ." }
        require(program.optInt("version", 0) >= 1) { "CASL version không hợp lệ." }
        require(program.optJSONArray("steps") != null) { "CASL steps phải là array." }
    }

    private fun resolveObject(source: JSONObject, context: JSONObject): JSONObject {
        val result = JSONObject()
        source.keys().forEach { key -> result.put(key, resolveValue(source.opt(key), context)) }
        return result
    }

    private fun resolveValue(value: Any?, context: JSONObject): Any? {
        if (value is JSONObject) {
            if (value.has("\$ref")) return resolvePath(context, value.optString("\$ref")) ?: JSONObject.NULL
            val result = JSONObject()
            value.keys().forEach { key -> result.put(key, resolveValue(value.opt(key), context)) }
            return result
        }
        if (value is JSONArray) {
            return JSONArray().also { output ->
                for (index in 0 until value.length()) output.put(resolveValue(value.opt(index), context))
            }
        }
        return value
    }

    private fun resolvePath(root: JSONObject, path: String): Any? {
        var current: Any? = root
        path.split('.').forEach { part ->
            current = when (val value = current) {
                is JSONObject -> if (value.has(part)) value.opt(part) else null
                else -> return null
            }
        }
        return current
    }

    private fun requireName(value: String) {
        if (!value.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}"))) {
            throw CaslRuntimeException("Tên biến CASL không hợp lệ.")
        }
    }

    private data class CaslReturn(val value: Any?)

    companion object { const val SCHEMA_VERSION = 1 }
}
