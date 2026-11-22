package dev.syhung.cocassistant.recovery

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dev.syhung.cocassistant.ActionExecutor
import dev.syhung.cocassistant.database.AppDatabase
import dev.syhung.cocassistant.database.dao.RecoveryPatternDao
import dev.syhung.cocassistant.database.entity.RecoveryPatternEntity
import dev.syhung.cocassistant.research.DeepSkillResearcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Điều phối 3 tầng phục hồi với DB Persistence
 */
class RecoveryOrchestrator(
    private val context: Context,
    private val apiKey: String
) {
    private val instantRecovery = DeepSkillResearcher(context, apiKey)
    private val recoveryDao: RecoveryPatternDao = AppDatabase.getDatabase(context).recoveryPatternDao()

    suspend fun handle(bitmap: Bitmap, currentScene: String): RecoveryResult {
        // Tầng 2: Branching (Kiểm tra DB - Chỉ lấy pattern đã Approved)
        val pattern = withContext(Dispatchers.IO) { recoveryDao.getApprovedPattern(currentScene) }
        if (pattern != null) {
            val actions = org.json.JSONArray(pattern.jsonArgs)
            Log.d("Recovery", "Branching từ DB cho: $currentScene -> ${actions.length()} steps")
            
            // Log hành động Branching
            dev.syhung.cocassistant.util.VisualLogger.logRecovery(context, currentScene, bitmap, "BRANCH_MULTI")
            
            for (i in 0 until actions.length()) {
                val action = actions.getJSONObject(i)
                ActionExecutor.performGenericAction(context, action.getString("type"), action.getJSONObject("args").toString())
            }
            return RecoveryResult.Branch("SEQUENCE")
        }

        // Tầng 1: Instant Analysis
        val actionsArray = instantRecovery.recoverAndReturnActions(bitmap, currentScene)
        if (actionsArray != null && actionsArray.length() > 0) {
            
            // Log hành động học mới
            dev.syhung.cocassistant.util.VisualLogger.logRecovery(context, currentScene, bitmap, "LEARNED_MULTI")
            
            // Tự học: Lưu vào DB
            withContext(Dispatchers.IO) {
                recoveryDao.insert(
                    RecoveryPatternEntity(
                        sceneName = currentScene,
                        actionType = "SEQUENCE",
                        jsonArgs = actionsArray.toString(),
                        lastUsed = System.currentTimeMillis()
                    )
                )
            }
            Log.d("Recovery", "Học thành công và lưu DB: $currentScene -> ${actionsArray.length()} steps")
            
            for (i in 0 until actionsArray.length()) {
                val action = actionsArray.getJSONObject(i)
                ActionExecutor.performGenericAction(context, action.getString("type"), action.getJSONObject("args").toString())
            }
            return RecoveryResult.Handled
        }

        // Tầng 3: Critical Failure
        return RecoveryResult.Critical
    }


}

sealed class RecoveryResult {
    object Handled : RecoveryResult()
    data class Branch(val action: String) : RecoveryResult()
    object Critical : RecoveryResult()
}
