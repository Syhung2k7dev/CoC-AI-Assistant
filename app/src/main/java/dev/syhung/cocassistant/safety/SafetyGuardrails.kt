package dev.syhung.cocassistant.safety

import android.graphics.Rect

/**
 * SafetyGuardrails - Bộ lọc bảo vệ.
 * Ban đầu trống (Learning mode), sẽ được cập nhật khi Agent khám phá ra các vùng nguy hiểm.
 */
object SafetyGuardrails {
    
    // Định nghĩa các vùng cấm mặc định (Ví dụ: Nút Shop, Gem, Cài đặt)
    private val unsafeZones = mutableListOf<Rect>().apply {
        // Ví dụ tọa độ ước tính (Cần calibration thực tế)
        add(Rect(0, 0, 100, 100)) // Top-left Shop button area
        add(Rect(800, 0, 1000, 100)) // Top-right Settings
    }
    
    fun isSafeAction(x: Int, y: Int, actionType: String): Boolean {
        // Không cho phép chi tiêu Gem (giả định tọa độ Gem là khu vực này)
        if (actionType == "CLICK" && x > 900 && y < 100) return false
        
        return unsafeZones.none { it.contains(x, y) }
    }

    fun addUnsafeZone(rect: Rect) {
        unsafeZones.add(rect)
    }
}

