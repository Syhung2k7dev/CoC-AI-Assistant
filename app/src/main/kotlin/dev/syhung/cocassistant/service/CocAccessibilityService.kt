package dev.syhung.cocassistant.service

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import dev.syhung.cocassistant.CoCAccessibilityService

class CocAccessibilityService : CoCAccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun dispatchGesture(path: Path, duration: Long) {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        var instance: CocAccessibilityService? = null
            private set

        fun stop() = CoCAccessibilityService.stop()
    }
}
