package dev.syhung.cocassistant.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Các trạng thái của Agent.
 */
enum class AgentState {
    IDLE, NAVIGATING, FARMING, MAINTENANCE, RECOVERING
}

/**
 * CentralAgentLoop - Vòng lặp điều phối sử dụng State Machine.
 */
class CentralAgentLoop(private val core: CoreEngine) {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val TAG = "CentralAgentLoop"
    private var currentState: AgentState = AgentState.IDLE
    
    // Target chưa xác định, sẽ được gán bởi quá trình Discovery/Research
    private var currentTarget: String = "UNKNOWN"

    fun start() {
        Log.d(TAG, "Agent Loop Started")
        scheduler.scheduleAtFixedRate({ runTick() }, 0, 2, TimeUnit.SECONDS)
    }

    fun setTarget(target: String) {
        this.currentTarget = target
    }

    private fun runTick() {
        scope.launch {
            try {
                when (currentState) {
                    AgentState.IDLE -> handleIdle()
                    AgentState.NAVIGATING -> handleNavigating()
                    AgentState.FARMING -> handleFarming()
                    AgentState.MAINTENANCE -> handleMaintenance()
                    AgentState.RECOVERING -> handleRecovering()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in tick: ${e.message}")
            }
        }
    }

    private suspend fun handleIdle() {
        Log.d(TAG, "IDLE: Searching for tasks")
        if (currentTarget == "UNKNOWN") {
            Log.i(TAG, "Target unknown, launching DeepResearch...")
            core.researchRunner.runResearch(1L, "DISCOVER_GAME_INTERFACE")
            return
        }
        
        val worldState = core.db.worldStateRepository().findBySceneId(currentTarget)
        if (worldState != null && worldState.busyBuilders == 0) {
            currentState = AgentState.NAVIGATING
        } else {
            currentState = AgentState.MAINTENANCE
        }
    }

    private suspend fun handleNavigating() {
        Log.d(TAG, "NAVIGATING: Moving to $currentTarget")
        val navigated = core.nav.navigateTo(currentTarget)
        if (navigated) {
            currentState = AgentState.FARMING
        } else {
            currentState = AgentState.RECOVERING
        }
    }

    private suspend fun handleFarming() {
        Log.d(TAG, "FARMING: Performing action")
        core.action.performClick(500, 500)
        currentState = AgentState.IDLE
    }

    private suspend fun handleMaintenance() {
        Log.d(TAG, "MAINTENANCE: Sleeping")
        // Logic check timer nâng cấp
        currentState = AgentState.IDLE
    }

    private suspend fun handleRecovering() {
        Log.d(TAG, "RECOVERING: Resolving error")
        currentState = AgentState.IDLE
    }
}


