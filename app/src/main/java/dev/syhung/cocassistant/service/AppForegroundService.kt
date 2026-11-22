package dev.syhung.cocassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.syhung.cocassistant.R
import dev.syhung.cocassistant.engine.CoreEngine
import dev.syhung.cocassistant.database.AppDatabase

/**
 * AppForegroundService: Đảm bảo app chạy ngầm và quản lý luồng MediaProjection.
 */
class AppForegroundService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var projectionManager: MediaProjectionManager? = null
    private var imageReader: android.media.ImageReader? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
        
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>("data")
        
        if (resultCode != 0 && resultData != null) {
            mediaProjection = projectionManager?.getMediaProjection(resultCode, resultData)
            mediaProjection?.let { setupVirtualDisplay(it) }
        }
        
        return START_STICKY
    }

    companion object {
        var latestFrame: android.graphics.Bitmap? = null
    }

    private fun setupVirtualDisplay(mediaProjection: MediaProjection) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = android.media.ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
        
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                // Convert to bitmap and store
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                
                val bitmap = android.graphics.Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height, android.graphics.Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                
                // Crop the padding
                latestFrame = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, width, height)
                
                bitmap.recycle()
                image.close()
            }
        }, null)

        mediaProjection.createVirtualDisplay(
            "CoCAssistantScreen",
            width, height, density,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "coc_assistant_channel",
            "CoC Assistant Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "coc_assistant_channel")
            .setContentTitle("CoC AI Assistant")
            .setContentText("Dịch vụ đang chạy ngầm & Sẵn sàng stream hình ảnh...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
    }
}
