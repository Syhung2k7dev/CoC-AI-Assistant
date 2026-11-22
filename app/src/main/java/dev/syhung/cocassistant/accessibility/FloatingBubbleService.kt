package dev.syhung.cocassistant.accessibility

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp

/**
 * FloatingBubbleService - Dịch vụ hiển thị bong bóng nổi để điều khiển Agent.
 */
class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: ComposeView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = ComposeView(this)
        
        // Cấu hình params cho bong bóng
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        
        windowManager.addView(floatingView, params)
        
        // Thiết lập Compose Content (nút bấm điều khiển)
        floatingView.setContent {
            Button(onClick = {
                // Logic dừng bot: Gửi lệnh dừng đến AppForegroundService hoặc Controller
                // Tạm thời dừng Service này
                stopSelf()
            }, modifier = Modifier.padding(8.dp)) {
                Text("STOP BOT")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(floatingView)
    }
}
