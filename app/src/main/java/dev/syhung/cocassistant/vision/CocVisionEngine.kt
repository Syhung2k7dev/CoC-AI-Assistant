package dev.syhung.cocassistant.vision

import android.graphics.Bitmap
import android.graphics.Rect
import dev.syhung.cocassistant.engine.GesturePerformer

import android.util.Log

/**
 * CocVisionEngine - Nhận diện cảnh qua vùng ROI.
 * ROI chưa được cấu hình (Learning mode).
 */
class CocVisionEngine(private val gesturePerformer: GesturePerformer) {
    
    private val TAG = "CocVisionEngine"

    // Các vùng ROI sẽ được học và lưu vào DB trong quá trình Research.
    private val discoveredROIs = mutableMapOf<String, Rect>()

    fun captureAndProcess(callback: (Map<String, String>) -> Unit) {
        gesturePerformer.captureScreenshot { bitmap ->
            if (bitmap == null) {
                Log.e(TAG, "Screenshot capture failed: bitmap is null")
                callback(mapOf("STATUS" to "ERROR_NULL_SCREENSHOT"))
                return@captureScreenshot
            }
            callback(processScreenshot(bitmap))
            bitmap.recycle()
        }
    }

    fun processScreenshot(bitmap: Bitmap): Map<String, String> {
        val results = mutableMapOf<String, String>()
        
        // Nếu chưa học được vùng nào, trả về trạng thái "Learning"
        if (discoveredROIs.isEmpty()) {
            return mapOf("STATUS" to "LEARNING_REQUIRED")
        }

        for ((key, rect) in discoveredROIs) {
            val roiBitmap = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
            results[key] = analyzeRegion(key, roiBitmap)
            roiBitmap.recycle()
        }
        
        return results
    }

    private fun analyzeRegion(regionName: String, bitmap: Bitmap): String {
        return "UNKNOWN"
    }

    fun learnROI(name: String, rect: Rect) {
        discoveredROIs[name] = rect
    }
}
