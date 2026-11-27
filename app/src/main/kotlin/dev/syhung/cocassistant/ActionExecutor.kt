package dev.syhung.cocassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import dev.syhung.cocassistant.database.AppDatabase
import dev.syhung.cocassistant.database.entity.ManualActionEntity
import dev.syhung.cocassistant.safety.SafetyGuardrails
import org.json.JSONObject

sealed class ActionDispatchResult {
    data class Accepted(val skillId: String) : ActionDispatchResult()
    data class Rejected(val reason: String) : ActionDispatchResult()
}

/** Single gateway for verified, user-authorized device actions. */
object ActionExecutor {
    suspend fun executeAwait(
        context: Context,
        registry: AgentSkillRegistry,
        request: SkillActionRequest,
        fromState: String? = null,
        userApproved: Boolean
    ): ActionDispatchResult {
        if (!userApproved) return ActionDispatchResult.Rejected("Thao tác chưa được người dùng phê duyệt.")
        if (!request.validate()) return ActionDispatchResult.Rejected("Yêu cầu thao tác không hợp lệ.")
        val skill = registry.find(request.skillId, request.skillVersion)
            ?: return ActionDispatchResult.Rejected("Không tìm thấy Skill đã pin.")
        if (skill.status != SkillLifecycle.VERIFIED) {
            return ActionDispatchResult.Rejected("Chỉ Skill VERIFIED mới được phép thực thi.")
        }
        if (request.capability !in skill.capabilities) {
            return ActionDispatchResult.Rejected("Skill không khai báo capability.")
        }

        val metrics = context.resources.displayMetrics
        val step = when (request.capability) {
            SkillActionCapability.TAP -> {
                val x = request.normalizedX!!
                val y = request.normalizedY!!
                if (!SafetyGuardrails.isSafeAction(x, y, "CLICK")) {
                    return ActionDispatchResult.Rejected("Tọa độ nằm trong vùng bảo vệ; thao tác đã bị chặn.")
                }
                AutomationStep.Tap(
                    x * metrics.widthPixels / 1000f,
                    y * metrics.heightPixels / 1000f,
                    request.durationMs ?: 100L
                )
            }
            SkillActionCapability.SWIPE -> {
                val x1 = request.normalizedX!!
                val y1 = request.normalizedY!!
                val x2 = request.endNormalizedX!!
                val y2 = request.endNormalizedY!!
                if (!SafetyGuardrails.isSafeAction(x1, y1, "CLICK") ||
                    !SafetyGuardrails.isSafeAction(x2, y2, "CLICK")
                ) {
                    return ActionDispatchResult.Rejected("Đường vuốt đi qua vùng bảo vệ; thao tác đã bị chặn.")
                }
                AutomationStep.Swipe(
                    x1 * metrics.widthPixels / 1000f,
                    y1 * metrics.heightPixels / 1000f,
                    x2 * metrics.widthPixels / 1000f,
                    y2 * metrics.heightPixels / 1000f,
                    request.durationMs!!
                )
            }
            SkillActionCapability.WAIT -> AutomationStep.Wait(request.durationMs!!)
            else -> return ActionDispatchResult.Rejected("Capability này chỉ dùng cho quan sát/xác minh.")
        }

        return if (CoCAccessibilityService.performSingleStep(step)) {
            ActionDispatchResult.Accepted(skill.id)
        } else {
            ActionDispatchResult.Rejected("Accessibility service không thực hiện được thao tác.")
        }
    }

    suspend fun executeAction(context: Context, frame: Bitmap, action: ManualActionEntity): Boolean {
        val finalPoint = if (action.anchorId != null) {
            val template = AppDatabase.getDatabase(context).templateDao().getTemplateById(action.anchorId)
                ?: return false
            val templateBitmap = BitmapFactory.decodeByteArray(template.imageData, 0, template.imageData.size)
                ?: return false
            val anchorPoint = try {
                VisionProcessor.findAnchor(frame, templateBitmap)
            } finally {
                if (!templateBitmap.isRecycled) templateBitmap.recycle()
            } ?: return false
            Point((anchorPoint.x + action.offsetX).toInt(), (anchorPoint.y + action.offsetY).toInt())
        } else {
            Point(action.x1.toInt(), action.y1.toInt())
        }
        if (!isSafePixelPoint(context, finalPoint.x.toFloat(), finalPoint.y.toFloat())) return false
        return CoCAccessibilityService.performSingleStep(
            AutomationStep.Tap(
                finalPoint.x.toFloat(),
                finalPoint.y.toFloat(),
                action.duration.coerceIn(50L, 10_000L)
            )
        )
    }

    suspend fun performGenericAction(context: Context, type: String, argsJson: String): Boolean {
        val args = runCatching { JSONObject(argsJson) }.getOrNull() ?: return false
        val step = when (type.uppercase()) {
            "TAP" -> {
                val x = args.optDouble("x", Double.NaN).toFloat()
                val y = args.optDouble("y", Double.NaN).toFloat()
                if (!x.isFinite() || !y.isFinite() || !isSafePixelPoint(context, x, y)) return false
                AutomationStep.Tap(x, y, args.optLong("duration", 100L).coerceIn(50L, 10_000L))
            }
            "SWIPE" -> {
                val values = listOf("x1", "y1", "x2", "y2")
                    .map { args.optDouble(it, Double.NaN).toFloat() }
                if (values.any { !it.isFinite() } ||
                    !isSafePixelPoint(context, values[0], values[1]) ||
                    !isSafePixelPoint(context, values[2], values[3])
                ) return false
                AutomationStep.Swipe(
                    values[0], values[1], values[2], values[3],
                    args.optLong("duration", 350L).coerceIn(50L, 10_000L)
                )
            }
            "WAIT" -> AutomationStep.Wait(args.optLong("duration", -1L))
            else -> return false
        }
        return CoCAccessibilityService.performSingleStep(step)
    }

    private fun isSafePixelPoint(context: Context, x: Float, y: Float): Boolean {
        val metrics = context.resources.displayMetrics
        if (!x.isFinite() || !y.isFinite() ||
            x < 0f || y < 0f ||
            x >= metrics.widthPixels || y >= metrics.heightPixels
        ) return false
        val normalizedX = (x * 1000f / metrics.widthPixels).toInt().coerceIn(0, 1000)
        val normalizedY = (y * 1000f / metrics.heightPixels).toInt().coerceIn(0, 1000)
        return SafetyGuardrails.isSafeAction(normalizedX, normalizedY, "CLICK")
    }
}
