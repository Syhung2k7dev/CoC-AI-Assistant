package dev.syhung.cocassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import dev.syhung.cocassistant.database.AppDatabase
import dev.syhung.cocassistant.database.entity.ManualActionEntity
import org.json.JSONObject

sealed class ActionDispatchResult {
    data class Accepted(val skillId: String) : ActionDispatchResult()
    data class Rejected(val reason: String) : ActionDispatchResult()
}

/** Central gateway for verified skill actions and explicitly authored manual actions. */
object ActionExecutor {
    suspend fun executeAwait(context: Context, registry: AgentSkillRegistry, request: SkillActionRequest, fromState: String? = null, userApproved: Boolean): ActionDispatchResult {
        if (!userApproved) return ActionDispatchResult.Rejected("Thao tác chưa được người dùng phê duyệt.")
        if (!request.validate()) return ActionDispatchResult.Rejected("Yêu cầu thao tác không hợp lệ.")
        val skill = registry.find(request.skillId, request.skillVersion) ?: return ActionDispatchResult.Rejected("Không tìm thấy Skill đã pin.")
        if (skill.status != SkillLifecycle.VERIFIED) return ActionDispatchResult.Rejected("Chỉ Skill VERIFIED mới được phép thực thi.")
        if (request.capability !in skill.capabilities) return ActionDispatchResult.Rejected("Skill không khai báo capability.")
        val metrics = context.resources.displayMetrics
        val step = when (request.capability) {
            SkillActionCapability.TAP -> AutomationStep.Tap(request.normalizedX!! * metrics.widthPixels / 1000f, request.normalizedY!! * metrics.heightPixels / 1000f, request.durationMs ?: 100L)
            SkillActionCapability.SWIPE -> AutomationStep.Swipe(request.normalizedX!! * metrics.widthPixels / 1000f, request.normalizedY!! * metrics.heightPixels / 1000f, request.endNormalizedX!! * metrics.widthPixels / 1000f, request.endNormalizedY!! * metrics.heightPixels / 1000f, request.durationMs!!)
            SkillActionCapability.WAIT -> AutomationStep.Wait(request.durationMs!!)
            else -> return ActionDispatchResult.Rejected("Capability này chỉ dùng cho quan sát/xác minh.")
        }
        return if (CoCAccessibilityService.performSingleStep(step)) ActionDispatchResult.Accepted(skill.id) else ActionDispatchResult.Rejected("Accessibility service không thực hiện được thao tác.")
    }

    suspend fun executeAction(context: Context, frame: Bitmap, action: ManualActionEntity): Boolean {
        val finalPoint = if (action.anchorId != null) {
            val template = AppDatabase.getDatabase(context).templateDao().getTemplateById(action.anchorId) ?: return false
            val templateBitmap = BitmapFactory.decodeByteArray(template.imageData, 0, template.imageData.size) ?: return false
            val anchorPoint = try { VisionProcessor.findAnchor(frame, templateBitmap) } finally { if (!templateBitmap.isRecycled) templateBitmap.recycle() } ?: return false
            Point((anchorPoint.x + action.offsetX).toInt(), (anchorPoint.y + action.offsetY).toInt())
        } else Point(action.x1.toInt(), action.y1.toInt())
        return CoCAccessibilityService.performSingleStep(AutomationStep.Tap(finalPoint.x.toFloat(), finalPoint.y.toFloat(), action.duration.coerceIn(50L, 10_000L)))
    }

    suspend fun performGenericAction(context: Context, type: String, argsJson: String): Boolean {
        val args = runCatching { JSONObject(argsJson) }.getOrNull() ?: return false
        val step = when (type.uppercase()) {
            "TAP" -> { val x = args.optDouble("x", Double.NaN).toFloat(); val y = args.optDouble("y", Double.NaN).toFloat(); if (!x.isFinite() || !y.isFinite()) return false; AutomationStep.Tap(x, y, 100L) }
            "SWIPE" -> { val v = listOf("x1", "y1", "x2", "y2").map { args.optDouble(it, Double.NaN).toFloat() }; if (v.any { !it.isFinite() }) return false; AutomationStep.Swipe(v[0], v[1], v[2], v[3], args.optLong("duration", 350L)) }
            "WAIT" -> AutomationStep.Wait(args.optLong("duration", -1L))
            else -> return false
        }
        return CoCAccessibilityService.performSingleStep(step)
    }
}