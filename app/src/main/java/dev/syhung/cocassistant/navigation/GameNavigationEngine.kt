package dev.syhung.cocassistant.navigation

import dev.syhung.cocassistant.engine.CoreEngine

/**
 * GameNavigationEngine - Điều hướng cảnh trong game.
 */
class GameNavigationEngine(private val core: CoreEngine) {

    suspend fun detectCurrentScene(): String {
        return core.db.worldStateRepository().findNewestByTTL()?.sceneId ?: "UNKNOWN"
    }

    suspend fun navigateTo(targetScene: String): Boolean {
        if (detectCurrentScene() == targetScene) return true

        val anchor = core.db.anchorRepository().findByName("NAV_BUTTON", targetScene)
        if (anchor != null) {
            core.action.performClick(anchor.x.toInt(), anchor.y.toInt())
            return true
        }
        return false
    }
}

