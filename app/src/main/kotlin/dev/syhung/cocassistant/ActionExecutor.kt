package dev.syhung.cocassistant

import android.content.Context

sealed class ActionDispatchResult {
    data class Accepted(val skillId: String) : ActionDispatchResult()
    data class Rejected(val reason: String) : ActionDispatchResult()
}

/** Single gate between declarative Skill requests and Android Accessibility gestures. */
object ActionExecutor {
    fun execute(
        context: Context,
        registry: AgentSkillRegistry,
        request: SkillActionRequest,
        userApproved: Boolean = false
    ): ActionDispatchResult {
        if (!userApproved) return ActionDispatchResult.Rejected("Cần xác nhận của người dùng trước khi thực thi.")
        if (!request.validate()) return ActionDispatchResult.Rejected("Yêu cầu thao tác không hợp lệ.")
        val skill = registry.find(request.skillId)
            ?: return ActionDispatchResult.Rejected("Không tìm thấy Skill.")
        if (skill.status != SkillLifecycle.VERIFIED) {
            return ActionDispatchResult.Rejected("Chỉ Skill đã xác minh mới được thực thi.")
        }
        if (request.capability !in skill.capabilities) {
            return ActionDispatchResult.Rejected("Skill không khai báo capability được yêu cầu.")
        }
        if (!CoCAccessibilityService.isConnected()) {
            return ActionDispatchResult.Rejected("Accessibility Service chưa được kết nối.")
        }

        val metrics = context.resources.displayMetrics
        val x1 = request.normalizedX?.let { HvcsVision.toPixel(it, metrics.widthPixels) }
        val y1 = request.normalizedY?.let { HvcsVision.toPixel(it, metrics.heightPixels) }
        val x2 = request.endNormalizedX?.let { HvcsVision.toPixel(it, metrics.widthPixels) }
        val y2 = request.endNormalizedY?.let { HvcsVision.toPixel(it, metrics.heightPixels) }
        val step = when (request.capability) {
            SkillActionCapability.TAP -> AutomationStep.Tap(
                x = x1!!.toFloat(), y = y1!!.toFloat(), durationMs = request.durationMs ?: 100L
            )
            SkillActionCapability.SWIPE -> AutomationStep.Swipe(
                startX = x1!!.toFloat(), startY = y1!!.toFloat(),
                endX = x2!!.toFloat(), endY = y2!!.toFloat(),
                durationMs = request.durationMs!!
            )
            SkillActionCapability.WAIT -> AutomationStep.Wait(request.durationMs!!)
            else -> return ActionDispatchResult.Rejected("Capability này chưa có bộ thực thi.")
        }
        return if (CoCAccessibilityService.startWorkflow(context, listOf(step))) {
            ActionDispatchResult.Accepted(skill.id)
        } else {
            ActionDispatchResult.Rejected("Accessibility Runtime từ chối workflow.")
        }
    }
}
