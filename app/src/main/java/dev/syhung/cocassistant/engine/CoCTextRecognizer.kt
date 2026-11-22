package dev.syhung.cocassistant.engine

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * CoCTextRecognizer - Sử dụng ML Kit để trích xuất văn bản từ game.
 */
class CoCTextRecognizer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Trích xuất văn bản từ một Bitmap cụ thể. 
     * Sử dụng Tasks.await() để blocking, phù hợp với luồng xử lý frame của VisionEngine.
     */
    fun extractText(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        return try {
            val task = recognizer.process(image)
            val result = Tasks.await(task, 2, TimeUnit.SECONDS) // Timeout 2s
            result.text
        } catch (e: Exception) {
            ""
        }
    }
}