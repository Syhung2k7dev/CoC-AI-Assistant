package dev.syhung.cocassistant.engine

import android.util.Log
import dev.syhung.cocassistant.safety.SafetyGuardrails
import android.content.res.Resources

/**
 * ActionExecutor - Hỗ trợ Scaling tự động.
 * Tỉ lệ mặc định chưa được Calibrate.
 */
class ActionExecutor(private val gesturePerformer: GesturePerformer) {

    private val TAG = "ActionExecutor"
    private val builder = HumanizedGestureBuilder()
    
    // Tỉ lệ scale chưa cấu hình (0.0). Hệ thống cần được calibrate qua DeepResearch.
    private var scaleX = 0.0f
    private var scaleY = 0.0f
    private var isCalibrated = false

    fun calibrate(width: Float, height: Float) {
        val screenW = Resources.getSystem().displayMetrics.widthPixels.toFloat()
        val screenH = Resources.getSystem().displayMetrics.heightPixels.toFloat()
        scaleX = screenW / width
        scaleY = screenH / height
        isCalibrated = true
    }

    fun performClick(x: Int, y: Int) {
        if (!isCalibrated) {
            Log.w(TAG, "Uncalibrated action skipped")
            return
        }

        val scaledX = (x * scaleX).toInt()
        val scaledY = (y * scaleY).toInt()

        if (!SafetyGuardrails.isSafeAction(scaledX, scaledY, "CLICK")) return

        val gesture = builder.buildClick(scaledX, scaledY)
        gesturePerformer.dispatchGesture(gesture) { success ->
            Log.d(TAG, "Click executed: $success")
        }
    }

    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int) {
        if (!isCalibrated) return
        
        val sX = (startX * scaleX).toInt()
        val sY = (startY * scaleY).toInt()
        val eX = (endX * scaleX).toInt()
        val eY = (endY * scaleY).toInt()
        
        if (!SafetyGuardrails.isSafeAction(sX, sY, "SWIPE")) return

        val gesture = builder.buildSwipe(sX, sY, eX, eY)
        gesturePerformer.dispatchGesture(gesture) { success ->
            Log.d(TAG, "Swipe executed: $success")
        }
    }
}
