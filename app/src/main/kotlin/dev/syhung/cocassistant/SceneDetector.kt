package dev.syhung.cocassistant

import android.graphics.Bitmap
import org.json.JSONObject

/**
 * Định danh các Cảnh (Scenes) cốt lõi trong Clash of Clans.
 * Giúp Agent và Skill luôn nhận thức được mình đang đứng ở đâu trước khi hành động.
 */
enum class GameScene(val displayName: String, val isPlayableVillage: Boolean = false) {
    HOME_VILLAGE("Làng chính (Home Village)", isPlayableVillage = true),
    BUILDER_BASE("Làng đêm (Builder Base)", isPlayableVillage = true),
    CLAN_CAPITAL("Đô thành bang hội (Clan Capital)", isPlayableVillage = true),
    WAR_MAP("Bản đồ Clan War", isPlayableVillage = false),
    ATTACK_SCOUT("Tìm kiếm / Khảo sát trận đánh", isPlayableVillage = false),
    BATTLE_ACTIVE("Đang trong trận đánh", isPlayableVillage = false),
    MODAL_POPUP("Cửa sổ / Thông báo che màn hình", isPlayableVillage = false),
    MAINTENANCE_OR_UPDATE("Bảo trì hoặc Cần cập nhật", isPlayableVillage = false),
    LOADING_SCREEN("Màn hình tải (Loading)", isPlayableVillage = false),
    UNKNOWN("Chưa xác định", isPlayableVillage = false)
}

data class SceneDetectionResult(
    val scene: GameScene,
    val confidence: Float,
    val details: String,
    val closeButtonNormalized: Pair<Int, Int>? = null, // Tọa độ (0..1000) nút X nếu có popup
    val detectedKeywords: List<String> = emptyList()
)

object SceneDetector {

