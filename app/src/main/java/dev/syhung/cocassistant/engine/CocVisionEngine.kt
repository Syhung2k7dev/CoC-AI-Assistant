package dev.syhung.cocassistant.engine

import android.graphics.Bitmap
import android.graphics.Point
import dev.syhung.cocassistant.models.WorldState

/**
 * Interface cho Engine thị giác (Vision Engine).
 */
interface CocVisionEngine {
    fun analyzeFrame(frame: Bitmap): WorldState
    fun recognizeText(frame: Bitmap): String
    fun compareWithTemplate(frame: Bitmap, templatePath: String): Float
    // New: Tìm vị trí của anchor dựa trên templateId
    fun findAnchor(frame: Bitmap, anchorId: String): Point?
}
