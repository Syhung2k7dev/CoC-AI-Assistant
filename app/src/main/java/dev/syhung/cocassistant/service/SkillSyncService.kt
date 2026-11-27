package dev.syhung.cocassistant.service

import dev.syhung.cocassistant.database.AppDatabase
import dev.syhung.cocassistant.database.entity.ManualActionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Converts approved recovery sequences into explicit, editable manual actions. */
class SkillSyncService(private val db: AppDatabase) {
    suspend fun syncResearchToManual(scriptId: Long, researchSessionId: String) = withContext(Dispatchers.IO) {
        require(scriptId > 0L)
        // RecoveryPatternEntity has no session identifier; only approved patterns are eligible.
        val patterns = db.recoveryPatternDao().getApprovedPatterns()
        val actions = mutableListOf<ManualActionEntity>()
        patterns.forEach { pattern ->
            val sequence = runCatching { JSONArray(pattern.jsonArgs) }.getOrNull() ?: return@forEach
            for (index in 0 until sequence.length()) {
                val item = sequence.optJSONObject(index) ?: continue
                val type = item.optString("type").uppercase()
                val args = item.optJSONObject("args") ?: continue
                val x = args.optDouble("x", Double.NaN)
                val y = args.optDouble("y", Double.NaN)
                if (type != "TAP" || !x.isFinite() || !y.isFinite() ||
                    x !in 0.0..1_000.0 || y !in 0.0..1_000.0) continue
                actions += ManualActionEntity(
                    scriptId = scriptId,
                    type = "CLICK",
                    x1 = x.toFloat(),
                    y1 = y.toFloat(),
                    order = actions.size
                )
            }
        }
        if (actions.isNotEmpty()) db.manualActionDao().insertActions(actions)
    }
}
