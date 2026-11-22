package dev.syhung.cocassistant.engine

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import java.util.Random

/**
 * HumanizedGestureBuilder - Xây dựng cử chỉ con người (Bézier, nhiễu Gaussian).
 */
class HumanizedGestureBuilder {

    private val random = Random()

    fun buildClick(x: Int, y: Int): GestureDescription {
        val path = Path()
        val offsetX = (random.nextGaussian() * 5).toFloat()
        val offsetY = (random.nextGaussian() * 5).toFloat()
        
        path.moveTo(x.toFloat() + offsetX, y.toFloat() + offsetY)
        
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, 100 + random.nextInt(60).toLong()))
        return builder.build()
    }

    fun buildSwipe(startX: Int, startY: Int, endX: Int, endY: Int): GestureDescription {
        val path = Path()
        path.moveTo(startX.toFloat(), startY.toFloat())
        
        // Bézier curve
        val midX = (startX + endX) / 2f + (random.nextFloat() - 0.5f) * 50
        val midY = (startY + endY) / 2f + (random.nextFloat() - 0.5f) * 50
        path.quadTo(midX, midY, endX.toFloat(), endY.toFloat())
        
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, 300 + random.nextInt(100).toLong()))
        return builder.build()
    }
}
