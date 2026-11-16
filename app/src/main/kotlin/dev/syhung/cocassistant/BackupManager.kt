package dev.syhung.cocassistant

import android.content.Context
import android.net.Uri
import org.json.JSONObject

object BackupManager {
    fun export(context: Context, uri: Uri, store: CadssStore) {
        val document = store.exportDocument()
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(document.toString(2))
        } ?: error("Cannot open destination")
    }

    fun restore(context: Context, uri: Uri, store: CadssStore) {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Cannot open backup")
        store.importDocument(JSONObject(raw))
    }
}
