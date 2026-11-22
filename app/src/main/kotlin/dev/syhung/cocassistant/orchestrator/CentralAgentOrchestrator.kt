package dev.syhung.cocassistant.orchestrator

import android.util.Log
import android.graphics.Bitmap
import dev.syhung.cocassistant.detector.VisionSceneDetector
import dev.syhung.cocassistant.detector.Scene
import dev.syhung.cocassistant.navigation.NavigationEngine

class CentralAgentOrchestrator(
    private val navigationEngine: NavigationEngine,
    private val sceneDetector: VisionSceneDetector
) {
    
    suspend fun executeTask(goal: String, currentScreen: Bitmap) {
        Log.d("CentralAgent", "Starting task: $goal")
        
        // 1. Planning: Based on goal, decide next action
        val currentScene = sceneDetector.detectScene(currentScreen)
        
        // 2. Navigate: Check if scene matches
        if (currentScene != Scene.HOME) {
            navigationEngine.navigateTo(Scene.HOME)
        }
        
        // 3. Act: Execute specific skill
        Log.d("CentralAgent", "Executing action for $goal in $currentScene")
        
        // 4. Verify: Check result
    }
}
