package dev.syhung.cocassistant.safety

import android.graphics.Rect

/**
 * SafetyGuardrails - Bộ lọc bảo vệ.
 * Ban đầu trống (Learning mode), sẽ được cập nhật khi Agent khám phá ra các vùng nguy hiểm.
 */
object SafetyGuardrails {
    
    // Vùng cấm ban đầu là rỗng. Sẽ được DeepResearch đúc kết và thêm vào sau.
    private val unsafeZones = mutableListOf<Rect>()
    
    fun isSafeAction(x: Int, y: Int, actionType: String): Boolean {
        // Nếu danh sách chưa được học, mặc định an toàn (hoặc có cơ chế cảnh báo)
        if (unsafeZones.isEmpty()) return true
        
        return unsafeZones.none { it.contains(x, y) }
    }

    fun addUnsafeZone(rect: Rect) {
        unsafeZones.add(rect)
    }
}
