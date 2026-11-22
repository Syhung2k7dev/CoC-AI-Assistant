package dev.syhung.cocassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import dev.syhung.cocassistant.database.AppDatabase
import dev.syhung.cocassistant.engine.CoreEngine
import dev.syhung.cocassistant.engine.GesturePerformer

import dev.syhung.cocassistant.service.AppForegroundService
/**
 * CoCAccessibilityService - Service điều khiển thao tác trong game.
 */
class CoCAccessibilityService : AccessibilityService(), GesturePerformer {

    private lateinit var coreEngine: CoreEngine

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        // Khởi tạo CoreEngine khi service kết nối
        val db = AppDatabase.getDatabase(this) 
        coreEngine = CoreEngine(db, this)
        
        // Bootstrap: Cân chỉnh tỉ lệ màn hình
        val metrics = resources.displayMetrics
        coreEngine.action.calibrate(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
    }

    override fun dispatchGesture(gesture: GestureDescription, callback: (Boolean) -> Unit) {
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback(false)
            }
        }, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Xử lý các sự kiện nếu cần
    }

    override fun captureScreenshot(callback: (Bitmap?) -> Unit) {
        // Try MediaProjection first
        val mediaFrame = AppForegroundService.latestFrame
        if (mediaFrame != null && !mediaFrame.isRecycled) {
            callback(mediaFrame.copy(Bitmap.Config.ARGB_8888, false))
            return
        }
        // Fallback to accessibility
        captureScreenshotWithRetry(callback, 3)
    }

    private fun captureScreenshotWithRetry(callback: (Bitmap?) -> Unit, retries: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            captureScreenshotApi30 { bitmap ->
                if (bitmap != null) {
                    callback(bitmap)
                } else if (retries > 0) {
                    Log.w("CoCAccessibilityService", "Screenshot failed, retrying... ($retries left)")
                    // Delay before retry
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        captureScreenshotWithRetry(callback, retries - 1)
                    }, 500)
                } else {
                    callback(null)
                }
            }
        } else {
            captureScreenshotLegacy { bitmap ->
                if (bitmap != null) {
                    callback(bitmap)
                } else if (retries > 0) {
                    Log.w("CoCAccessibilityService", "Legacy screenshot failed, retrying... ($retries left)")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        captureScreenshotWithRetry(callback, retries - 1)
                    }, 500)
                } else {
                    callback(null)
                }
            }
        }
    }

    private fun captureScreenshotLegacy(callback: (Bitmap?) -> Unit) {
        try {
            val method = AccessibilityService::class.java.getMethod(
                "takeScreenshot",
                Int::class.javaPrimitiveType,
                java.util.concurrent.Executor::class.java,
                AccessibilityService.TakeScreenshotCallback::class.java
            )

            // Dùng reflection để tạo interface callback cho API cũ
            // Trên Android 9/10, TakeScreenshotCallback có onSuccess(Bitmap)
            val callbackProxy = java.lang.reflect.Proxy.newProxyInstance(
                AccessibilityService.TakeScreenshotCallback::class.java.classLoader,
                arrayOf(AccessibilityService.TakeScreenshotCallback::class.java)
            ) { _, method, args ->
                if (method.name == "onSuccess") {
                    // Đối số đầu tiên là Bitmap
                    callback(args[0] as Bitmap)
                } else if (method.name == "onFailure") {
                    callback(null)
                }
                null
            }

            method.invoke(this, Display.DEFAULT_DISPLAY, mainExecutor, callbackProxy)
        } catch (e: Exception) {
            e.printStackTrace()
            callback(null)
        }
    }

    // Hàm này chỉ được gọi trên Android 11+, nên an toàn với các class mới
    private fun captureScreenshotApi30(callback: (Bitmap?) -> Unit) {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val hardwareBuffer = screenshot.hardwareBuffer
                    val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                    // Copy to software-backed bitmap to avoid issues after closing HardwareBuffer
                    val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    bitmap?.recycle() // Free the hardware-backed bitmap
                    hardwareBuffer?.close()
                    callback(softwareBitmap)
                }

                override fun onFailure(errorCode: Int) {
                    Log.e("CoCAccessibilityService", "Screenshot failed with error code: $errorCode")
                    callback(null)
                }
            }
        )
    }

    override fun onInterrupt() {
        // Xử lý khi service bị ngắt
    }
}

