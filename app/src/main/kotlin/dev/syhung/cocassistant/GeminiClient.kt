package dev.syhung.cocassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {
    suspend fun generate(apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "API key is empty" }
        require(prompt.isNotBlank()) { "Prompt is empty" }
        val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            val payload = JSONObject().put(
                "contents",
                JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))
            )
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("Gemini HTTP $code: ${response.take(300)}")
            val root = JSONObject(response)
            root.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                ?.optString("text")?.takeIf { it.isNotBlank() }
                ?: "Gemini không trả về nội dung văn bản."
        } finally { connection.disconnect() }
    }
}
