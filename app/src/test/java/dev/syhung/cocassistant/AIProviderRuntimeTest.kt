package dev.syhung.cocassistant

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
}
