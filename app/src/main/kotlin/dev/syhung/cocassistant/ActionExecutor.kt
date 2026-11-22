package dev.syhung.cocassistant

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Point
import dev.syhung.cocassistant.database.AppDatabase
import dev.syhung.cocassistant.database.entity.ManualActionEntity

object ActionExecutor {
    suspend fun executeAction(
        context: Context,
        frame: android.graphics.Bitmap,
        action: ManualActionEntity
    ): Boolean {
        val finalPoint = if (action.anchorId != null) {
            val template = AppDatabase.getDatabase(context)
                .templateDao()
                .getTemplateById(action.anchorId) ?: return false

            val templateBitmap = BitmapFactory.decodeByteArray(
                template.imageData,
                0,
                template.imageData.size
            ) ?: return false

            val anchorPoint = try {
                VisionProcessor.findAnchor(frame, templateBitmap)
            } finally {
                if (!templateBitmap.isRecycled) templateBitmap.recycle()
            } ?: return false

            Point(
                (anchorPoint.x + action.offsetX).toInt(),
                (anchorPoint.y + action.offsetY).toInt()
            )
        } else {
            Point(action.x1.toInt(), action.y1.toInt())
        }

        return CoCAccessibilityService.performSingleStep(
            AutomationStep.Tap(finalPoint.x.toFloat(), finalPoint.y.toFloat())
        )
    }
}
