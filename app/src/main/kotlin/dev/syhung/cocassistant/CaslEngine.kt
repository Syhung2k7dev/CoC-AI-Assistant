package dev.syhung.cocassistant

import org.json.JSONArray
import org.json.JSONObject

/** CASL evaluates declarative rules only; it never executes arbitrary code. */
object CaslEngine {
    data class Result(val branch: String, val action: String, val passed: Boolean)

    fun evaluate(rule: JSONObject, context: JSONObject): Result {
        val condition = rule.optJSONObject("condition") ?: error("Thiếu phần điều kiện")
        val path = condition.optString("path")
        require(path.isNotBlank()) { "Đường dẫn điều kiện không được để trống" }
        val actual = resolve(context, path)
        val expected = condition.opt("value")
        val passed = when (condition.optString("op")) {
            "exists" -> actual != null
            "equals" -> actual?.toString() == expected?.toString()
            "not_equals" -> actual?.toString() != expected?.toString()
            "contains" -> actual?.toString()?.contains(expected?.toString() ?: "") == true
            "greater_than" -> compare(actual, expected) { a, b -> a > b }
            "less_than" -> compare(actual, expected) { a, b -> a < b }
            "in" -> (expected as? JSONArray)?.let { array -> (0 until array.length()).any { array.opt(it)?.toString() == actual?.toString() } } ?: false
            else -> error("Toán tử CASL không được hỗ trợ")
        }
        val branch = if (passed) "THEN" else "ELSE"
        val action = rule.optJSONObject(if (passed) "then" else "else")
            ?.let { it.optString("type", "none") + ": " + it.optString("value", "") } ?: "none"
        return Result(branch, action, passed)
    }

    private fun compare(actual: Any?, expected: Any?, operation: (Double, Double) -> Boolean): Boolean {
        val a = (actual as? Number)?.toDouble() ?: return false
        val b = (expected as? Number)?.toDouble() ?: return false
        return operation(a, b)
    }

    private fun resolve(root: JSONObject, path: String): Any? {
        var current: Any? = root
        path.split('.').forEach { part ->
            current = when (val value = current) {
                is JSONObject -> if (value.has(part)) value.opt(part) else null
                else -> return null
            }
        }
        return current
    }
}
