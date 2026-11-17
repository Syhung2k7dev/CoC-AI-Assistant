package dev.syhung.cocassistant

import android.graphics.Bitmap

/**
 * Backward-compatible facade used by the current UI.
 * Provider selection and transport lifecycle are owned by AI Core.
 */
object GeminiClient {
    suspend fun generate(apiKey: String, prompt: String, image: Bitmap? = null): String {
        require(apiKey.isNotBlank()) { "Khóa API chưa được nhập" }
        val adapter = AIProviderRegistry.createAdapter(
            configuration = AIProviderConfiguration(providerId = AIProviderId.GEMINI),
            credential = apiKey
        )
        return AIProviderRuntime.generate(
            adapter = adapter,
            request = AIProviderRequest(prompt = prompt, image = image)
        )
    }
}
