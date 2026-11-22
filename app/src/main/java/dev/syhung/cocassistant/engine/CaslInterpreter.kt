package dev.syhung.cocassistant.engine

import android.util.Log

/**
 * CaslInterpreter - Trình thông dịch cho ngôn ngữ CASL (CoC Automation Script Language).
 */
class CaslInterpreter(private val core: CoreEngine) {

    private val TAG = "CaslInterpreter"
    private val variables = mutableMapOf<String, String>()

    fun executeScript(script: String) {
        val lines = script.lines()
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) continue
            
            val tokens = trimmedLine.split(" ")
            
            when (tokens[0].uppercase()) {
                "SET" -> {
                    if (tokens.size >= 3) {
                        variables[tokens[1]] = tokens[2]
                        Log.d(TAG, "Variable set: ${tokens[1]} = ${tokens[2]}")
                    }
                }
                "CALL" -> {
                    val skillName = tokens[1]
                    Log.d(TAG, "Calling skill: $skillName")
                    // Future implementation: core.action.performSkill(skillName)
                }
                "WAIT" -> {
                    val duration = tokens[1].toLongOrNull() ?: 1000L
                    Log.d(TAG, "Waiting for $duration ms")
                    try {
                        Thread.sleep(duration)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                "ASSERT" -> {
                    if (tokens.size >= 3) {
                        val varName = tokens[1]
                        val expectedValue = tokens[2]
                        val currentValue = variables[varName]
                        if (currentValue != expectedValue) {
                            Log.e(TAG, "Assertion failed: $varName expected $expectedValue but got $currentValue")
                        }
                    }
                }
            }
        }
    }
}

