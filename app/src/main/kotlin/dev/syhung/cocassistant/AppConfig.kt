package dev.syhung.cocassistant

import android.content.Context

/**
 * Cấu hình hệ thống tập trung (Singleton)
 */
object AppConfig {
    var geminiApiKey: String = ""
    var isEnabled: Boolean = true
    
    // Lưu các cài đặt vào SharedPrefs sau này
    fun load(context: Context) {
        val prefs = context.getSharedPreferences("coc_config", Context.MODE_PRIVATE)
        geminiApiKey = prefs.getString("api_key", "") ?: ""
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences("coc_config", Context.MODE_PRIVATE)
        prefs.edit().putString("api_key", geminiApiKey).apply()
    }
}
