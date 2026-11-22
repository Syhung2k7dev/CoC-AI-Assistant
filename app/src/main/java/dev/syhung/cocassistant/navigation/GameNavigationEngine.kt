package dev.syhung.cocassistant.navigation

import android.util.Log
import dev.syhung.cocassistant.engine.CoreEngine
import kotlinx.coroutines.delay

/**
 * GameNavigationEngine - Điều hướng cảnh trong game.
 */
class GameNavigationEngine(private val core: CoreEngine) {

    private val TAG = "GameNavigationEngine"

    suspend fun detectCurrentScene(): String {
        return core.db.worldStateRepository().findAll().firstOrNull()?.sceneId ?: "UNKNOWN"
    }

    suspend fun navigateTo(targetScene: String): Boolean {
        var retries = 3
        while (retries > 0) {
            val current = detectCurrentScene()
            if (current == targetScene) return true
            
            Log.d(TAG, "Navigating from $current to $targetScene")
            
            // Tìm nút điều hướng (Nav Button) cho cảnh đích
            val anchor = core.db.anchorRepository().findByName("NAV_BUTTON", targetScene)
            if (anchor != null) {
                core.action.performClick(anchor.x.toInt(), anchor.y.toInt())
                delay(3000) // Chờ chuyển cảnh
                retries--
            } else {
                Log.e(TAG, "No navigation anchor found for $targetScene")
                break
            }
        }
        return false
    }
}


