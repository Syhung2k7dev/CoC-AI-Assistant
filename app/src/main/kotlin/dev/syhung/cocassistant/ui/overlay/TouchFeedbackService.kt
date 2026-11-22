package dev.syhung.cocassistant.ui.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.WindowManager
import android.widget.FrameLayout
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator

class TouchFeedbackService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var feedbackView: FeedbackView

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        feedbackView = FeedbackView(this)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(feedbackView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val x = intent?.getFloatExtra("x", 0f) ?: 0f
        val y = intent?.getFloatExtra("y", 0f) ?: 0f
        feedbackView.showTouch(x, y)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(feedbackView)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    class FeedbackView(context: android.content.Context) : View(context) {
        private var touchX = 0f
        private var touchY = 0f
        private var radius = 0f
        private var alpha = 255
        private val paint = Paint().apply {
            color = 0xFFFF0000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 10f
            isAntiAlias = true
        }

        fun showTouch(x: Float, y: Float) {
            touchX = x
            touchY = y
            val animator = ValueAnimator.ofFloat(0f, 100f)
            animator.duration = 500
            animator.interpolator = AccelerateDecelerateInterpolator()
            animator.addUpdateListener {
                radius = it.animatedValue as Float
                alpha = (255 * (1 - it.animatedFraction)).toInt()
                invalidate()
            }
            animator.start()
        }

        override fun onDraw(canvas: Canvas) {
            if (radius > 0) {
                paint.alpha = alpha
                canvas.drawCircle(touchX, touchY, radius, paint)
            }
        }
    }
}
