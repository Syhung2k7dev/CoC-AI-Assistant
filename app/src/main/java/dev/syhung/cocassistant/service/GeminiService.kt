package dev.syhung.cocassistant.service

import android.util.Log

/**
 * GeminiService - Tích hợp Gemini API để suy luận AI và Grounded Deep Research.
 */
class GeminiService(private val apiKey: String) {

    private val TAG = "GeminiService"

    /**
     * Gửi yêu cầu suy luận đến Gemini.
     * @param prompt Nội dung yêu cầu (ví dụ: "Phân tích màn hình này, có nút Shop không?")
     * @param imageBytes (Tùy chọn) Ảnh chụp màn hình để AI phân tích
     */
    fun analyze(prompt: String, imageBytes: ByteArray? = null): String {
        Log.d(TAG, "Analyzing prompt: $prompt")
        // Hiện tại: Mô phỏng gọi API thực tế
        return "DECISION_RESULT"
    }

    /**
     * Grounded Search: Sử dụng Google Search qua Gemini để trả lời câu hỏi về game.
     */
    fun performGroundedSearch(query: String): String {
        Log.d(TAG, "Performing grounded search: $query")
        return "SEARCH_RESULT"
    }
}
