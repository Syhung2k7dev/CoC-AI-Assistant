package dev.syhung.cocassistant.service

import dev.syhung.cocassistant.database.AppDatabase
import dev.syhung.cocassistant.database.entity.ManualActionEntity
import dev.syhung.cocassistant.database.entity.ManualScriptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SkillSyncService {
    suspend fun syncResearchToManual(context: Context, scriptId: Long, researchSessionId: String) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            // Lấy dữ liệu từ bảng research (giả định bạn có bảng này trong SkillResearchDatabase)
            // Ở đây tôi sẽ giả định lấy từ một nguồn dữ liệu nghiên cứu
            val researchResults = db.skillResearchDao().getActionsBySession(researchSessionId)
            
            researchResults.forEachIndexed { index, action ->
                val manualAction = ManualActionEntity(
                    scriptId = scriptId,
                    type = action.type,
                    x1 = action.x,
                    y1 = action.y,
                    order = index,
                    anchorId = action.anchorId // Sử dụng Anchor AI đã tìm thấy
                )
                db.manualActionDao().insert(manualAction)
            }
        }
    }
}
