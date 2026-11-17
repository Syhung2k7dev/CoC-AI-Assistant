package dev.syhung.cocassistant

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

}
