package dev.syhung.cocassistant.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import dev.syhung.cocassistant.SystemStatusRegistry
import dev.syhung.cocassistant.database.AppDatabase
import dev.syhung.cocassistant.database.entity.RecoveryPatternEntity
import dev.syhung.cocassistant.util.VisualLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardActivity : Activity() {

    private lateinit var patternsList: ListView
    private var patterns: List<RecoveryPatternEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(resources.getIdentifier("activity_dashboard", "layout", packageName))

        val statusText = findViewById<TextView>(resources.getIdentifier("statusText", "id", packageName))
        patternsList = findViewById<ListView>(resources.getIdentifier("patternsList", "id", packageName))
        val btnClearLogs = findViewById<Button>(resources.getIdentifier("btnClearLogs", "id", packageName))

        btnClearLogs.setOnClickListener {
            VisualLogger.clearAllLogs(this)
            Toast.makeText(this, "Logs cleared!", Toast.LENGTH_SHORT).show()
        }

        val status = SystemStatusRegistry.getStatus()
        statusText.text = "Gemini: ${status["gemini"]}\nAccessibility: ${status["accessibility"]}\nVision: ${status["vision"]}"

        patternsList.setOnItemClickListener { _, _, position, _ ->
            showPatternDialog(patterns[position])
        }

        refreshList()
    }

    private fun refreshList() {
        CoroutineScope(Dispatchers.Main).launch {
            val dao = AppDatabase.getDatabase(this@DashboardActivity).recoveryPatternDao()
            patterns = withContext(Dispatchers.IO) { dao.getAllPatterns() }
            val adapter = ArrayAdapter(
                this@DashboardActivity,
                android.R.layout.simple_list_item_1,
                patterns.map { "${it.sceneName} [${if (it.isApproved) "OK" else "Pending"}]\nArgs: ${it.jsonArgs}" }
            )
            patternsList.adapter = adapter
        }
    }

    private fun showPatternDialog(pattern: RecoveryPatternEntity) {
        val options = arrayOf(
            if (pattern.isApproved) "Unapprove" else "Approve",
            "Delete"
        )
        AlertDialog.Builder(this)
            .setTitle("Manage Pattern")
            .setItems(options) { _, which ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = AppDatabase.getDatabase(this@DashboardActivity).recoveryPatternDao()
                    when (which) {
                        0 -> {
                            val updated = pattern.copy(isApproved = !pattern.isApproved)
                            dao.update(updated)
                        }
                        1 -> dao.delete(pattern)
                    }
                    withContext(Dispatchers.Main) { refreshList() }
                }
            }.show()
    }
}

