package dev.syhung.cocassistant

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Point
import dev.syhung.cocassistant.database.AppDatabase
import dev.syhung.cocassistant.database.entity.ManualActionEntity
import dev.syhung.cocassistant.accessibility.CoCAccessibilityService

object ActionExecutor {
    
    suspend fun executeAction(context: Context, frame: android.graphics.Bitmap, action: ManualActionEntity): Boolean {
        var anchorPoint: Point? = null
        
        // 1. Xác định tọa độ mỏ neo với kiểm tra null an toàn
        if (action.anchorId != null) {
            try {
                val template = AppDatabase.getDatabase(context).templateDao().getTemplateById(action.anchorId)
                if (template != null) {
                    val templateBitmap = BitmapFactory.decodeByteArray(template.imageData, 0, template.imageData.size)
                    if (templateBitmap != null) {
                        anchorPoint = VisionProcessor.findAnchor(frame, templateBitmap)
                        templateBitmap.recycle() // Giải phóng bộ nhớ ngay sau khi dùng
                    }
                }
            } catch (e: Exception) {
                // Log lỗi nếu cần, nhưng không làm crash bot
                e.printStackTrace()
            }
        }

        // 2. Tính toán điểm Click
        val finalPoint = if (anchorPoint != null) {
            Point(
                (anchorPoint.x + action.offsetX).toInt(),
                (anchorPoint.y + action.offsetY).toInt()
            )
        } else {
            Point(action.x1.toInt(), action.y1.toInt())
        }

        // 3. Thực hiện click
        return CoCAccessibilityService.performClick(finalPoint.x, finalPoint.y)
    }
}
