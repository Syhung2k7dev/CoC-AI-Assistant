package dev.syhung.cocassistant.engine

import android.graphics.Bitmap
import android.graphics.Color

/**
 * PHashVisualDetector - Phát hiện thay đổi hình ảnh bằng Perceptual Hash.
 * So sánh độ tương đồng giữa 2 ảnh bằng cách hash các vùng (block) của ảnh.
 */
class PHashVisualDetector {

    /**
     * Tạo hash cho một bitmap (đơn giản hóa thành 8x8 grayscaled)
     */
    fun computeHash(bitmap: Bitmap): Long {
        val resized = Bitmap.createScaledBitmap(bitmap, 8, 8, false)
        var hash = 0L
        var avg = 0
        
        // Tính độ sáng trung bình
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                avg += Color.red(resized.getPixel(x, y))
            }
        }
        avg /= 64
        
        // Tạo bit hash
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (Color.red(resized.getPixel(x, y)) >= avg) {
                    hash = hash or (1L shl (y * 8 + x))
                }
            }
        }
        return hash
    }

    /**
     * Tính độ tương đồng (Hamming distance) giữa 2 hash
     */
    fun similarity(hash1: Long, hash2: Long): Double {
        val distance = java.lang.Long.bitCount(hash1 xor hash2)
        return 1.0 - (distance.toDouble() / 64.0)
    }
}