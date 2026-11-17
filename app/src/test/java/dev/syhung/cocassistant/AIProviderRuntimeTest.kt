package dev.syhung.cocassistant

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AIProviderRuntimeTest {
    private val noWait = AIRetryPolicy(
        maxRetries = 1,
        initialDelayMillis = 0,
        maxDelayMillis = 0,
        jitterMillis = 0,
        maxRetryAfterMillis = 0
    )

    @Test
    fun fallsBackToNextModelAfterRetryable503ExhaustsRetries() = runBlocking {
        var primaryCalls = 0
        var fallbackCalls = 0
        val result = AIProviderRuntime.generateWithFallback(
            models = listOf("gemini-lite-primary", "gemini-lite-secondary"),
            request = AIProviderRequest("hello"),
            adapterFactory = { model ->
                AIProviderAdapter {
                    if (model == "gemini-lite-primary") {
                        primaryCalls++
                        throw AIProviderException(
                            failure = AIProviderFailure.SERVER,
                            message = "service unavailable",
                            httpStatus = 503,
                            retryable = true
                        )
                    }
                    fallbackCalls++
                    "fallback response"
                }
            },
            retryPolicy = noWait
        )

        assertEquals("fallback response", result)
        assertEquals(2, primaryCalls)
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun doesNotFallbackForAuthenticationFailure() = runBlocking {
        var fallbackCalls = 0
        assertThrows(AIProviderException::class.java) {
            runBlocking {
                AIProviderRuntime.generateWithFallback(
                    models = listOf("primary", "secondary"),
                    request = AIProviderRequest("hello"),
                    adapterFactory = { model ->
                        AIProviderAdapter {
                            if (model == "primary") {
                                throw AIProviderException(
                                    failure = AIProviderFailure.AUTHENTICATION,
                                    message = "invalid key",
                                    httpStatus = 403,
                                    retryable = false
                                )
                            }
                            fallbackCalls++
                            "should not run"
                        }
                    },
                    retryPolicy = noWait
                )
            }
        }
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun fallsBackWhenConfiguredModelIsUnavailable() = runBlocking {
        val attemptedModels = mutableListOf<String>()
        val result = AIProviderRuntime.generateWithFallback(
            models = listOf("retired-model", "available-model"),
            request = AIProviderRequest("hello"),
            adapterFactory = { model ->
                AIProviderAdapter {
                    attemptedModels += model
                    if (model == "retired-model") {
                        throw AIProviderException(
                            failure = AIProviderFailure.MODEL_UNAVAILABLE,
                            message = "model not found",
                            httpStatus = 404,
                            retryable = false
                        )
                    }
                    "ok"
                }
            },
            retryPolicy = noWait
        )

        assertEquals("ok", result)
        assertEquals(listOf("retired-model", "available-model"), attemptedModels)
    }
    @Test
    fun retriesTransientProviderFailureAndReturnsSuccessfulResponse() = runBlocking {
        var calls = 0
        val adapter = AIProviderAdapter {
            calls++
            if (calls == 1) {
                throw AIProviderException(
                    failure = AIProviderFailure.SERVER,
                    message = "temporary server error",
                    httpStatus = 503,
                    retryable = true
                )
            }
            "response"
        }

        val result = AIProviderRuntime.generate(
            adapter,
            AIProviderRequest("hello"),
            noWait
        )

        assertEquals("response", result)
        assertEquals(2, calls)
    }

    @Test
    fun doesNotRetryAuthenticationFailure() = runBlocking {
        var calls = 0
        val adapter = AIProviderAdapter {
            calls++
            throw AIProviderException(
                failure = AIProviderFailure.AUTHENTICATION,
                message = "invalid credentials",
                httpStatus = 401,
                retryable = false
            )
        }

        assertThrows(AIProviderException::class.java) {
            runBlocking {
                AIProviderRuntime.generate(
                    adapter,
                    AIProviderRequest("hello"),
                    noWait
                )
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun rejectsBlankPromptBeforeCallingProvider() = runBlocking {
        var calls = 0
        val adapter = AIProviderAdapter {
            calls++
            "unexpected"
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                AIProviderRuntime.generate(
                    adapter,
                    AIProviderRequest("   "),
                    noWait
                )
            }
        }
        assertEquals(0, calls)
    }
    @Test
    fun cancellationIsPropagatedToProviderCall() = runBlocking {
        var started = false
        val adapter = AIProviderAdapter {
            started = true
            awaitCancellation()
        }
        val job = launch {
            AIProviderRuntime.generate(
                adapter,
                AIProviderRequest("hello"),
                noWait
            )
        }

        while (!started) delay(1)
        job.cancelAndJoin()

        assertEquals(true, job.isCancelled)
    }


    @Test
    fun overallRequestTimeoutIsClassifiedAsTimeout(): Unit = runBlocking {
        val adapter = AIProviderAdapter { awaitCancellation() }
        assertThrows(AIProviderException::class.java) {
            runBlocking {
                AIProviderRuntime.generate(
                    adapter,
                    AIProviderRequest("hello"),
                    noWait,
                    requestTimeoutMillis = 1_000L
                )
            }
        }
    }

    @Test
    fun registryExposesGeminiCapabilitiesAndRejectsMissingCredentials() {
        val descriptor = AIProviderRegistry.descriptors().single()
        assertEquals(AIProviderId.GEMINI, descriptor.id)
        assertEquals(true, descriptor.supportsImageInput)
        assertEquals(false, descriptor.supportsStreaming)

        assertThrows(IllegalArgumentException::class.java) {
            AIProviderRegistry.createAdapter(
                AIProviderConfiguration(AIProviderId.GEMINI),
                " "
            )
        }
    }

    @Test
    fun overallTimeoutDoesNotRestartForEachAttempt() = runBlocking {
        var calls = 0
        val adapter = AIProviderAdapter {
            calls++
            delay(2_000)
            "too late"
        }
        val error = assertThrows(AIProviderException::class.java) {
            runBlocking {
                AIProviderRuntime.generate(
                    adapter = adapter,
                    request = AIProviderRequest("hello"),
                    retryPolicy = AIRetryPolicy(
                        maxRetries = 4,
                        initialDelayMillis = 0,
                        maxDelayMillis = 0,
                        jitterMillis = 0,
                        maxRetryAfterMillis = 0
                    ),
                    requestTimeoutMillis = 1_000L
                )
            }
        }
        assertEquals(AIProviderFailure.TIMEOUT, error.failure)
        assertEquals(1, calls)
    }

    @Test
    fun parentTimeoutIsNotReclassifiedAsProviderTimeout(): Unit = runBlocking {
        val adapter = AIProviderAdapter { awaitCancellation() }
        assertThrows(TimeoutCancellationException::class.java) {
            runBlocking {
                withTimeout(100L) {
                    AIProviderRuntime.generate(
                        adapter,
                        AIProviderRequest("hello"),
                        noWait,
                        requestTimeoutMillis = 10_000L
                    )
                }
            }
        }
    }

    @Test
    fun retryPolicyRejectsUnboundedConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            AIRetryPolicy(maxRetries = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AIRetryPolicy(maxDelayMillis = Long.MAX_VALUE)
        }
    }

}
