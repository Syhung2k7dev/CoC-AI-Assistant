package dev.syhung.cocassistant

import android.content.Context
import android.util.Log

/**
 * Registry tập trung trạng thái toàn bộ hệ thống để hiển thị lên UI/Settings
 */
object SystemStatusRegistry {
    var isGeminiReady: Boolean = false
    var isAccessibilityReady: Boolean = false
    var isVisionReady: Boolean = false
    
    fun getStatus(): Map<String, Any> = mapOf(
        "gemini" to isGeminiReady,
        "accessibility" to isAccessibilityReady,
        "vision" to isVisionReady
    )
}
