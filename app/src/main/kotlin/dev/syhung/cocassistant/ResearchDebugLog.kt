package dev.syhung.cocassistant

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * On-device, key-safe diagnostics for autonomous research.
 * Events are persisted in app-private storage so they remain available after CoC
 * takes the foreground and the user returns to the assistant.
 */
object ResearchDebugLog {
    data class Event(
        val time: Long,
        val stage: String,
        val message: String,
        val details: String = "",
        val imageName: String? = null
    )

    private const val MAX_EVENTS = 500
    private val lock = Any()
    private val buffer = CopyOnWriteArrayList<Event>()
    private val mutableEvents = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = mutableEvents.asStateFlow()
    @Volatile private var logFile: File? = null
    @Volatile private var activeSessionId: String = ""

    fun begin(context: Context, sessionId: String) {
        synchronized(lock) {
            buffer.clear()
            activeSessionId = sessionId
            val directory = File(context.applicationContext.filesDir, "research-debug").apply { mkdirs() }
            logFile = File(directory, "research-$sessionId.jsonl")
            mutableEvents.value = emptyList()
        }
        record("SESSION", "Bắt đầu phiên nghiên cứu", "sessionId=$sessionId")
    }

    fun record(
        stage: String,
        message: String,
        details: String = "",
        imageName: String? = null
    ) {
        val event = Event(System.currentTimeMillis(), stage.take(32), message.take(500),
            details.take(4_000), imageName?.take(160))
        synchronized(lock) {
            buffer += event
            while (buffer.size > MAX_EVENTS) buffer.removeAt(0)
            mutableEvents.value = buffer.toList()
            val json = JSONObject()
                .put("time", event.time)
                .put("stage", event.stage)
                .put("message", event.message)
                .put("details", event.details)
                .put("imageName", event.imageName ?: JSONObject.NULL)
            runCatching { logFile?.appendText(json.toString() + "\n", Charsets.UTF_8) }
        }
    }

    fun exportText(): String = synchronized(lock) {
        buildString {
            appendLine("CoC Research Debug · session=$activeSessionId")
            buffer.forEach { event ->
                appendLine("[${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(event.time))}] [${event.stage}] ${event.message}")
                if (event.details.isNotBlank()) appendLine(event.details)
                if (!event.imageName.isNullOrBlank()) appendLine("SCREENSHOT=${event.imageName}")
            }
        }
    }

    fun logFilePath(): String = logFile?.absolutePath.orEmpty()
}
