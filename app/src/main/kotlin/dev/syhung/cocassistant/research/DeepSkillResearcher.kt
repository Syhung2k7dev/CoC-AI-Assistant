package dev.syhung.cocassistant.research

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dev.syhung.cocassistant.GeminiClient
import dev.syhung.cocassistant.ActionExecutor
import org.json.JSONObject
import org.json.JSONArray

class DeepSkillResearcher(private val context: Context, private val apiKey: String) {
    
    // Trả về JSONArray chứa danh sách các action
    suspend fun recoverAndReturnActions(bitmap: Bitmap, currentScene: String): JSONArray? {
        Log.d("DeepSkillResearcher", "Attempting multi-step recovery for scene: $currentScene")
        
        val prompt = """
            Tôi đang điều khiển Clash of Clans. Tôi đang ở scene: $currentScene.
            Giao diện bị chặn. Hãy phân tích ảnh này:
            1. Liệt kê các hành động cần thiết để đóng pop-up theo trình tự (có thể là TAP, SWIPE, WAIT).
            2. Trả về JSON ARRAY: 
            [
                {"type": "TAP", "args": {"x": 500, "y": 500}},
                {"type": "WAIT", "args": {"duration": 1000}},
                {"type": "TAP", "args": {"x": 600, "y": 600}}
            ]
            Nếu không cần làm gì, trả về [] (rỗng).
        """.trimIndent()
        
        val response = GeminiClient.generate(apiKey, prompt, bitmap)
        return try {
            val json = org.json.JSONTokener(response).nextValue()
            if (json is JSONArray && json.length() > 0) json else null
        } catch (e: Exception) {
            null
        }
    }


}
