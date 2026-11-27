package dev.syhung.cocassistant.detector

import android.graphics.Bitmap

/**
 * Conservative OCR-backed scene adapter.
 *
 * A bitmap alone is not enough evidence to identify a Clash of Clans scene.
 * Callers should pass recognized OCR text when available; otherwise UNKNOWN
 * is returned so the agent cannot treat an arbitrary frame as a battle or village.
 */
class VisionSceneDetector : SceneDetector {
    override fun detectScene(screenData: Any): Scene = when (screenData) {
        is String -> detectOcrText(screenData)
        is Bitmap -> if (!screenData.isRecycled && screenData.width > 0 && screenData.height > 0) {
            Scene.UNKNOWN
        } else {
            Scene.UNKNOWN
        }
        else -> Scene.UNKNOWN
    }

    private fun detectOcrText(text: String): Scene {
        val normalized = text.lowercase()
        return when {
            containsAny(normalized, "end battle", "kết thúc trận", "surrender", "đầu hàng", "time left", "thời gian còn") ->
                Scene.BATTLE
            containsAny(normalized, "builder base", "builder hall", "làng thợ xây", "nhà thợ xây") ->
                Scene.BUILDER_BASE
            containsAny(normalized, "clan capital", "đô thành bang hội", "thủ đô bang hội") ->
                Scene.CLAN_CAPITAL
            containsAny(normalized, "available loot", "chiến lợi phẩm khả dụng", "find a match", "tìm trận", "next", "tiếp theo") ->
                Scene.PREPARATION_SCREEN
            containsAny(normalized, "settings", "cài đặt") ->
                Scene.SETTINGS
            containsAny(normalized, "upgrade", "nâng cấp") ->
                Scene.UPGRADE_SCREEN
            containsAny(normalized, "are you sure", "bạn có chắc", "confirm", "xác nhận") ->
                Scene.POPUP_DIALOG
            containsAny(normalized, "home village", "làng chính") ->
                Scene.HOME
            else -> Scene.UNKNOWN
        }
    }

    private fun containsAny(text: String, vararg phrases: String): Boolean =
        phrases.any(text::contains)
}