    /**
     * Phân tích văn bản OCR và đặc trưng ảnh để định vị Cảnh hiện tại với độ trễ cực thấp.
     */
    fun detect(ocrText: String, bitmap: Bitmap? = null): SceneDetectionResult {
        val lowerText = ocrText.lowercase()

        // 1. Kiểm tra màn hình bảo trì / cập nhật hệ thống
        if (lowerText.contains("bảo trì") || lowerText.contains("maintenance") ||
            lowerText.contains("cập nhật") || lowerText.contains("update required") ||
            lowerText.contains("hãy thử lại sau")
        ) {
            return SceneDetectionResult(
                scene = GameScene.MAINTENANCE_OR_UPDATE,
                confidence = 0.95f,
                details = "Hệ thống đang bảo trì hoặc yêu cầu cập nhật game.",
                detectedKeywords = listOf("bảo trì", "cập nhật")
            )
        }

        // 2. Kiểm tra màn hình Đang đánh trận (Battle Active)
        if (lowerText.contains("kết thúc trận") || lowerText.contains("end battle") ||
            lowerText.contains("đầu hàng") || lowerText.contains("surrender") ||
            lowerText.contains("thời gian còn") || lowerText.contains("time left")
        ) {
            return SceneDetectionResult(
                scene = GameScene.BATTLE_ACTIVE,
                confidence = 0.90f,
                details = "Đang trong trận đánh thời gian thực.",
                detectedKeywords = listOf("kết thúc trận", "end battle")
            )
        }

        // 3. Kiểm tra màn hình Tìm trận / Khảo sát (Attack Scout / Nexting)
        if (lowerText.contains("tiếp theo") || lowerText.contains("lượt tiếp") ||
            lowerText.contains("next") || lowerText.contains("chiến lợi phẩm khả dụng") ||
            lowerText.contains("available loot")
        ) {
            return SceneDetectionResult(
                scene = GameScene.ATTACK_SCOUT,
                confidence = 0.92f,
                details = "Đang ở màn hình trinh sát tìm căn cứ đối thủ.",
                detectedKeywords = listOf("tiếp theo", "loot khả dụng")
            )
        }

        // 4. Kiểm tra Pop-up / Hộp thoại che màn hình (Modal Popup)
        val hasCloseKeyword = lowerText.contains("đóng") || lowerText.contains("close") ||
            lowerText.contains("hủy") || lowerText.contains("cancel") || lowerText.contains("xác nhận")
        if (hasCloseKeyword || lowerText.contains("phần thưởng") || lowerText.contains("ưu đãi") || lowerText.contains("sự kiện mới")) {
            // Nút đóng (X) thường neo ở góc trên bên phải của hộp thoại (chuẩn hóa khoảng x: 820..920, y: 120..220)
            return SceneDetectionResult(
                scene = GameScene.MODAL_POPUP,
                confidence = 0.85f,
                details = "Phát hiện hộp thoại hoặc sự kiện che màn hình.",
                closeButtonNormalized = Pair(880, 160),
                detectedKeywords = listOf("popup", "dialog")
            )
        }

        // 5. Kiểm tra Làng đêm (Builder Base)
        if (lowerText.contains("nhà thợ xây") || lowerText.contains("builder hall") ||
            lowerText.contains("thuyền trưởng") || lowerText.contains("máy chiến đấu") ||
            lowerText.contains("battle machine") || lowerText.contains("làng đêm")
        ) {
            return SceneDetectionResult(
                scene = GameScene.BUILDER_BASE,
                confidence = 0.88f,
                details = "Đang ở Làng thợ xây (Làng đêm).",
                detectedKeywords = listOf("builder hall", "làng đêm")
            )
        }

        // 6. Kiểm tra Đô thành (Clan Capital)
        if (lowerText.contains("đô thành") || lowerText.contains("clan capital") ||
            lowerText.contains("đỉnh đô thành") || lowerText.contains("capital peak") ||
            lowerText.contains("đột kích cuối tuần")
        ) {
            return SceneDetectionResult(
                scene = GameScene.CLAN_CAPITAL,
                confidence = 0.90f,
                details = "Đang ở Đô thành bang hội.",
                detectedKeywords = listOf("đô thành", "clan capital")
            )
        }

        // 7. Kiểm tra Bản đồ War (Clan War Map)
        if (lowerText.contains("ngày chuẩn bị") || lowerText.contains("ngày chiến đấu") ||
            lowerText.contains("clan war") || lowerText.contains("chiến tranh bang hội") ||
            lowerText.contains("bản đồ chiến sự")
        ) {
            return SceneDetectionResult(
                scene = GameScene.WAR_MAP,
                confidence = 0.90f,
                details = "Đang ở Bản đồ Clan War.",
                detectedKeywords = listOf("clan war", "chiến tranh bang hội")
            )
        }

        // 8. Kiểm tra Làng chính (Home Village) - Cảnh mặc định và phổ biến nhất
        if (lowerText.contains("tấn công") || lowerText.contains("attack") ||
            lowerText.contains("cửa hàng") || lowerText.contains("shop") ||
            lowerText.contains("thông tin") || lowerText.contains("hồ sơ") ||
            lowerText.contains("bảo vệ") || lowerText.contains("khiên")
        ) {
            return SceneDetectionResult(
                scene = GameScene.HOME_VILLAGE,
                confidence = 0.88f,
                details = "Đang ở Làng chính (Home Village).",
                detectedKeywords = listOf("tấn công", "cửa hàng", "làng chính")
            )
        }

        // 9. Màn hình trống / đang tải
        if (ocrText.isBlank()) {
            return SceneDetectionResult(
                scene = GameScene.LOADING_SCREEN,
                confidence = 0.70f,
                details = "Không nhận diện được chữ. Có thể đang trong màn hình tải hoặc kết nối lại."
            )
        }

        return SceneDetectionResult(
            scene = GameScene.UNKNOWN,
            confidence = 0.50f,
            details = "Chưa xác định được cảnh hiện tại từ OCR."
        )
    }
}
