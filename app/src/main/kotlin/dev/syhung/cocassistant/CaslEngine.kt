package dev.syhung.cocassistant

import org.json.JSONArray
import org.json.JSONObject

/** CASL evaluates declarative rules only; it never executes arbitrary code. */
object CaslEngine {
    data class Result(val branch: String, val action: String, val passed: Boolean)

    fun evaluate(rule: JSONObject, context: JSONObject): Result {
        val condition = rule.optJSONObject("condition") ?: error("Missing condition")
        val path = condition.optString("path")
        require(path.isNotBlank()) { "Condition path is required" }
        val actual = resolve(context, path)
        val expected = condition.opt("value")
        val passed = when (condition.optString("op")) {
            "exists" -> actual != null
            "equals" -> actual?.toString() == expected?.toString()
            "not_equals" -> actual?.toString() != expected?.toString()
            "contains" -> actual?.toString()?.contains(expected?.toString() ?: "") == true
            "greater_than" -> (actual as? Number)?.toDouble()?.let { it > (expected as? Number)?.toDouble() ?: Double.NaN } ?: false
            "less_than" -> (actual as? Number)?.toDouble()?.let { it < (expected as? Number)?.toDouble() ?: Double.NaN } ?: false
            "in" -> (expected as? JSONArray)?.let { array -> (0 until array.length()).any { array.opt(it)?.toString() == actual?.toString() } } ?: false
            else -> error("Unsupported CASL operator")
        }
        val branch = if (passed) "THEN" else "ELSE"
        val action = rule.optJSONObject(if (passed) "then" else "else")
            ?.let { it.optString("type", "none") + ": " + it.optString("value", "") } ?: "none"
        return Result(branch, action, passed)
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
