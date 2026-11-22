package dev.syhung.cocassistant

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Xây dựng các cử chỉ tap/swipe mô phỏng hành vi người dùng thật:
 * - Tap: có jitter Gaussian ±5–7 px quanh tâm mục tiêu
 * - Swipe: đường cong Bézier bậc 2, tốc độ biến đổi, micro-pause ở cuối
 *
 * Không gọi Gemini hoặc bất kỳ API nào — hoàn toàn local/offline.
 */
object HumanizedGestureBuilder {

    private const val DISPLAY_W = 1600
    private const val DISPLAY_H = 900

    // Jitter Gaussian: ±sigma px, clamped vào trong màn hình
    private fun jitter(value: Int, sigma: Float = 5.5f, min: Int = 0, max: Int): Int {
        // Box-Muller transform (xấp xỉ bằng 2 uniform)
        val u1 = Random.nextFloat().coerceAtLeast(1e-6f)
        val u2 = Random.nextFloat().coerceAtLeast(1e-6f)
        val gauss = kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1.toDouble())) *
                kotlin.math.cos(2.0 * Math.PI * u2)
        val offset = (gauss * sigma).roundToInt()
        return (value + offset).coerceIn(min, max)
    }

    /**
     * Tọa độ HVCS (0..1000) → pixel thật với jitter Gaussian.
     */
    fun normalizedToPixelJittered(nx: Int, ny: Int, sigmaX: Float = 5.5f, sigmaY: Float = 5.5f): Pair<Int, Int> {
        val px = (nx * DISPLAY_W / 1000.0).roundToInt()
        val py = (ny * DISPLAY_H / 1000.0).roundToInt()
        return Pair(
            jitter(px, sigmaX, 2, DISPLAY_W - 2),
            jitter(py, sigmaY, 2, DISPLAY_H - 2)
        )
    }

    /**
     * Tạo GestureDescription.StrokeDescription cho TAP human-like.
     * - Thời gian nhấn 80–150ms (ngẫu nhiên)
     * - Jitter vị trí ±5–7 px
     */
    fun buildTap(nx: Int, ny: Int): GestureDescription.StrokeDescription {
        val (px, py) = normalizedToPixelJittered(nx, ny)
        val pressDuration = Random.nextLong(80, 151)
        val path = Path().apply { moveTo(px.toFloat(), py.toFloat()) }
        return GestureDescription.StrokeDescription(path, 0L, pressDuration)
    }

    /**
     * Tạo GestureDescription.StrokeDescription cho SWIPE human-like theo đường cong Bézier bậc 2.
     * Control point lệch ngẫu nhiên khỏi đường thẳng để không bị phát hiện là bot.
     *
     * @param segments Số đoạn chia đường cong (độ mịn, 12–20)
     * @param durationMs Thời gian swipe tổng (200–600ms)
     */
    fun buildSwipe(
        startNx: Int, startNy: Int,
        endNx: Int, endNy: Int,
        durationMs: Long = Random.nextLong(220, 420)
    ): GestureDescription.StrokeDescription {
        val (sx, sy) = normalizedToPixelJittered(startNx, startNy, 3f, 3f)
        val (ex, ey) = normalizedToPixelJittered(endNx, endNy, 3f, 3f)

        // Control point Bézier: trung điểm + lệch ngang/dọc ngẫu nhiên
        val midX = (sx + ex) / 2f
        val midY = (sy + ey) / 2f
        val perpRange = ((kotlin.math.abs(ex - sx) + kotlin.math.abs(ey - sy)) * 0.12f).coerceIn(10f, 60f)
        val cpX = midX + Random.nextFloat() * perpRange * (if (Random.nextBoolean()) 1 else -1)
        val cpY = midY + Random.nextFloat() * perpRange * (if (Random.nextBoolean()) 1 else -1)

        val segments = 16
        val path = Path()
        path.moveTo(sx.toFloat(), sy.toFloat())
        for (i in 1..segments) {
            val t = i.toFloat() / segments
            val oneMinusT = 1f - t
            // Bézier bậc 2: B(t) = (1-t)²·P0 + 2(1-t)t·CP + t²·P1
            val bx = oneMinusT * oneMinusT * sx + 2 * oneMinusT * t * cpX + t * t * ex
            val by = oneMinusT * oneMinusT * sy + 2 * oneMinusT * t * cpY + t * t * ey
            path.lineTo(bx, by)
        }
        return GestureDescription.StrokeDescription(path, 0L, durationMs)
    }

    /**
     * Tạo GestureDescription hoàn chỉnh từ StrokeDescription để gửi qua AccessibilityService.
     */
    fun buildGesture(stroke: GestureDescription.StrokeDescription): GestureDescription =
        GestureDescription.Builder().addStroke(stroke).build()
}
