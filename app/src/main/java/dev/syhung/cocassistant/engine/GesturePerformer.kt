package dev.syhung.cocassistant.engine

import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap

interface GesturePerformer {
    fun dispatchGesture(gesture: GestureDescription, callback: (Boolean) -> Unit)
    fun captureScreenshot(callback: (Bitmap?) -> Unit)
}
