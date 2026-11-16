package dev.syhung.cocassistant

import android.app.Activity
import android.os.Bundle
import android.os.CountDownTimer
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.Context
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var timerLabel: TextView
    private lateinit var noteInput: EditText
    private var countdown: CountDownTimer? = null
    private var remainingMillis = 0L

    private val prefs by lazy {
        getSharedPreferences("companion_preferences", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        remainingMillis = savedInstanceState?.getLong("remaining") ?: 0L
        buildScreen()
        noteInput.setText(prefs.getString("notes", ""))
        updateTimerLabel()
        if (remainingMillis > 0L) startCountdown(remainingMillis)
    }

    private fun buildScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(0xFFF5F6FA.toInt())
        }

        val scroll = ScrollView(this)
        scroll.addView(page)

        page.addView(TextView(this).apply {
            text = "CoC Companion"
            textSize = 28f
            setTextColor(0xFF202536.toInt())
            typeface = Typeface.DEFAULT_BOLD
        })

        page.addView(TextView(this).apply {
            text = "Bảng theo dõi cá nhân • Android"
            textSize = 14f
            setTextColor(0xFF667085.toInt())
            setPadding(0, dp(6), 0, dp(22))
        })

        sectionTitle(page, "Bộ đếm thời gian")
        timerLabel = TextView(this).apply {
            textSize = 34f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setTextColor(0xFF25304A.toInt())
            setPadding(dp(12), dp(20), dp(12), dp(20))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        page.addView(timerLabel, matchWidth())

        val timerButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        timerButtons.addView(actionButton("30 phút") { startCountdown(30 * 60 * 1000L) }, weighted())
        timerButtons.addView(actionButton("1 giờ") { startCountdown(60 * 60 * 1000L) }, weighted())
        timerButtons.addView(actionButton("Dừng") { stopCountdown() }, weighted())
        page.addView(timerButtons)

        sectionTitle(page, "Ghi chú")
        noteInput = EditText(this).apply {
            hint = "Lưu mục tiêu hoặc ghi chú của bạn..."
            minLines = 4
            gravity = Gravity.TOP
            textSize = 15f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        page.addView(noteInput, matchWidth())

        page.addView(actionButton("Lưu ghi chú") {
            prefs.edit().putString("notes", noteInput.text.toString()).apply()
            showMessage("Đã lưu ghi chú trên thiết bị.")
        }, matchWidth())

        sectionTitle(page, "Phạm vi ứng dụng")
        page.addView(TextView(this).apply {
            text = "Phiên bản đầu tập trung vào ghi chú và bộ đếm thủ công. Ứng dụng không tự chạm, không điều khiển trò chơi, không chuyển tài khoản và không thu thập thông tin đăng nhập."
            textSize = 14f
            setTextColor(0xFF475467.toInt())
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(0xFFFFFFFF.toInt())
        })

        setContentView(scroll)
    }

    private fun sectionTitle(parent: LinearLayout, value: String) {
        parent.addView(TextView(this).apply {
            text = value
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF202536.toInt())
            setPadding(0, dp(22), 0, dp(10))
        })
    }

    private fun actionButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }
    }

    private fun startCountdown(duration: Long) {
        countdown?.cancel()
        remainingMillis = duration
        countdown = object : CountDownTimer(duration, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                updateTimerLabel()
            }

            override fun onFinish() {
                remainingMillis = 0L
                updateTimerLabel()
                showMessage("Bộ đếm đã kết thúc.")
            }
        }.start()
    }

    private fun stopCountdown() {
        countdown?.cancel()
        countdown = null
        remainingMillis = 0L
        updateTimerLabel()
    }

    private fun updateTimerLabel() {
        val totalSeconds = remainingMillis / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        timerLabel.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("remaining", remainingMillis)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        countdown?.cancel()
        super.onDestroy()
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun weighted() = LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        1f
    )
}
