package dev.syhung.cocassistant

/** Stable identifiers exposed to AI Core callers, never raw endpoint URLs. */
internal enum class AIProviderId {
    GEMINI
}

internal data class AIProviderConfiguration(
    val providerId: AIProviderId,
    val model: String? = null
)

internal data class AIProviderDescriptor(
    val id: AIProviderId,
    val supportsImageInput: Boolean,
    val supportsStreaming: Boolean
)

/**
 * Central provider catalog and adapter factory.
 *
 * Credentials are supplied at creation time and are not retained by this registry.
 * No automatic fallback is performed: selecting another provider must be an
 * explicit caller/policy decision.
 */
internal object AIProviderRegistry {
    fun descriptors(): List<AIProviderDescriptor> = listOf(
        AIProviderDescriptor(
            id = AIProviderId.GEMINI,
            supportsImageInput = true,
            supportsStreaming = false
        )
    )

    fun createAdapter(
        configuration: AIProviderConfiguration,
        credential: String
    ): AIProviderAdapter {
        require(credential.isNotBlank()) { "Thông tin xác thực Provider chưa được cung cấp" }
        return when (configuration.providerId) {
            AIProviderId.GEMINI -> GeminiProviderAdapter(
                apiKey = credential,
                model = configuration.model ?: GeminiProviderAdapter.DEFAULT_MODEL
            )
        }
    }
}
