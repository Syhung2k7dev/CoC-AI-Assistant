package dev.syhung.cocassistant.navigation

import dev.syhung.cocassistant.detector.Scene
import android.util.Log
import dev.syhung.cocassistant.service.CocAccessibilityService
import dev.syhung.cocassistant.touch.TouchActionUtils
import android.graphics.PointF

class SimpleNavigationEngine : NavigationEngine {
    
    override suspend fun navigateTo(targetScene: Scene) {
        Log.d("NavigationEngine", "Navigating to $targetScene")
        
        // Example: If target is HOME, tap middle of screen
        val targetPoint = TouchActionUtils.getHumanLikePoint(500f, 500f)
        val path = TouchActionUtils.generateBezierPath(PointF(0f, 0f), targetPoint)
        
        CocAccessibilityService.instance?.dispatchGesture(path, 500)
    }
}
