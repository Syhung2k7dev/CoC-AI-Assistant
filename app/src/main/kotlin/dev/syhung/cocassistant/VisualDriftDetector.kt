package dev.syhung.cocassistant

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Phát hiện "độ trôi" (drift) trong nhận diện hình ảnh — tín hiệu cho biết
 * giao diện CoC đã thay đổi (patch/update) và Skill / template có thể lỗi thời.
 *
 * Cơ chế:
 *  - Theo dõi confidence trung bình của VisionLearningManager qua N quan sát gần nhất.
 *  - Nếu confidence trung bình < threshold (mặc định 0.55), phát tín hiệu DRIFT.
 *  - Khi có DRIFT, CentralAgentLoop sẽ gọi GroundedDeepResearchService để tái nghiên cứu.
 *
 * Không có network call, không có Gemini call — hoàn toàn local.
 */
class VisualDriftDetector(
    private val context: Context,
    private val windowSize: Int = 20,          // số quan sát gần nhất cần giữ
    private val driftThreshold: Float = 0.55f, // confidence < ngưỡng này → drift
    private val minObservationsRequired: Int = 5  // cần ít nhất N quan sát trước khi phán xét
) {
    companion object {
        private const val TAG = "VisualDriftDetector"
    }

    private val recentConfidences = ArrayDeque<Float>(windowSize)

    /**
     * Ghi nhận một lần quan sát mới với confidence score.
     * Gọi sau mỗi vòng quan sát của CentralAgentLoop.
     */
    fun record(confidence: Float) {
        if (recentConfidences.size >= windowSize) {
            recentConfidences.removeFirst()
        }
        recentConfidences.addLast(confidence.coerceIn(0f, 1f))
        val avg = averageConfidence()
        Log.d(TAG, "Recorded confidence=$confidence | window=${recentConfidences.size} | avg=${"%.2f".format(avg)}")
    }

    /**
     * Kiểm tra xem có đang xảy ra DRIFT hay không.
     * @return DriftState.DRIFT nếu cần tái nghiên cứu, STABLE nếu bình thường, INSUFFICIENT_DATA nếu chưa đủ quan sát.
     */
    fun evaluate(): DriftState {
        if (recentConfidences.size < minObservationsRequired) {
            return DriftState.INSUFFICIENT_DATA
        }
        val avg = averageConfidence()
        return if (avg < driftThreshold) {
            Log.w(TAG, "DRIFT DETECTED: avgConfidence=$avg < threshold=$driftThreshold. Khuyến nghị tái nghiên cứu Skill.")
            DriftState.DRIFT(avg)
        } else {
            DriftState.STABLE(avg)
        }
    }

    /**
     * Trả về bản tóm tắt để đưa vào prompt Gemini.
     */
    fun summary(): String {
        val state = evaluate()
        val avg = if (recentConfidences.isNotEmpty()) averageConfidence() else Float.NaN
        return when (state) {
            is DriftState.DRIFT ->
                "VISUAL_DRIFT: confidence trung bình ${"%.2f".format(avg)} < $driftThreshold; " +
                "game có thể đã cập nhật, Skill/template cũ có thể lỗi thời. " +
                "Hãy kích hoạt GroundedDeepResearch hoặc dừng để tránh thao tác sai."
            is DriftState.STABLE ->
                "VISION_STABLE: confidence trung bình ${"%.2f".format(avg)}; nhận diện bình thường."
            DriftState.INSUFFICIENT_DATA ->
                "VISION_LEARNING: Chỉ mới ${recentConfidences.size}/$minObservationsRequired quan sát, chưa đủ dữ liệu."
        }
    }

    /**
     * Reset khi người dùng xác nhận đã cập nhật Skill/template.
     */
    fun reset() {
        recentConfidences.clear()
        Log.i(TAG, "VisualDriftDetector đã được reset.")
    }

    private fun averageConfidence(): Float =
        if (recentConfidences.isEmpty()) 0f
        else recentConfidences.average().toFloat()
}

sealed class DriftState {
    data class DRIFT(val avgConfidence: Float) : DriftState()
    data class STABLE(val avgConfidence: Float) : DriftState()
    object INSUFFICIENT_DATA : DriftState()
}
