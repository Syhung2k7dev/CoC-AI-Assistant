package dev.syhung.cocassistant

import android.graphics.Bitmap
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/** Hybrid Visual Coordinate System: grid metadata, normalized coordinates and region crops. */
object HvcsVision {
    data class GridCell(
        val id: String, val row: Int, val column: Int,
        val left: Int, val top: Int, val right: Int, val bottom: Int,
        val centerX: Int, val centerY: Int,
        val normalizedCenterX: Int, val normalizedCenterY: Int,
        val meanRed: Int, val meanGreen: Int, val meanBlue: Int,
        val hue: Float, val saturation: Float, val value: Float,
        val luminance: Float, val edgeDensity: Float
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id).put("row", row).put("column", column)
            .put("bounds", JSONObject().put("left", left).put("top", top).put("right", right).put("bottom", bottom))
            .put("center", JSONObject().put("x", centerX).put("y", centerY))
            .put("normalizedCenter", JSONObject().put("x", normalizedCenterX).put("y", normalizedCenterY))
            .put("rgb", JSONArray().put(meanRed).put(meanGreen).put(meanBlue))
            .put("hsv", JSONArray().put(hue.toDouble()).put(saturation.toDouble()).put(value.toDouble()))
            .put("luminance", luminance.toDouble()).put("edgeDensity", edgeDensity.toDouble())
    }

    data class Analysis(
        val imageWidth: Int, val imageHeight: Int, val rows: Int, val columns: Int,
        val cells: List<GridCell>
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("format", "HVCS").put("version", 1)
            .put("imageWidth", imageWidth).put("imageHeight", imageHeight)
            .put("rows", rows).put("columns", columns).put("coordinateScale", 1000)
            .put("cells", JSONArray().apply { cells.forEach { put(it.toJson()) } })
    }

    /** Build a spatial/color/edge map. Gemini refers to cell IDs; Android resolves pixels. */
    fun analyze(bitmap: Bitmap, rows: Int = 16, columns: Int = 16): Analysis {
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0)
        require(rows in 2..32 && columns in 2..32)
        val result = ArrayList<GridCell>(rows * columns)
        for (row in 0 until rows) {
            val top = row * bitmap.height / rows
            val bottom = max(top + 1, (row + 1) * bitmap.height / rows)
            for (column in 0 until columns) {
                val left = column * bitmap.width / columns
                val right = max(left + 1, (column + 1) * bitmap.width / columns)
                val step = max(1, max(right - left, bottom - top) / 24)
                var red = 0L; var green = 0L; var blue = 0L
                var light = 0.0; var edgeTotal = 0.0
                var edgeSamples = 0; var count = 0
                var y = top
                while (y < bottom) {
                    var x = left
                    while (x < right) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
                        red += r; green += g; blue += b
                        val luma = .2126 * r + .7152 * g + .0722 * b
                        light += luma
                        if (x + step < right) {
                            val neighbor = bitmap.getPixel(x + step, y)
                            val other = .2126 * Color.red(neighbor) + .7152 * Color.green(neighbor) +
                                .0722 * Color.blue(neighbor)
                            edgeTotal += abs(luma - other)
                            edgeSamples++
                        }
                        count++; x += step
                    }
                    y += step
                }
                val divisor = max(1, count)
                val mr = (red / divisor).toInt()
                val mg = (green / divisor).toInt()
                val mb = (blue / divisor).toInt()
                val hsv = FloatArray(3)
                Color.RGBToHSV(mr, mg, mb, hsv)
                val centerX = (left + right) / 2
                val centerY = (top + bottom) / 2
                result += GridCell(
                    id = columnLabel(column) + (row + 1), row = row, column = column,
                    left = left, top = top, right = right, bottom = bottom,
                    centerX = centerX, centerY = centerY,
                    normalizedCenterX = (centerX * 1000f / bitmap.width).toInt().coerceIn(0, 1000),
                    normalizedCenterY = (centerY * 1000f / bitmap.height).toInt().coerceIn(0, 1000),
                    meanRed = mr, meanGreen = mg, meanBlue = mb,
                    hue = hsv[0], saturation = hsv[1], value = hsv[2],
                    luminance = (light / divisor / 255.0).toFloat(),
                    edgeDensity = if (edgeSamples == 0) 0f else (edgeTotal / edgeSamples / 255.0).toFloat()
                )
            }
        }
        return Analysis(bitmap.width, bitmap.height, rows, columns, result)
    }

    fun toPixel(normalized: Int, dimension: Int): Int {
        require(normalized in 0..1000 && dimension > 0)
        return ((normalized / 1000f) * (dimension - 1)).toInt().coerceIn(0, dimension - 1)
    }

    /** Caller owns the returned Bitmap and must recycle it when finished. */
    fun cropNormalized(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap {
        require(!bitmap.isRecycled)
        require(left in 0..999 && top in 0..999 && right in 1..1000 && bottom in 1..1000)
        require(right > left && bottom > top)
        val x1 = toPixel(left, bitmap.width)
        val y1 = toPixel(top, bitmap.height)
        val x2 = (toPixel(right, bitmap.width) + 1).coerceAtMost(bitmap.width)
        val y2 = (toPixel(bottom, bitmap.height) + 1).coerceAtMost(bitmap.height)
        return Bitmap.createBitmap(bitmap, x1, y1, (x2 - x1).coerceAtLeast(1), (y2 - y1).coerceAtLeast(1))
    }

    private fun columnLabel(index: Int): String {
        var value = index
        var label = ""
        do {
            label = ('A'.code + value % 26).toChar() + label
            value = value / 26 - 1
        } while (value >= 0)
        return label
    }
}
