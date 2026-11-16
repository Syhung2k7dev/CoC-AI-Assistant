package dev.syhung.cocassistant

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.random.Random

/**
 * Provider boundary for AI Core. Credentials stay inside the adapter and are never
 * part of Workflow/Skill payloads or persisted request metadata.
 */
internal data class AIProviderRequest(
    val prompt: String,
    val image: Bitmap? = null
)

internal enum class AIProviderFailure {
    INVALID_REQUEST,
    AUTHENTICATION,
    MODEL_UNAVAILABLE,
    RATE_LIMITED,
    SERVER,
    NETWORK,
    TIMEOUT,
    MALFORMED_RESPONSE,
    UNKNOWN
}

internal class AIProviderException(
    val failure: AIProviderFailure,
    message: String,
    val httpStatus: Int? = null,
    val retryable: Boolean = false,
    val retryAfterMillis: Long? = null,
    cause: Throwable? = null
) : Exception(message, cause)

internal data class AIRetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMillis: Long = 1_000L,
    val maxDelayMillis: Long = 8_000L,
    val jitterMillis: Long = 700L,
    val maxRetryAfterMillis: Long = 30_000L
) {
    init {
        require(maxRetries >= 0)
        require(initialDelayMillis >= 0)
        require(maxDelayMillis >= initialDelayMillis)
        require(jitterMillis >= 0)
        require(maxRetryAfterMillis >= 0)
    }
}

internal fun interface AIProviderAdapter {
    suspend fun generate(request: AIProviderRequest): String
}

/**
 * Shared connection lifecycle and bounded retry policy for provider adapters.
 * It deliberately does not silently switch providers: fallback requires an
 * explicit policy decision elsewhere in AI Core.
 */
internal object AIProviderRuntime {
    suspend fun generate(
        adapter: AIProviderAdapter,
        request: AIProviderRequest,
        retryPolicy: AIRetryPolicy = AIRetryPolicy()
    ): String {
        require(request.prompt.isNotBlank()) { "Nội dung yêu cầu chưa được nhập" }
        var retries = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                return adapter.generate(request)
            } catch (error: AIProviderException) {
                if (!error.retryable || retries >= retryPolicy.maxRetries) throw error
                delay(computeDelay(retries, error.retryAfterMillis, retryPolicy))
                retries++
            } catch (error: SocketTimeoutException) {
                if (retries >= retryPolicy.maxRetries) {
                    throw AIProviderException(
                        AIProviderFailure.TIMEOUT,
                        "Kết nối AI hết thời gian chờ.",
                        retryable = false,
                        cause = error
                    )
                }
                delay(computeDelay(retries, null, retryPolicy))
                retries++
            } catch (error: IOException) {
                if (retries >= retryPolicy.maxRetries) {
                    throw AIProviderException(
                        AIProviderFailure.NETWORK,
                        "Không thể kết nối tới dịch vụ AI.",
                        retryable = false,
                        cause = error
                    )
                }
                delay(computeDelay(retries, null, retryPolicy))
                retries++
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException("AI provider runtime exited unexpectedly")
    }

    private fun computeDelay(
        retryIndex: Int,
        retryAfterMillis: Long?,
        policy: AIRetryPolicy
    ): Long {
        if (retryAfterMillis != null) {
            return retryAfterMillis.coerceIn(0L, policy.maxRetryAfterMillis)
        }
        val multiplier = 1L shl retryIndex.coerceAtMost(20)
        val exponential = (policy.initialDelayMillis * multiplier)
            .coerceAtMost(policy.maxDelayMillis)
        val jitter = if (policy.jitterMillis == 0L) 0L
            else Random.nextLong(0L, policy.jitterMillis + 1L)
        return (exponential + jitter).coerceAtMost(policy.maxDelayMillis + policy.jitterMillis)
    }
}

