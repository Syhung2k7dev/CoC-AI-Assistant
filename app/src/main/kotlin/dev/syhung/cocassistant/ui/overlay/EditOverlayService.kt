package dev.syhung.cocassistant.ui.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import dev.syhung.cocassistant.database.AppDatabase
import dev.syhung.cocassistant.database.dao.ManualScriptDao
import dev.syhung.cocassistant.database.entity.ManualActionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EditOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private val handles = mutableListOf<DraggableHandleView>()
    private lateinit var dao: ManualScriptDao
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        dao = AppDatabase.getInstance(this).manualScriptDao()
    }

    fun addHandle(action: ManualActionEntity) {
        val handle = DraggableHandleView(this, action) { updatedAction ->
            scope.launch {
                dao.updateAction(updatedAction)
            }
        }
        
        val params = WindowManager.LayoutParams(
            200, 200,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = action.x1.toInt()
        params.y = action.y1.toInt()
        
        windowManager.addView(handle, params)
        handles.add(handle)
    }

    override fun onDestroy() {
        super.onDestroy()
        handles.forEach { windowManager.removeView(it) }
        handles.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

