package dev.syhung.cocassistant

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Portable CADSS archive: versioned JSON plus original/crop Vision Memory assets. */
object BackupManager {
    fun export(context: Context, uri: Uri, store: CadssStore) {
        val document = store.exportDocument()
        val assetNames = linkedSetOf<String>()
        listOf("vision_memory", "template_memory").forEach { collection ->
            store.getRecords(collection).forEach { record ->
                listOf("original", "crop").forEach { key ->
                    record.optString(key).takeIf { it.isNotBlank() }?.let(assetNames::add)
                }
            }
        }
        val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot open destination")
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("cadss.json"))
            zip.write(document.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            assetNames.forEach { name ->
                val file = VisionMemory.resolve(context, name) ?: return@forEach
                zip.putNextEntry(ZipEntry("vision/$name"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun restore(context: Context, uri: Uri, store: CadssStore) {
        val staging = File(context.cacheDir, "cadss-restore-${System.currentTimeMillis()}").apply { mkdirs() }
        var document: JSONObject? = null
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open backup")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) { "Unexpected directory entry" }
                    when {
                        entry.name == "cadss.json" -> {
                            val raw = zip.readBytes().toString(Charsets.UTF_8)
                            document = JSONObject(raw)
                        }
                        entry.name.startsWith("vision/") -> {
                            val name = entry.name.removePrefix("vision/")
                            require(name.isNotBlank() && '/' !in name && '\\' !in name && !name.contains("..")) {
                                "Invalid asset path"
                            }
                            File(staging, name).outputStream().use { zip.copyTo(it) }
                        }
                        else -> error("Unknown backup entry")
                    }
                    zip.closeEntry()
                }
            }
            val parsed = document ?: error("Backup manifest is missing")
            // Validate schema and collection JSON before changing local state.
            require(parsed.optString("format") == "CADSS")
            require(parsed.optInt("schemaVersion") in 1..CadssStore.SCHEMA_VERSION)
            val target = File(context.filesDir, "vision-memory").apply { mkdirs() }
            staging.listFiles()?.forEach { staged ->
                staged.copyTo(File(target, staged.name), overwrite = true)
            }
            store.importDocument(parsed)
        } finally {
            staging.deleteRecursively()
        }
    }
}
