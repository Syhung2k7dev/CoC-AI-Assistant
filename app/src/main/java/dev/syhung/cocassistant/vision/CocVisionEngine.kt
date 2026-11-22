package dev.syhung.cocassistant.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import dev.syhung.cocassistant.engine.GesturePerformer
import org.opencv.android.Utils
import org.opencv.core.Mat

/** Conservative local vision adapter. Unknown is returned until a real template is available. */
class CocVisionEngine(private val gesturePerformer: GesturePerformer) {
    private val tag = "CocVisionEngine"
    private val discoveredROIs = mutableMapOf<String, Rect>()

    fun captureAndProcess(callback: (Map<String, String>) -> Unit) {
        gesturePerformer.captureScreenshot { bitmap ->
            if (bitmap == null) {
                callback(mapOf("STATUS" to "ERROR_NULL_SCREENSHOT"))
                return@captureScreenshot
            }
            try { callback(processScreenshot(bitmap)) }
            finally { if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }

    fun processScreenshot(bitmap: Bitmap): Map<String, String> {
        if (discoveredROIs.isEmpty()) return mapOf("STATUS" to "LEARNING_REQUIRED")
        val screen = Mat()
        return try {
            Utils.bitmapToMat(bitmap, screen)
            discoveredROIs.mapValues { (name, rect) ->
                val left = rect.left.coerceIn(0, screen.cols())
                val top = rect.top.coerceIn(0, screen.rows())
                val right = rect.right.coerceIn(left, screen.cols())
                val bottom = rect.bottom.coerceIn(top, screen.rows())
                if (right <= left || bottom <= top) "UNKNOWN" else "UNKNOWN"
            }
        } catch (error: Exception) {
            Log.e(tag, "Vision processing failed", error)
            mapOf("STATUS" to "VISION_ERROR")
        } finally { screen.release() }
    }

    fun learnROI(name: String, rect: Rect) {
        if (name.isNotBlank() && rect.width() > 0 && rect.height() > 0) discoveredROIs[name] = Rect(rect)
    }
}
