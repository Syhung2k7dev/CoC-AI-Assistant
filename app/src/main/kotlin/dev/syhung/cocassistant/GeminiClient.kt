package dev.syhung.cocassistant

import android.graphics.Bitmap

/**
 * Backward-compatible facade used by the current UI.
 * Provider selection and transport lifecycle are owned by AI Core.
 */
object GeminiClient {
    suspend fun generate(apiKey: String, prompt: String, image: Bitmap? = null): String {
        require(apiKey.isNotBlank()) { "Khóa API chưa được nhập" }
        return AIProviderRuntime.generateWithFallback(
            models = GeminiProviderAdapter.FALLBACK_MODELS,
            request = AIProviderRequest(prompt = prompt, image = image),
            adapterFactory = { model ->
                AIProviderRegistry.createAdapter(
                    configuration = AIProviderConfiguration(
                        providerId = AIProviderId.GEMINI,
                        model = model
                    ),
                    credential = apiKey
                )
            },
            requestTimeoutMillis = 45_000L
        )
    }
}
