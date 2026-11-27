package dev.syhung.cocassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * User-consented MediaProjection foreground service.
 * Frames are copied under a lock and all projection resources are released on stop.
 */
class AppForegroundService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var projectionManager: MediaProjectionManager? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            releaseProjectionResources(stopProjection = false)
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeeded()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != android.app.Activity.RESULT_OK || resultData == null) {
            Log.w(TAG, "MediaProjection consent data missing; stopping service.")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        return try {
            releaseProjectionResources(stopProjection = true)
            val projection = projectionManager?.getMediaProjection(resultCode, resultData)
                ?: error("Android không cấp MediaProjection.")
            mediaProjection = projection
            projection.registerCallback(projectionCallback, mainHandler)
            setupVirtualDisplay(projection)
            START_NOT_STICKY
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start MediaProjection.", error)
            releaseProjectionResources(stopProjection = true)
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    private fun setupVirtualDisplay(projection: MediaProjection) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        require(width > 0 && height > 0) { "Kích thước màn hình không hợp lệ." }

        val reader = ImageReader.newInstance(
            width,
            height,
            android.graphics.PixelFormat.RGBA_8888,
            2
        )
        imageReader = reader
        reader.setOnImageAvailableListener({ source ->
            val image = try {
                source.acquireLatestImage()
            } catch (error: Exception) {
                Log.w(TAG, "Unable to acquire projection image.", error)
                null
            } ?: return@setOnImageAvailableListener

            var paddedBitmap: Bitmap? = null
            try {
                val plane = image.planes.firstOrNull() ?: return@setOnImageAvailableListener
                val pixelStride = plane.pixelStride
                if (pixelStride <= 0) return@setOnImageAvailableListener
                val rowPadding = plane.rowStride - pixelStride * width
                val paddedWidth = width + (rowPadding / pixelStride)
                paddedBitmap = Bitmap.createBitmap(
                    paddedWidth,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                paddedBitmap.copyPixelsFromBuffer(plane.buffer)
                val frame = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
                publishFrame(frame)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to convert projection image.", error)
            } finally {
                paddedBitmap?.takeIf { !it.isRecycled }?.recycle()
                image.close()
            }
        }, mainHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "CoCAssistantScreen",
            width,
            height,
            metrics.densityDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            mainHandler
        ) ?: error("Không tạo được VirtualDisplay.")
    }

    override fun onDestroy() {
        releaseProjectionResources(stopProjection = true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun releaseProjectionResources(stopProjection: Boolean) {
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null

        val projection = mediaProjection
        mediaProjection = null
        if (projection != null) {
            runCatching { projection.unregisterCallback(projectionCallback) }
            if (stopProjection) runCatching { projection.stop() }
        }
        clearLatestFrame()
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "CoC Assistant Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CoC AI Assistant")
            .setContentText("Chụp màn hình chỉ hoạt động trong phiên đã được bạn cấp quyền.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()

    private fun publishFrame(frame: Bitmap) {
        synchronized(frameLock) {
            val previous = latestFrame
            latestFrame = frame
            if (previous != null && !previous.isRecycled) previous.recycle()
        }
    }

    private fun clearLatestFrame() {
        synchronized(frameLock) {
            val previous = latestFrame
            latestFrame = null
            if (previous != null && !previous.isRecycled) previous.recycle()
        }
    }

    companion object {
        private const val TAG = "AppForegroundService"
        private const val CHANNEL_ID = "coc_assistant_channel"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "data"

        private val frameLock = Any()
        private var latestFrame: Bitmap? = null

        @JvmStatic
        fun copyLatestFrame(): Bitmap? = synchronized(frameLock) {
            latestFrame?.takeIf { !it.isRecycled }?.copy(Bitmap.Config.ARGB_8888, false)
        }
    }
}
