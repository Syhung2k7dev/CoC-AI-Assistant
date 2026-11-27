package dev.syhung.cocassistant

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared cooperative stop flag for the Agent, research loop, and floating HUD.
 * A stop request is checked before every new device action and observation cycle.
 */
object AgentRunControl {
    private val stopRequested = AtomicBoolean(false)

    fun beginRun() {
        stopRequested.set(false)
    }

    fun endRun() {
        stopRequested.set(false)
    }

    fun requestStop() {
        stopRequested.set(true)
        CoCAccessibilityService.stop()
    }

    fun isStopRequested(): Boolean = stopRequested.get()
}
