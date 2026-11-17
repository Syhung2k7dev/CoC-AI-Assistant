package dev.syhung.cocassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed class AutomationStep {
    data class Tap(val x: Float, val y: Float, val durationMs: Long = 100L) : AutomationStep()
    data class Swipe(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val durationMs: Long = 350L) : AutomationStep()
    data class Wait(val durationMs: Long) : AutomationStep()
}

/** Bounded, user-started gesture runtime. AI suggestions must be reviewed before being converted to steps. */
class CoCAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var workflow: List<AutomationStep> = emptyList()
    private var stepIndex = 0
    private var repeatIndex = 0
    private var repeatLimit = 1
    private var running = false
    private var paused = false
    private var generation = 0L
    private var pending: Runnable? = null
    @Volatile private var lastObservedPackage: String = ""

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.packageName?.toString()?.takeIf { it.isNotBlank() }?.let { lastObservedPackage = it }
    }
    override fun onInterrupt() { stopWorkflow("Dịch vụ Trợ năng bị gián đoạn.") }
    override fun onDestroy() {
        stopWorkflow(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun begin(steps: List<AutomationStep>, repeats: Int) {
        require(steps.isNotEmpty() && steps.size <= MAX_STEPS)
        require(repeats in 1..MAX_REPEATS && steps.all(::isValid))
        stopWorkflow(null)
        workflow = steps.toList()
        stepIndex = 0
        repeatIndex = 0
        repeatLimit = repeats
        running = true
        paused = false
        generation++
        Toast.makeText(this, "Bắt đầu sau 5 giây.", Toast.LENGTH_LONG).show()
        schedule(START_DELAY_MS)
    }

    private fun isValid(step: AutomationStep): Boolean = when (step) {
        is AutomationStep.Tap -> step.x.isFinite() && step.y.isFinite() &&
            step.x in 0f..20_000f && step.y in 0f..20_000f && step.durationMs in 50L..10_000L
        is AutomationStep.Swipe -> listOf(step.startX, step.startY, step.endX, step.endY)
            .all { it.isFinite() && it in 0f..20_000f } && step.durationMs in 50L..10_000L
        is AutomationStep.Wait -> step.durationMs in 100L..60_000L
    }

    private fun executeNext() {
        if (!running || paused) return
        if (stepIndex >= workflow.size) {
            repeatIndex++
            if (repeatIndex >= repeatLimit) { stopWorkflow("Đã hoàn thành workflow."); return }
            stepIndex = 0
            schedule(STEP_GAP_MS)
            return
        }
        when (val step = workflow[stepIndex]) {
            is AutomationStep.Wait -> { stepIndex++; schedule(step.durationMs) }
            is AutomationStep.Tap -> dispatch(step.x, step.y, step.x, step.y, step.durationMs)
            is AutomationStep.Swipe -> dispatch(step.startX, step.startY, step.endX, step.endY, step.durationMs)
        }
    }

    private fun dispatch(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        val metrics = resources.displayMetrics
        if (x1 !in 0f..metrics.widthPixels.toFloat() || x2 !in 0f..metrics.widthPixels.toFloat() ||
            y1 !in 0f..metrics.heightPixels.toFloat() || y2 !in 0f..metrics.heightPixels.toFloat()) {
            stopWorkflow("Tọa độ ngoài màn hình. Workflow đã dừng.")
            return
        }
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration)).build()
        val token = generation
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (!running || token != generation) return
                stepIndex++
                schedule(STEP_GAP_MS)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (running && token == generation) stopWorkflow("Android đã hủy thao tác. Workflow đã dừng.")
            }
        }, handler)
        if (!accepted) stopWorkflow("Android từ chối thao tác. Workflow đã dừng.")
    }

    private fun schedule(delayMs: Long) {
        pending?.let(handler::removeCallbacks)
        val task = Runnable { pending = null; executeNext() }
        pending = task
        handler.postDelayed(task, delayMs)
    }

    private fun stopWorkflow(message: String?) {
        generation++
        pending?.let(handler::removeCallbacks)
        pending = null
        running = false
        paused = false
        workflow = emptyList()
        stepIndex = 0
        repeatIndex = 0
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun pauseOrResumeWorkflow(): Boolean {
        if (!running) return false
        paused = !paused
        if (paused) {
            pending?.let(handler::removeCallbacks); pending = null
            Toast.makeText(this, "Đã tạm dừng workflow.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Tiếp tục workflow.", Toast.LENGTH_SHORT).show()
            schedule(STEP_GAP_MS)
        }
        return true
    }


    private suspend fun dispatchSingleStep(step: AutomationStep): Boolean =
        suspendCancellableCoroutine { continuation ->
            handler.post {
                if (!continuation.isActive) return@post
                if (!isValid(step)) {
                    continuation.resume(false)
                    return@post
                }
                if (step is AutomationStep.Wait) {
                    val waitTask = Runnable { if (continuation.isActive) continuation.resume(true) }
                    handler.postDelayed(waitTask, step.durationMs)
                    continuation.invokeOnCancellation { handler.removeCallbacks(waitTask) }
                    return@post
                }
                val metrics = resources.displayMetrics
                val startX: Float
                val startY: Float
                val endX: Float
                val endY: Float
                val duration: Long
                when (step) {
                    is AutomationStep.Tap -> {
                        startX = step.x; startY = step.y; endX = step.x; endY = step.y
                        duration = step.durationMs
                    }
                    is AutomationStep.Swipe -> {
                        startX = step.startX; startY = step.startY; endX = step.endX; endY = step.endY
                        duration = step.durationMs
                    }
                    is AutomationStep.Wait -> return@post
                }
                if (startX !in 0f..metrics.widthPixels.toFloat() ||
                    endX !in 0f..metrics.widthPixels.toFloat() ||
                    startY !in 0f..metrics.heightPixels.toFloat() ||
                    endY !in 0f..metrics.heightPixels.toFloat()) {
                    continuation.resume(false)
                    return@post
                }
                val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0L, duration)).build()
                val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }, handler)
                if (!accepted && continuation.isActive) continuation.resume(false)
            }
        }

    private fun capture(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { callback(null); return }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Exception) { null } finally { buffer.close() }
                    callback(bitmap)
                }
                override fun onFailure(errorCode: Int) { callback(null) }
            })
        } catch (_: Exception) { callback(null) }
    }

    companion object {
        private const val MAX_STEPS = 50
        private const val MAX_REPEATS = 20
        private const val START_DELAY_MS = 5_000L
        private const val STEP_GAP_MS = 250L
        @Volatile private var instance: CoCAccessibilityService? = null
        fun isConnected(): Boolean = instance != null
        fun isGameForeground(packageName: String): Boolean {
            val service = instance ?: return false
            val rootPackage = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull()
            return rootPackage == packageName || service.lastObservedPackage == packageName
        }
        suspend fun performSingleStep(step: AutomationStep): Boolean = instance?.dispatchSingleStep(step) ?: false
        fun startWorkflow(context: Context, steps: List<AutomationStep>, repeats: Int = 1): Boolean {
            val service = instance ?: return false
            return try { service.begin(steps, repeats); true } catch (_: IllegalArgumentException) { false }
        }
        fun pauseOrResume(): Boolean = instance?.pauseOrResumeWorkflow() ?: false
        fun stop() { instance?.stopWorkflow("Đã dừng workflow theo yêu cầu.") }
        fun captureScreen(callback: (Bitmap?) -> Unit): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                service.capture(callback)
                return true
            }
            return ScreenCaptureService.capture(callback)
        }
    }
}
