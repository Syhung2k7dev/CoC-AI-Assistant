package dev.syhung.cocassistant.safety

import android.graphics.Rect

/**
 * Conservative normalized-coordinate denylist.
 * Coordinates are in HVCS 0..1000 space; invalid input is rejected.
 */
object SafetyGuardrails {
    private val unsafeZones = mutableListOf(
        Rect(0, 0, 100, 100),
        Rect(800, 0, 1000, 100)
    )

    @Synchronized
    fun isSafeAction(x: Int, y: Int, actionType: String): Boolean {
        if (x !in 0..1000 || y !in 0..1000) return false
        if (actionType.uppercase() !in setOf("CLICK", "SWIPE")) return false
        if (actionType.equals("CLICK", ignoreCase = true) && x > 900 && y < 100) return false
        return unsafeZones.none { it.contains(x, y) }
    }

    @Synchronized
    fun addUnsafeZone(rect: Rect) {
        require(!rect.isEmpty) { "Vùng bảo vệ phải có diện tích dương." }
        require(rect.left >= 0 && rect.top >= 0 && rect.right <= 1000 && rect.bottom <= 1000) {
            "Vùng bảo vệ phải nằm trong tọa độ HVCS 0..1000."
        }
        unsafeZones.add(Rect(rect))
    }

    @Synchronized
    fun getUnsafeZones(): List<Rect> = unsafeZones.map { Rect(it) }
}
