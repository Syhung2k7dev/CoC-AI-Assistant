package dev.syhung.cocassistant.navigation

import dev.syhung.cocassistant.detector.Scene

interface NavigationEngine {
    /**
     * Executes the necessary touch actions to navigate from the current scene to the target scene.
     */
    suspend fun navigateTo(targetScene: Scene)
}
