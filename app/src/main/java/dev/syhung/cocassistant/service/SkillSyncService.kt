package dev.syhung.cocassistant.service

import dev.syhung.cocassistant.database.AppDatabase
import dev.syhung.cocassistant.database.entity.ManualActionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SkillSyncService - Đồng bộ hóa kết quả Research thành các Action thủ công.
 */
class SkillSyncService(private val db: AppDatabase) {

    suspend fun syncResearchToManual(scriptId: Long, researchSessionId: String) {
        withContext(Dispatchers.IO) {
            // Lấy các mẫu phục hồi từ database dựa trên session ID
            val researchResults = db.recoveryPatternDao().getPatternsBySessionId(researchSessionId)
            
            researchResults.forEachIndexed { index, pattern ->
                val manualAction = ManualActionEntity(
                    scriptId = scriptId,
                    type = pattern.patternType,
                    x1 = pattern.startX,
                    y1 = pattern.startY,
                    order = index,
                    anchorId = null 
                )
                db.manualActionDao().insert(manualAction)
            }
        }
    }
}