/** Gemini REST adapter. The API key is held only for this request/session. */
internal class GeminiProviderAdapter(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 45_000
) : AIProviderAdapter {
    override suspend fun generate(request: AIProviderRequest): String =
        suspendCancellableCoroutine { continuation ->
            require(apiKey.isNotBlank()) { "Khóa API chưa được nhập" }
            val activeConnection = AtomicReference<HttpURLConnection?>(null)

            continuation.invokeOnCancellation {
                activeConnection.getAndSet(null)?.disconnect()
            }

            Dispatchers.IO.dispatch(continuation.context, Runnable {
                if (!continuation.isActive) return@Runnable
                var connection: HttpURLConnection? = null
                try {
                    val endpoint = URL("$BASE_URL/$model:generateContent")
                    connection = (endpoint.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = connectTimeoutMillis
                        readTimeout = readTimeoutMillis
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("x-goog-api-key", apiKey)
                    }
                    activeConnection.set(connection)
                    if (!continuation.isActive) {
                        connection.disconnect()
                        return@Runnable
                    }

                    val payload = createPayload(request)
                    connection.outputStream.use {
                        it.write(payload.toByteArray(Charsets.UTF_8))
                    }

                    val status = connection.responseCode
                    val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        .orEmpty()

                    if (status !in 200..299) {
                        throw httpFailure(status, responseBody, connection.getHeaderField("Retry-After"))
                    }

                    val result = try {
                        extractText(JSONObject(responseBody))
                    } catch (error: JSONException) {
                        throw AIProviderException(
                            AIProviderFailure.MALFORMED_RESPONSE,
                            "Phản hồi từ Gemini không đúng định dạng.",
                            cause = error
                        )
                    }
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(result))
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                } finally {
                    activeConnection.set(null)
                    connection?.disconnect()
                }
            })
        }

    private fun createPayload(request: AIProviderRequest): String {
        val parts = JSONArray().put(JSONObject().put("text", request.prompt))
        request.image?.let { bitmap ->
            val bytes = ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)
                output.toByteArray()
            }
            parts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                )
            )
        }
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .toString()
    }

    private fun extractText(root: JSONObject): String {
        val candidates = root.optJSONArray("candidates")
            ?: throw AIProviderException(
                AIProviderFailure.MALFORMED_RESPONSE,
                "Gemini không trả về danh sách kết quả."
            )
        for (candidateIndex in 0 until candidates.length()) {
            val parts = candidates.optJSONObject(candidateIndex)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: continue
            for (partIndex in 0 until parts.length()) {
                val text = parts.optJSONObject(partIndex)?.optString("text").orEmpty()
                if (text.isNotBlank()) return text
            }
        }
        throw AIProviderException(
            AIProviderFailure.MALFORMED_RESPONSE,
            "Gemini không trả về nội dung văn bản."
        )
    }

    private fun httpFailure(status: Int, body: String, retryAfter: String?): AIProviderException {
        val failure = when (status) {
            400 -> AIProviderFailure.INVALID_REQUEST
            401, 403 -> AIProviderFailure.AUTHENTICATION
            404 -> AIProviderFailure.MODEL_UNAVAILABLE
            408 -> AIProviderFailure.TIMEOUT
            429 -> AIProviderFailure.RATE_LIMITED
            in 500..599 -> AIProviderFailure.SERVER
            else -> AIProviderFailure.UNKNOWN
        }
        val retryable = status == 408 || status == 429 ||
            status == 500 || status == 502 || status == 503 || status == 504
        val retryAfterMillis = retryAfter?.trim()?.toLongOrNull()
            ?.takeIf { it >= 0L }
            ?.coerceAtMost(30L)
            ?.times(1_000L)
        val detail = body.replace(Regex("\\s+"), " ").take(240)
        return AIProviderException(
            failure = failure,
            message = if (detail.isBlank()) "Gemini trả về lỗi HTTP $status."
                else "Gemini trả về lỗi HTTP $status: $detail",
            httpStatus = status,
            retryable = retryable,
            retryAfterMillis = retryAfterMillis
        )
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        internal const val DEFAULT_MODEL = "gemini-3.8-flash"
    }
}
