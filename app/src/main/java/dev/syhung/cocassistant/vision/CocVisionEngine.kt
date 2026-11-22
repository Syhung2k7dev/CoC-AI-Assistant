package dev.syhung.cocassistant.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import dev.syhung.cocassistant.engine.GesturePerformer
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * CocVisionEngine - Nhận diện cảnh qua vùng ROI sử dụng OpenCV.
 */
class CocVisionEngine(private val gesturePerformer: GesturePerformer) {
    
    private val TAG = "CocVisionEngine"
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
        
        if (discoveredROIs.isEmpty()) {
            return mapOf("STATUS" to "LEARNING_REQUIRED")
        }

        val screenMat = Mat()
        Utils.bitmapToMat(bitmap, screenMat)
        
        for ((key, rect) in discoveredROIs) {
            val roiMat = screenMat.submat(org.opencv.core.Rect(rect.left, rect.top, rect.width(), rect.height()))
            results[key] = analyzeRegion(key, roiMat)
            roiMat.release()
        }
        
        screenMat.release()
        return results
    }

    private fun analyzeRegion(regionName: String, roiMat: Mat): String {
        // Placeholder for template matching logic
        // Thực hiện Template Matching bằng OpenCV
        val resultMat = Mat()
        val templateMat = Mat() // Cần load từ Storage hoặc DB
    /**
     * Tích hợp AI để phân loại cảnh phức tạp khi OpenCV/Template Matching không đủ.
     */
    fun analyzeWithAI(bitmap: Bitmap, prompt: String, gemini: dev.syhung.cocassistant.service.GeminiService): String {
        // Chuyển bitmap thành byte array để gửi cho AI
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val imageBytes = outputStream.toByteArray()
        
        return gemini.analyze(prompt, imageBytes)
    }
        
        // Giả lập load template (bạn cần triển khai load từ đường dẫn file trong DB)
        // Utils.matToBitmap(..., templateMat) 
        
        Imgproc.matchTemplate(roiMat, templateMat, resultMat, Imgproc.TM_CCOEFF_NORMED)
        
        val minMaxLoc = Core.minMaxLoc(resultMat)
        val confidence = minMaxLoc.maxVal
        
        resultMat.release()
        templateMat.release()
        
        return if (confidence > 0.8) "DETECTED" else "UNKNOWN"
        // Imgproc.matchTemplate(roiMat, templateMat, resultMat, Imgproc.TM_CCOEFF_NORMED)
        return "DETECTED" 
    }

    fun learnROI(name: String, rect: Rect) {
        discoveredROIs[name] = rect
        Log.i(TAG, "Learned new ROI: $name at $rect")
    }
}

