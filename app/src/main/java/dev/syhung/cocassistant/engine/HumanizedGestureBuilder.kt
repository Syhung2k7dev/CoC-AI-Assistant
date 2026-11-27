package dev.syhung.cocassistant.engine

import android.accessibilityservice.GestureDescription
import android.graphics.Path

/**
 * Builds predictable, screen-coordinate gestures.
 *
 * No randomized offsets, timing, or path variation are used.
 */
class HumanizedGestureBuilder {

    fun buildClick(x: Int, y: Int): GestureDescription {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100L))
            .build()
    }

    fun buildSwipe(startX: Int, startY: Int, endX: Int, endY: Int): GestureDescription {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350L))
            .build()
    }
}
