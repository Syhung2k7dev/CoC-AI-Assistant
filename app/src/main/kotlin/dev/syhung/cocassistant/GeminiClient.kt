package dev.syhung.cocassistant

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {
    suspend fun generate(apiKey: String, prompt: String, image: Bitmap? = null): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Khóa API chưa được nhập" }
        require(prompt.isNotBlank()) { "Nội dung yêu cầu chưa được nhập" }
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        if (image != null) {
            val bytes = ByteArrayOutputStream().use { output ->
                image.compress(Bitmap.CompressFormat.JPEG, 86, output)
                output.toByteArray()
            }
            parts.put(JSONObject().put("inlineData", JSONObject()
                .put("mimeType", "image/jpeg")
                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))))
        }
        val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            val payload = JSONObject().put(
                "contents",
                JSONArray().put(JSONObject().put("parts", parts))
            )
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("Gemini trả về lỗi HTTP $code: ${response.take(300)}")
            val root = JSONObject(response)
            root.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                ?.optString("text")?.takeIf { it.isNotBlank() }
                ?: "Gemini không trả về nội dung văn bản."
        } finally { connection.disconnect() }
    }
}
