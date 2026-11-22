package dev.syhung.cocassistant

import org.json.JSONObject

sealed class NavigationAction {
    data class TapCoordinate(val normalizedX: Int, val normalizedY: Int, val reason: String) : NavigationAction()
    data class SendKey(val keyCode: Int, val reason: String) : NavigationAction()
    data class WaitForScene(val expectedScene: GameScene, val timeoutMs: Long = 5000L) : NavigationAction()
    data class RequestUserAction(val reason: String) : NavigationAction()
    object AlreadyAtTarget : NavigationAction()
}

/**
 * Bộ điều phối chuyển cảnh thông minh giữa các màn hình trong CoC.
 */
object NavigationRouter {

    /**
     * Lập lộ trình di chuyển từ cảnh hiện tại sang cảnh mục tiêu.
     */
    fun route(current: GameScene, target: GameScene, detectionDetails: SceneDetectionResult? = null): NavigationAction {
        if (current == target) return NavigationAction.AlreadyAtTarget

        // Nếu bị popup che, ưu tiên số 1 là đóng popup
        if (current == GameScene.MODAL_POPUP) {
            val closePoint = detectionDetails?.closeButtonNormalized ?: Pair(880, 160)
            return NavigationAction.TapCoordinate(
                normalizedX = closePoint.first,
                normalizedY = closePoint.second,
                reason = "Đóng cửa sổ popup che màn hình để trở về giao diện làng"
            )
        }

        // Đang bảo trì hoặc cập nhật -> Không thể điều hướng
        if (current == GameScene.MAINTENANCE_OR_UPDATE) {
            return NavigationAction.RequestUserAction("Game đang bảo trì hoặc yêu cầu cập nhật. Vui lòng thử lại sau.")
        }

        // Đang ở màn hình trinh sát trận đánh -> Bấm nút Về nhà (End / Surrender)
        if (current == GameScene.ATTACK_SCOUT && (target == GameScene.HOME_VILLAGE || target == GameScene.BUILDER_BASE)) {
            // Nút "Kết thúc trận / Về nhà" thường ở góc dưới bên trái (x: 80, y: 880)
            return NavigationAction.TapCoordinate(
                normalizedX = 80,
                normalizedY = 880,
                reason = "Bấm nút Kết thúc trận để quay về làng chính"
            )
        }

        // Chuyển từ Làng chính -> Làng đêm (Bấm vào Thuyền ở bờ biển góc dưới bên trái)
        if (current == GameScene.HOME_VILLAGE && target == GameScene.BUILDER_BASE) {
            return NavigationAction.TapCoordinate(
                normalizedX = 180,
                normalizedY = 780,
                reason = "Bấm vào Thuyền vượt biển để sang Làng thợ xây"
            )
        }

        // Chuyển từ Làng đêm -> Làng chính (Bấm vào Thuyền để quay về)
        if (current == GameScene.BUILDER_BASE && target == GameScene.HOME_VILLAGE) {
            return NavigationAction.TapCoordinate(
                normalizedX = 220,
                normalizedY = 720,
                reason = "Bấm vào Thuyền vượt biển để quay về Làng chính"
            )
        }

        // Đang tải -> Chờ đợi
        if (current == GameScene.LOADING_SCREEN) {
            return NavigationAction.WaitForScene(target, timeoutMs = 6000L)
        }

        return NavigationAction.RequestUserAction("Không tìm thấy đường điều hướng tự động từ $current sang $target.")
    }
}
