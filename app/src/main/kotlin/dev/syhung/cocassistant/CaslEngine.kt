package dev.syhung.cocassistant

import org.json.JSONArray
import org.json.JSONObject

/** CASL condition evaluator. Supports nested all/any/not and typed leaf comparisons. */
object CaslEngine {
    data class Result(val branch: String, val action: String, val passed: Boolean)

    fun evaluate(rule: JSONObject, context: JSONObject): Result {
        val condition = rule.optJSONObject("condition")
            ?: throw CaslRuntimeException("Thiếu phần điều kiện CASL.")
        val passed = evaluateCondition(condition, context)
        val branchName = if (passed) "THEN" else "ELSE"
        val action = rule.optJSONObject(if (passed) "then" else "else")
            ?.let { it.optString("type", "none") + ": " + it.optString("value", "") } ?: "none"
        return Result(branchName, action, passed)
    }

    fun evaluateCondition(condition: JSONObject, context: JSONObject): Boolean {
        val groups = listOf("all", "any", "not").filter(condition::has)
        if (groups.isNotEmpty()) {
            if (groups.size != 1 || condition.length() != 1) {
                throw CaslRuntimeException("Điều kiện nhóm chỉ được chứa một toán tử all, any hoặc not.")
            }
            return when (groups.single()) {
                "all" -> {
                    val items = condition.optJSONArray("all") ?: throw CaslRuntimeException("all phải là mảng.")
                    (0 until items.length()).all { evaluateCondition(items.getJSONObject(it), context) }
                }
                "any" -> {
                    val items = condition.optJSONArray("any") ?: throw CaslRuntimeException("any phải là mảng.")
                    (0 until items.length()).any { evaluateCondition(items.getJSONObject(it), context) }
                }
                else -> evaluateCondition(
                    condition.optJSONObject("not") ?: throw CaslRuntimeException("not phải chứa một điều kiện."),
                    context
                ).not()
            }
        }
        val path = condition.optString("path")
        require(path.isNotBlank() && path.length <= 256) { "Đường dẫn điều kiện không hợp lệ." }
        val actual = resolve(context, path)
        val expected = condition.opt("value")
        return when (condition.optString("op")) {
            "exists" -> actual != null && actual != JSONObject.NULL
            "not_exists" -> actual == null || actual == JSONObject.NULL
            "equals" -> equivalent(actual, expected)
            "not_equals" -> !equivalent(actual, expected)
            "contains" -> when (actual) {
                is JSONArray -> (0 until actual.length()).any { equivalent(actual.opt(it), expected) }
                is String -> actual.contains(expected?.toString() ?: "")
                else -> false
            }
            "starts_with" -> (actual as? String)?.startsWith(expected?.toString() ?: "") == true
            "ends_with" -> (actual as? String)?.endsWith(expected?.toString() ?: "") == true
            "greater_than" -> compare(actual, expected) { a, b -> a > b }
            "less_than" -> compare(actual, expected) { a, b -> a < b }
            "greater_or_equal" -> compare(actual, expected) { a, b -> a >= b }
            "less_or_equal" -> compare(actual, expected) { a, b -> a <= b }
            "in" -> (expected as? JSONArray)?.let { array ->
                (0 until array.length()).any { equivalent(array.opt(it), actual) }
            } ?: false
            "not_in" -> (expected as? JSONArray)?.let { array ->
                (0 until array.length()).none { equivalent(array.opt(it), actual) }
            } ?: false
            else -> throw CaslRuntimeException("Toán tử CASL không được hỗ trợ: ${condition.optString("op")}")
        }
    }

    private fun equivalent(left: Any?, right: Any?): Boolean {
        if (left == null || left == JSONObject.NULL) return right == null || right == JSONObject.NULL
        if (right == null || right == JSONObject.NULL) return false
        if (left is Number && right is Number) return left.toDouble() == right.toDouble()
        return left == right || left.toString() == right.toString()
    }

    private fun compare(actual: Any?, expected: Any?, operation: (Double, Double) -> Boolean): Boolean {
        val a = (actual as? Number)?.toDouble() ?: actual?.toString()?.toDoubleOrNull() ?: return false
        val b = (expected as? Number)?.toDouble() ?: expected?.toString()?.toDoubleOrNull() ?: return false
        return operation(a, b)
    }

    private fun resolve(root: JSONObject, path: String): Any? {
        var current: Any? = root
        for (part in path.split('.')) {
            current = when (val value = current) {
                is JSONObject -> if (value.has(part)) value.opt(part) else return null
                is JSONArray -> part.toIntOrNull()?.takeIf { it in 0 until value.length() }?.let(value::opt) ?: return null
                else -> return null
            }
        }
        return current
    }
}
