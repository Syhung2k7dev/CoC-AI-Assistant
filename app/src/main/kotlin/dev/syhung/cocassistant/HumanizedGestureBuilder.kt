package dev.syhung.cocassistant

import android.accessibilityservice.GestureDescription
import android.graphics.Path

/**
 * Deterministic HVCS coordinate conversion and gesture geometry.
 *
 * Inputs are reproducible and bounded by the active display size. No randomized
 * movement or input camouflage is used.
 */
object HumanizedGestureBuilder {
    fun normalizedToPixel(
        nx: Int,
        ny: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Int, Int> {
        require(nx in 0..1000 && ny in 0..1000) { "HVCS coordinates must be within 0..1000." }
        require(screenWidth > 0 && screenHeight > 0) { "Display dimensions must be positive." }
        val x = (nx.toLong() * (screenWidth - 1) / 1000L).toInt()
        val y = (ny.toLong() * (screenHeight - 1) / 1000L).toInt()
        return x to y
    }

    @Deprecated("Use normalizedToPixel with the current display dimensions.")
    fun normalizedToPixelJittered(
        nx: Int,
        ny: Int,
        sigmaX: Float = 5.5f,
        sigmaY: Float = 5.5f
    ): Pair<Int, Int> = normalizedToPixel(nx, ny, 1600, 900)

    fun buildTap(
        nx: Int,
        ny: Int,
        screenWidth: Int,
        screenHeight: Int,
        durationMs: Long = 100L
    ): GestureDescription.StrokeDescription {
        require(durationMs in 50L..10_000L)
        val (x, y) = normalizedToPixel(nx, ny, screenWidth, screenHeight)
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return GestureDescription.StrokeDescription(path, 0L, durationMs)
    }

    fun buildSwipe(
        startNx: Int,
        startNy: Int,
        endNx: Int,
        endNy: Int,
        screenWidth: Int,
        screenHeight: Int,
        durationMs: Long = 350L
    ): GestureDescription.StrokeDescription {
        require(durationMs in 50L..10_000L)
        val (startX, startY) = normalizedToPixel(startNx, startNy, screenWidth, screenHeight)
        val (endX, endY) = normalizedToPixel(endNx, endNy, screenWidth, screenHeight)
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        return GestureDescription.StrokeDescription(path, 0L, durationMs)
    }

    fun buildGesture(stroke: GestureDescription.StrokeDescription): GestureDescription =
        GestureDescription.Builder().addStroke(stroke).build()
}
