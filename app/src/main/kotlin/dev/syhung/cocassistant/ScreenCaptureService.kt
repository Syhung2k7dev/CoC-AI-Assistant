package dev.syhung.cocassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import java.util.concurrent.ConcurrentLinkedQueue

/** Android 10 screen-capture fallback. User approval is mandatory; Accessibility still executes gestures. */
class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var worker: HandlerThread? = null
    private var handler: Handler? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCaptureForeground()
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null || code != android.app.Activity.RESULT_OK) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(code, data)
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            worker = HandlerThread("CoCScreenCapture").also { it.start() }
            handler = Handler(worker!!.looper)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            reader!!.setOnImageAvailableListener({ source ->
                val callback = callbacks.poll()
                val image = source.acquireLatestImage()
                if (callback == null) {
                    image?.close()
                    return@setOnImageAvailableListener
                }
                if (image == null) {
                    callback(null)
                    return@setOnImageAvailableListener
                }
                try {
                    val plane = image.planes[0]
                    val paddedWidth = image.width + (plane.rowStride - plane.pixelStride * image.width) / plane.pixelStride
                    val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                    plane.buffer.rewind()
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val bitmap = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                    if (bitmap !== padded) padded.recycle()
                    callback(bitmap)
                } catch (_: Exception) {
                    callback(null)
                } finally {
                    image.close()
                }
            }, handler)
            display = projection!!.createVirtualDisplay(
                "CoC-Agent-Screen", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler
            )
            projection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, handler)
            ready = true
        } catch (_: Exception) {
            ready = false
            stopSelf()
        }
        return START_STICKY
    }

    private fun startCaptureForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "CoC Agent screen capture", NotificationManager.IMPORTANCE_LOW))
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder.setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("CoC Agent đang quan sát màn hình")
            .setContentText("Chia sẻ màn hình chỉ dùng cho phiên Agent.")
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        ready = false
        display?.release()
        reader?.close()
        projection?.stop()
        worker?.quitSafely()
        display = null
        reader = null
        projection = null
        worker = null
        handler = null
        callbacks.clear()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "coc_agent_screen_capture"
        private const val NOTIFICATION_ID = 4317
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "projection_data"
        private val callbacks = ConcurrentLinkedQueue<(Bitmap?) -> Unit>()
        @Volatile private var ready = false
        fun isReady(): Boolean = ready
        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode).putExtra(EXTRA_DATA, data)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
        fun capture(callback: (Bitmap?) -> Unit): Boolean {
            if (!ready) return false
            callbacks.add(callback)
            return true
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }
    }
}
