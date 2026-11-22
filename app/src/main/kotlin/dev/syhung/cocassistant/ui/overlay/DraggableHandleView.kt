package dev.syhung.cocassistant.ui.overlay

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import dev.syhung.cocassistant.database.entity.ManualActionEntity

class DraggableHandleView(
    context: Context, 
    var action: ManualActionEntity, 
    val onActionChanged: (ManualActionEntity) -> Unit
) : View(context) {
    private var dX = 0f
    private var dY = 0f

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 5f
        textSize = 40f
        isAntiAlias = true
    }

    init {
        setOnClickListener {
            showEditDialog()
        }
    }

    private fun showEditDialog() {
        val layout = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL 
            setPadding(20, 20, 20, 20)
        }
        val durInput = EditText(context).apply { hint = "Duration (ms)"; setText(action.duration.toString()) }
        val delInput = EditText(context).apply { hint = "Delay (ms)"; setText(action.delay.toString()) }
        val repInput = EditText(context).apply { hint = "Repeat Count"; setText(action.repeatCount.toString()) }
        layout.addView(durInput)
        layout.addView(delInput)
        layout.addView(repInput)

        AlertDialog.Builder(context)
            .setTitle("Edit Action ${action.order}")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newAction = action.copy(
                    duration = durInput.text.toString().toLongOrNull() ?: 100,
                    delay = delInput.text.toString().toLongOrNull() ?: 500,
                    repeatCount = repInput.text.toString().toIntOrNull() ?: 1
                )
                action = newAction
                onActionChanged(newAction)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(width / 2f, height / 2f, width / 3f, paint)
        canvas.drawText(action.order.toString(), width / 2f - 10, height / 2f + 10, paint.apply { color = Color.WHITE })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dX = this.x - event.rawX
                dY = this.y - event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                this.x = event.rawX + dX
                this.y = event.rawY + dY
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (Math.abs(event.rawX + dX - x) < 10) {
                    performClick()
                } else {
                    // Update position in DB
                    val newAction = action.copy(x1 = this.x, y1 = this.y)
                    action = newAction
                    onActionChanged(newAction)
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
