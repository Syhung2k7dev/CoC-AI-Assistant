package dev.syhung.cocassistant.engine

import dev.syhung.cocassistant.database.AppDatabase
import dev.syhung.cocassistant.navigation.GameNavigationEngine
import dev.syhung.cocassistant.research.DeepResearchRunner
import dev.syhung.cocassistant.service.GeminiService
import dev.syhung.cocassistant.vision.CocVisionEngine

/**
 * CoreEngine - Bộ não trung tâm.
 */
class CoreEngine(val db: AppDatabase, gesturePerformer: GesturePerformer) {
    val nav = GameNavigationEngine(this)
    val action = ActionExecutor(gesturePerformer)
    val vision = CocVisionEngine(gesturePerformer)
    val researchRunner = DeepResearchRunner(this)
    val loop = CentralAgentLoop(this)
    val gemini = GeminiService("YOUR_API_KEY_HERE")
    val casl = CaslInterpreter(this)
}

