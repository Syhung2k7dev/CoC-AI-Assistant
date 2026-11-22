package dev.syhung.cocassistant.engine

import android.graphics.Bitmap
import android.graphics.Color

/**
 * PHashVisualDetector - Phát hiện thay đổi hình ảnh bằng Perceptual Hash.
 */
class PHashVisualDetector {

    /**
     * Tạo hash cho một bitmap (đơn giản hóa thành 8x8 grayscaled)
     */
    fun computeHash(bitmap: Bitmap): Long {
        val resized = Bitmap.createScaledBitmap(bitmap, 8, 8, false)
        var hash = 0L
        val pixels = IntArray(64)
        resized.getPixels(pixels, 0, 8, 0, 0, 8, 8)
        
        var avg = 0
        for (pixel in pixels) {
            avg += Color.red(pixel)
        }
        avg /= 64
        
        for (i in 0 until 64) {
            if (Color.red(pixels[i]) >= avg) {
                hash = hash or (1L shl i)
            }
        }
        resized.recycle()
        return hash
    }

    /**
     * Tính độ tương đồng (Hamming distance) giữa 2 hash
     */
    fun getSimilarity(hash1: Long, hash2: Long): Double {
        val distance = java.lang.Long.bitCount(hash1 xor hash2)
        return 1.0 - (distance.toDouble() / 64.0)
    }
}
