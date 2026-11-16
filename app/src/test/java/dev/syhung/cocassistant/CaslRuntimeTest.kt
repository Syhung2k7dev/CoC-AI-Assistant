package dev.syhung.cocassistant

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CaslRuntimeTest {
    @Test
    fun nestedAllAnyNotConditionsEvaluateAgainstContext() {
        val context = JSONObject()
            .put("account", JSONObject().put("level", 12).put("locked", false))
            .put("tags", JSONArray().put("builder").put("active"))
        val condition = JSONObject().put("all", JSONArray()
            .put(JSONObject().put("path", "account.level").put("op", "greater_or_equal").put("value", 10))
            .put(JSONObject().put("not", JSONObject().put("path", "account.locked").put("op", "equals").put("value", true)))
            .put(JSONObject().put("any", JSONArray()
                .put(JSONObject().put("path", "tags").put("op", "contains").put("value", "active"))
                .put(JSONObject().put("path", "tags.0").put("op", "equals").put("value", "unknown")))))
        assertTrue(CaslEngine.evaluateCondition(condition, context))
    }

    @Test
    fun missingAndExistingPathsAreDistinguished() {
        val context = JSONObject().put("data", JSONObject().put("value", JSONObject.NULL))
        assertFalse(CaslEngine.evaluateCondition(
            JSONObject().put("path", "data.value").put("op", "exists"), context
        ))
        assertTrue(CaslEngine.evaluateCondition(
            JSONObject().put("path", "data.missing").put("op", "not_exists"), context
        ))
        assertTrue(CaslEngine.evaluateCondition(
            JSONObject().put("path", "data.value").put("op", "not_exists"), context
        ))
    }

    @Test
    fun scriptRuntimeExecutesIfElseAndWritesOutputs() = runBlocking {
        val program = JSONObject()
            .put("format", "CASL").put("schemaVersion", CaslScriptRuntime.SCHEMA_VERSION)
            .put("kind", "workflow").put("id", "test.workflow").put("version", 1)
            .put("steps", JSONArray()
                .put(JSONObject().put("op", "if")
                    .put("condition", JSONObject().put("all", JSONArray()
                        .put(JSONObject().put("path", "inputs.score").put("op", "greater_than").put("value", 5))
                        .put(JSONObject().put("not", JSONObject().put("path", "inputs.blocked").put("op", "equals").put("value", true)))))
                    .put("then", JSONArray().put(JSONObject().put("op", "output").put("name", "decision").put("value", "approved")))
                    .put("else", JSONArray().put(JSONObject().put("op", "output").put("name", "decision").put("value", "rejected"))))
                .put(JSONObject().put("op", "output").put("name", "echo").put("value", JSONObject().put("$ref", "inputs.score"))))
        val runtime = CaslScriptRuntime(CaslApiRegistry()) { _, _ -> null }
        val result = runtime.execute(program, JSONObject().put("score", 9).put("blocked", false), userApproved = true)
        assertEquals("approved", result.outputs.getString("decision"))
        assertEquals(9, result.outputs.getInt("echo"))
    }

    @Test
    fun runtimeRejectsUnapprovedExecutionAndUnknownApis() = runBlocking {
        val program = JSONObject()
            .put("format", "CASL").put("schemaVersion", CaslScriptRuntime.SCHEMA_VERSION)
            .put("kind", "workflow").put("id", "test.workflow").put("version", 1)
            .put("steps", JSONArray().put(JSONObject().put("op", "call").put("api", "system.unknown")))
        val runtime = CaslScriptRuntime(CaslApiRegistry()) { _, _ -> null }
        assertThrows(CaslRuntimeException::class.java) {
            runBlocking { runtime.execute(program, userApproved = false) }
        }
        assertThrows(CaslRuntimeException::class.java) {
            runBlocking { runtime.execute(program, userApproved = true) }
        }
    }

    @Test
    fun validationRejectsUnpinnedSkillReferences() {
        val program = JSONObject()
            .put("format", "CASL").put("schemaVersion", CaslScriptRuntime.SCHEMA_VERSION)
            .put("kind", "workflow").put("id", "test.workflow").put("version", 1)
            .put("steps", JSONArray().put(JSONObject().put("op", "skill").put("id", "vision.ocr")))
        val runtime = CaslScriptRuntime(CaslApiRegistry()) { _, _ -> null }
        assertThrows(CaslRuntimeException::class.java) {
            runtime.validateProgramDocument(program)
        }
    }
}
