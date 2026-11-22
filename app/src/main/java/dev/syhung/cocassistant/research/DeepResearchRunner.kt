package dev.syhung.cocassistant.research

import android.util.Log
import dev.syhung.cocassistant.engine.CoreEngine
import dev.syhung.cocassistant.models.ResearchRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DeepResearchRunner - Chịu trách nhiệm thực hiện các tác vụ nghiên cứu đệ quy (max 6-8 bước).
 */
class DeepResearchRunner(private val coreEngine: CoreEngine) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "DeepResearchRunner"

    fun runResearch(taskId: Long, initialQuery: String) {
        scope.launch {
            val record = ResearchRecord(taskId = taskId, query = initialQuery, status = "STARTED")
            coreEngine.db.researchRecordRepository().insert(record)
            
            Log.i(TAG, "Starting research: $initialQuery")
            performResearchRecursive(record, 0)
        }
    }

    private suspend fun performResearchRecursive(record: ResearchRecord, depth: Int) {
        if (depth >= 8) {
            updateStatus(record, "COMPLETED")
            Log.i(TAG, "Research Completed at depth $depth")
            return
        }
        
        Log.d(TAG, "Research step: $depth")
        
        // 1. Observe: Giả lập chụp màn hình (cần tích hợp MediaProjection thật vào đây sau)
        // val bitmap = coreEngine.vision.captureScreen() 
        // val visionResults = coreEngine.vision.processScreenshot(bitmap)
        
        // 2. Analyze: Kiểm tra xem đã tìm thấy gì chưa
        // Ví dụ: Tìm nút Home, nếu thấy thì lưu vào Anchor
        val foundAnchor = false // Giả định logic kiểm tra
        if (foundAnchor) {
            // 3. Learn: Lưu vào DB
            // coreEngine.db.anchorRepository().insert(WorldLayoutAnchor("MAIN_BUTTON", 100, 100))
            Log.i(TAG, "New anchor learned!")
        } else {
            // 4. Decide: Chưa thấy gì? Thử swipe màn hình để khám phá vùng khác
            coreEngine.action.performSwipe(500, 500, 100, 500)
        }

        // Tạm dừng để game kịp phản hồi
        kotlinx.coroutines.delay(2000)
        
        performResearchRecursive(record, depth + 1)
    }

    private suspend fun updateStatus(record: ResearchRecord, status: String) {
        record.status = status
        coreEngine.db.researchRecordRepository().update(record)
    }
}
