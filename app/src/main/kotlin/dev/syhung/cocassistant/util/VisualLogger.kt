package dev.syhung.cocassistant.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VisualLogger {
    private const val TAG = "VisualLogger"
    private const val RECOVERY_DIR = "recovery_logs"
    private const val RESEARCH_DIR = "research_logs"

    fun logRecovery(context: Context, sceneName: String, bitmap: Bitmap, actionType: String) {
        saveBitmap(context, RECOVERY_DIR, sceneName, bitmap, actionType)
    }

    fun logResearch(context: Context, sceneName: String, bitmap: Bitmap, info: String) {
        saveBitmap(context, RESEARCH_DIR, sceneName, bitmap, info)
    }

    private fun saveBitmap(context: Context, folderName: String, sceneName: String, bitmap: Bitmap, info: String) {
        try {
            val directory = File(context.filesDir, folderName)
            if (!directory.exists()) directory.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${timestamp}_${sceneName}_${info}.png"
            val file = File(directory, fileName)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 80, out)
            }
            Log.d(TAG, "Đã lưu visual log: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi lưu visual log", e)
        }
    }

    fun clearAllLogs(context: Context) {
        deleteDir(File(context.filesDir, RECOVERY_DIR))
        deleteDir(File(context.filesDir, RESEARCH_DIR))
    }

    private fun deleteDir(dir: File) {
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }
}

