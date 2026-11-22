package dev.syhung.cocassistant

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject

data class GroundedPlaybook(
    val topic: String,
    val summary: String,
    val actionSteps: List<String>,
    val safetyWarnings: List<String>,
    val expectedOutcome: String,
    val rawAnalysis: String
)

/**
 * Dịch vụ Nghiên cứu Chuyên sâu Kết nối Tri thức (Grounded Deep Research).
 * Sử dụng Gemini kết hợp Google Search Grounding để tra cứu cẩm nang, luật chơi,
 * và hướng dẫn từ CoC Wiki, Reddit, Clash Ninja trước khi thao tác trên game.
 */
object GroundedDeepResearchService {

    suspend fun research(
        apiKey: String,
        topic: String,
        currentScreenshot: Bitmap? = null
    ): GroundedPlaybook {
        require(apiKey.isNotBlank()) { "Chưa có Gemini API Key" }
        require(topic.isNotBlank()) { "Chủ đề nghiên cứu không được để trống" }

        val prompt = """
            Bạn là Chuyên gia Nghiên cứu Chiến lược Clash of Clans (CoC Deep Research Agent).
            Hãy sử dụng Google Search để tìm kiếm thông tin mới nhất và chính xác nhất từ CoC Wiki, Reddit, Clash Ninja, và Blog Supercell về:
            
            "$topic"
            
            Hãy tổng hợp thành một CẨM NĂNG HÀNH ĐỘNG (ACTION PLAYBOOK) định dạng JSON thuần túy (không Markdown) theo schema sau:
            {
              "topic": "$topic",
              "summary": "Tóm tắt ngắn gọn tính năng/sự kiện này là gì và người chơi cần đạt được gì",
              "actionSteps": [
                "Bước 1: ...",
                "Bước 2: ...",
                "Bước 3: ..."
              ],
              "safetyWarnings": [
                "Cảnh báo: Không bấm vào nút nào tốn Gem hoặc tiền thật",
                "Cảnh báo: ..."
              ],
              "expectedOutcome": "Dấu hiệu rõ ràng để xác nhận việc này đã hoàn thành thành công"
            }
            
            Lưu ý bắt buộc:
            - Nêu rõ vị trí nút bấm, menu hoặc biểu tượng cần bấm theo mô tả cẩm nang.
            - Phân biệt rõ phần thưởng miễn phí và phần thưởng trả phí.
            - Trả về JSON hợp lệ.
        """.trimIndent()

        val response = AIProviderRuntime.generateWithFallback(
            models = GeminiProviderAdapter.FALLBACK_MODELS,
            request = AIProviderRequest(
                prompt = prompt,
                image = currentScreenshot,
                enableGoogleSearch = true
            ),
            adapterFactory = { model ->
                AIProviderRegistry.createAdapter(
                    configuration = AIProviderConfiguration(
                        providerId = AIProviderId.GEMINI,
                        model = model
                    ),
                    credential = apiKey
                )
            },
            requestTimeoutMillis = 60_000L
        )

        return parsePlaybook(topic, response)
    }

    private fun parsePlaybook(topic: String, raw: String): GroundedPlaybook {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start >= 0 && end > start) {
            val json = JSONObject(raw.substring(start, end + 1))
            val steps = mutableListOf<String>()
            val jsonSteps = json.optJSONArray("actionSteps") ?: JSONArray()
            for (i in 0 until jsonSteps.length()) {
                steps.add(jsonSteps.optString(i))
            }
            val warnings = mutableListOf<String>()
            val jsonWarnings = json.optJSONArray("safetyWarnings") ?: JSONArray()
            for (i in 0 until jsonWarnings.length()) {
                warnings.add(jsonWarnings.optString(i))
            }
            return GroundedPlaybook(
                topic = json.optString("topic", topic),
                summary = json.optString("summary", "Đã nghiên cứu thông tin về $topic"),
                actionSteps = steps,
                safetyWarnings = warnings,
                expectedOutcome = json.optString("expectedOutcome", "Thao tác thành công theo cẩm nang."),
                rawAnalysis = raw
            )
        }
        return GroundedPlaybook(
            topic = topic,
            summary = raw.take(500),
            actionSteps = listOf("Làm theo phân tích của AI"),
            safetyWarnings = listOf("Cẩn thận kiểm tra trước khi bấm"),
            expectedOutcome = "Hoàn thành mục tiêu",
            rawAnalysis = raw
        )
    }
}
