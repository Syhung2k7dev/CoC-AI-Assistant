package dev.syhung.cocassistant

import android.graphics.Bitmap

/**
 * Backward-compatible facade used by the current UI.
 * All transport, error classification and retry behavior lives in AI Core.
 */
object GeminiClient {
    suspend fun generate(apiKey: String, prompt: String, image: Bitmap? = null): String {
        require(apiKey.isNotBlank()) { "Khóa API chưa được nhập" }
        return AIProviderRuntime.generate(
            adapter = GeminiProviderAdapter(apiKey),
            request = AIProviderRequest(prompt = prompt, image = image)
        )
    }
}
