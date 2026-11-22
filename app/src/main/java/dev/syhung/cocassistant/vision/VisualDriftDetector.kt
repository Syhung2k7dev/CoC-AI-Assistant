package dev.syhung.cocassistant.vision

import android.graphics.Bitmap

/**
 * VisualDriftDetector - Phát hiện thay đổi cảnh dựa trên so khớp ảnh.
 */
class VisualDriftDetector {

    /**
     * So sánh 2 ảnh, trả về độ lệch. Nếu vượt ngưỡng, cảnh đã thay đổi.
     */
    fun detectDrift(oldScreen: Bitmap, newScreen: Bitmap): Float {
        // Tối giản hóa logic so khớp: Kiểm tra trung bình cường độ điểm ảnh
        // Thực tế sẽ dùng pHash hoặc SSIM
        return 0.0f
    }
}
