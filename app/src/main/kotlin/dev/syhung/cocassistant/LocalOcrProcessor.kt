package dev.syhung.cocassistant

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Offline OCR. The bitmap is released after recognition; text is returned only to the caller. */
object LocalOcrProcessor {
    fun recognize(bitmap: Bitmap, callback: (String?) -> Unit) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { callback(it.text) }
            .addOnFailureListener { callback(null) }
            .addOnCompleteListener {
                // Do not recycle the caller-owned bitmap here. Research Agent sends
                // the same screenshot to Gemini after OCR has completed.
                recognizer.close()
            }
    }
}
