package dev.syhung.cocassistant

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
        require(maxRetries in 0..8) { "Số lần retry phải từ 0 đến 8." }
        require(initialDelayMillis in 0L..30_000L)
        require(maxDelayMillis in initialDelayMillis..120_000L)
        require(jitterMillis in 0L..10_000L)
        require(maxRetryAfterMillis in 0L..120_000L)
    }
}

internal fun interface AIProviderAdapter {
    suspend fun generate(request: AIProviderRequest): String
}

/**
 * Shared connection lifecycle and bounded retry policy for provider adapters.
 * One timeout covers the complete attempt/retry sequence.
 */
internal object AIProviderRuntime {
    suspend fun generate(
        adapter: AIProviderAdapter,
        request: AIProviderRequest,
        retryPolicy: AIRetryPolicy = AIRetryPolicy(),
        requestTimeoutMillis: Long = 60_000L
    ): String {
        require(request.prompt.isNotBlank()) { "Nội dung yêu cầu chưa được nhập" }
        require(requestTimeoutMillis in 1_000L..300_000L) { "Request timeout không hợp lệ." }
        val response = withTimeoutOrNull(requestTimeoutMillis) {
            var retries = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                try {
                    return@withTimeoutOrNull adapter.generate(request)
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
            throw IllegalStateException("AI provider retry loop exited unexpectedly")
        }
        return response ?: throw AIProviderException(
            AIProviderFailure.TIMEOUT,
            "Yêu cầu AI vượt quá thời gian chờ tổng thể.",
            retryable = false
        )
    }

    /**
     * Tries the configured Gemini model sequence. Each model gets its own bounded
     * retry cycle; only transient server failures and unavailable model IDs advance
     * to the next model. Other failures are surfaced without changing the request target.
     */
    suspend fun generateWithFallback(
        models: List<String>,
        request: AIProviderRequest,
        adapterFactory: (String) -> AIProviderAdapter,
        retryPolicy: AIRetryPolicy = AIRetryPolicy(),
        requestTimeoutMillis: Long = 60_000L
    ): String {
        val candidates = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(candidates.isNotEmpty()) { "Danh sách Gemini model dự phòng đang trống." }

        for ((index, model) in candidates.withIndex()) {
            try {
                return generate(
                    adapter = adapterFactory(model),
                    request = request,
                    retryPolicy = retryPolicy,
                    requestTimeoutMillis = requestTimeoutMillis
                )
            } catch (error: AIProviderException) {
                val hasFallback = index < candidates.lastIndex
                val fallbackEligible = error.failure == AIProviderFailure.MODEL_UNAVAILABLE ||
                    (error.failure == AIProviderFailure.SERVER &&
                        (error.httpStatus == 500 || error.httpStatus == 502 ||
                            error.httpStatus == 503 || error.httpStatus == 504))
                if (!hasFallback || !fallbackEligible) throw error
            }
        }
        throw AIProviderException(
            AIProviderFailure.UNKNOWN,
            "Không có Gemini model nào khả dụng.",
            retryable = false
        )
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
    override suspend fun generate(request: AIProviderRequest): String {
        require(apiKey.isNotBlank()) { "Khóa API chưa được nhập" }
        require(connectTimeoutMillis in 1_000..120_000) { "Connect timeout không hợp lệ." }
        require(readTimeoutMillis in 1_000..180_000) { "Read timeout không hợp lệ." }
        val requestStartedAt = android.os.SystemClock.elapsedRealtime()
        ResearchDebugLog.record("HTTP", "Chuẩn bị Gemini HTTP request",
            "model=$model\npromptCharacters=${request.prompt.length}\nimage=${request.image?.let { "${it.width}x${it.height} px" } ?: "none"}\nconnectTimeoutMs=$connectTimeoutMillis\nreadTimeoutMs=$readTimeoutMillis")
        return suspendCancellableCoroutine { continuation ->
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
                    val payloadBytes = payload.toByteArray(Charsets.UTF_8)
                    ResearchDebugLog.record("HTTP_UPLOAD", "Đang tải request lên Gemini",
                        "model=$model\npayloadBytes=${payloadBytes.size}\nimageIncluded=${request.image != null}")
                    val uploadStartedAt = android.os.SystemClock.elapsedRealtime()
                    connection.outputStream.use { it.write(payloadBytes) }
                    ResearchDebugLog.record("HTTP_UPLOAD", "Đã gửi payload request",
                        "model=$model\nuploadWriteMs=${android.os.SystemClock.elapsedRealtime() - uploadStartedAt}\npayloadBytes=${payloadBytes.size}")

                    val status = connection.responseCode
                    ResearchDebugLog.record("HTTP_RESPONSE", "Gemini trả HTTP $status",
                        "model=$model\nstatus=$status\nrequestElapsedMs=${android.os.SystemClock.elapsedRealtime() - requestStartedAt}")
                    val responseBody = if (status in 200..299) {
                        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } else {
                        ""
                    }

                    if (status !in 200..299) {
                        val failure = httpFailure(status, connection.getHeaderField("Retry-After"))
                        ResearchDebugLog.record("HTTP_ERROR", "Gemini HTTP error $status",
                            "model=$model\nfailure=${failure.failure}\nretryable=${failure.retryable}\nmessage=${failure.message}")
                        throw failure
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
                    ResearchDebugLog.record("HTTP_SUCCESS", "Gemini response body đã được phân tích",
                        "model=$model\nrequestElapsedMs=${android.os.SystemClock.elapsedRealtime() - requestStartedAt}\nresponseCharacters=${result.length}")
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(result))
                    }
                } catch (error: Exception) {
                    ResearchDebugLog.record("HTTP_EXCEPTION", "Lỗi trong Gemini HTTP adapter",
                        "model=$model\nelapsedMs=${android.os.SystemClock.elapsedRealtime() - requestStartedAt}\n${error.javaClass.simpleName}: ${error.message}")
                    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                } finally {
                    activeConnection.set(null)
                    connection?.disconnect()
                }
            })
        }
    }

    private fun createPayload(request: AIProviderRequest): String {
        val parts = JSONArray().put(JSONObject().put("text", request.prompt))
        request.image?.let { bitmap ->
            // Keep the original full-resolution frame in Vision Memory, but send a
            // bounded image to Gemini to reduce upload time and request size.
            val longestEdge = maxOf(bitmap.width, bitmap.height)
            val scale = minOf(1f, 1_536f / longestEdge.toFloat())
            val uploadBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap
            val uploadWidth = uploadBitmap.width
            val uploadHeight = uploadBitmap.height
            val bytes = try {
                ByteArrayOutputStream().use { output ->
                    uploadBitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
                    output.toByteArray()
                }
            } finally {
                if (uploadBitmap !== bitmap && !uploadBitmap.isRecycled) uploadBitmap.recycle()
            }
            ResearchDebugLog.record("IMAGE_ENCODING", "Đã mã hóa ảnh gửi Gemini",
                "source=${bitmap.width}x${bitmap.height}\nupload=${uploadWidth}x${uploadHeight}\nquality=80\nimageBytes=${bytes.size}\nbase64Bytes~=${bytes.size * 4L / 3L}")
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

    private fun httpFailure(status: Int, retryAfter: String?): AIProviderException {
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
        return AIProviderException(
            failure = failure,
            message = when (status) {
                401, 403 -> "Thông tin xác thực Gemini không hợp lệ hoặc không đủ quyền."
                404 -> "Model Gemini không khả dụng."
                429 -> "Gemini đang giới hạn tốc độ yêu cầu."
                in 500..599 -> "Dịch vụ Gemini đang gặp sự cố tạm thời."
                else -> "Gemini trả về lỗi HTTP $status."
            },
            httpStatus = status,
            retryable = retryable,
            retryAfterMillis = retryAfterMillis
        )
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        val FALLBACK_MODELS = listOf(
            DEFAULT_MODEL,
            "gemini-3.1-flash-lite",
            "gemini-3.8-flash"
        )
    }
}
