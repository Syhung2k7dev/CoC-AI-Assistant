package dev.syhung.cocassistant

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** CADSS backup: versioned JSON plus original/crop assets referenced by Vision Memory. */
object BackupManager {
    private val VISION_COLLECTIONS = listOf(
        "vision_memory",
        "template_memory",
        "vision_candidates",
        "vision_templates",
        "research_media"
    )

    fun export(context: Context, uri: Uri, store: CadssStore) {
        val document = store.exportDocument()
        val assetNames = collectAssetNames(document)
        val output = context.contentResolver.openOutputStream(uri) ?: error("Không thể mở vị trí lưu tệp")
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("cadss.json"))
            zip.write(document.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("research/research.json"))
            zip.write(SkillResearchDatabase(context).use { it.exportJson().toString(2).toByteArray(Charsets.UTF_8) })
            zip.closeEntry()

            var totalAssetBytes = 0L
            assetNames.forEach { name ->
                val file = VisionMemory.resolve(context, name)
                    ?: error("Thiếu Vision Memory asset trong backup: $name")
                require(file.length() <= MAX_ASSET_BYTES) { "Vision asset vượt giới hạn kích thước." }
                totalAssetBytes += file.length()
                require(totalAssetBytes <= MAX_TOTAL_ASSET_BYTES) { "Tổng Vision assets vượt giới hạn backup." }
                zip.putNextEntry(ZipEntry("vision/$name"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun restore(context: Context, uri: Uri, store: CadssStore) {
        val staging = File(context.cacheDir, "cadss-restore-${System.currentTimeMillis()}").apply {
            if (!exists() && !mkdirs()) error("Không thể tạo thư mục khôi phục tạm.")
        }
        var document: JSONObject? = null
        var researchDocument: JSONObject? = null
        val stagedNames = linkedSetOf<String>()
        var totalBytes = 0L
        var entryCount = 0
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Không thể mở tệp sao lưu")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    require(entryCount <= MAX_ENTRIES) { "Gói sao lưu có quá nhiều thành phần." }
                    require(!entry.isDirectory) { "Gói sao lưu chứa mục thư mục không hợp lệ." }
                    when {
                        entry.name == "cadss.json" -> {
                            require(document == null) { "Gói sao lưu chứa nhiều tệp cadss.json." }
                            val bytes = readBounded(zip, MAX_DOCUMENT_BYTES)
                            totalBytes += bytes.size
                            document = JSONObject(bytes.toString(Charsets.UTF_8))
                        }
                        entry.name == "research/research.json" -> {
                            require(researchDocument == null) { "Gói sao lưu chứa nhiều research database." }
                            val bytes = readBounded(zip, MAX_DOCUMENT_BYTES)
                            totalBytes += bytes.size
                            researchDocument = JSONObject(bytes.toString(Charsets.UTF_8))
                        }
                        entry.name.startsWith("vision/") -> {
                            val name = entry.name.removePrefix("vision/")
                            require(isSafeAssetName(name)) { "Tên tài nguyên không hợp lệ." }
                            require(stagedNames.add(name)) { "Gói sao lưu chứa tài nguyên trùng lặp." }
                            val target = File(staging, name)
                            val copied = target.outputStream().use { copyBounded(zip, it, MAX_ASSET_BYTES) }
                            totalBytes += copied
                        }
                        else -> error("Gói sao lưu chứa thành phần không được hỗ trợ.")
                    }
                    require(totalBytes <= MAX_TOTAL_ARCHIVE_BYTES) { "Gói sao lưu vượt giới hạn tổng kích thước." }
                    zip.closeEntry()
                }
            }

            val parsed = document ?: error("Không tìm thấy tệp mô tả CADSS trong bản sao lưu.")
            require(parsed.optString("format") == "CADSS")
            require(parsed.optInt("schemaVersion") in 1..CadssStore.SCHEMA_VERSION)
            val requiredAssets = collectAssetNames(parsed)
            require(stagedNames == requiredAssets) {
                "Bản sao lưu thiếu hoặc chứa Vision assets không được tham chiếu."
            }

            val targetDirectory = File(context.filesDir, "vision-memory")
            if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
                error("Không thể tạo thư mục Vision Memory.")
            }
            requiredAssets.forEach { name ->
                val staged = File(staging, name)
                val destination = File(targetDirectory, name)
                val temporary = File(targetDirectory, "$name.restore")
                staged.copyTo(temporary, overwrite = true)
                if (destination.exists() && !destination.delete()) {
                    temporary.delete()
                    error("Không thể thay thế Vision asset: $name")
                }
                if (!temporary.renameTo(destination)) {
                    temporary.delete()
                    error("Không thể cài đặt Vision asset: $name")
                }
            }
            store.importDocument(parsed)
            researchDocument?.let { document ->
                SkillResearchDatabase(context).use { it.importJson(document) }
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun collectAssetNames(document: JSONObject): Set<String> {
        val collections = document.optJSONObject("collections") ?: JSONObject()
        val names = linkedSetOf<String>()
        VISION_COLLECTIONS.forEach { collection ->
            val raw = collections.opt(collection) ?: return@forEach
            val records = when (raw) {
                is JSONArray -> raw
                is String -> JSONArray(raw)
                else -> throw IllegalArgumentException("Vision collection '$collection' không hợp lệ.")
            }
            for (index in 0 until records.length()) {
                val record = records.optJSONObject(index) ?: continue
                listOf("original", "crop").forEach { field ->
                    val name = record.optString(field)
                    if (name.isNotBlank()) {
                        require(isSafeAssetName(name)) { "Tên Vision asset không hợp lệ." }
                        names.add(name)
                    }
                }
            }
        }
        return names
    }

    private fun isSafeAssetName(name: String): Boolean =
        name.matches(Regex("[A-Za-z0-9-]{1,64}-(original|center)\\.jpg"))

    private fun readBounded(input: InputStream, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        copyBounded(input, output, maxBytes)
        return output.toByteArray()
    }

    private fun copyBounded(input: InputStream, output: java.io.OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            copied += count
            require(copied <= maxBytes) { "Thành phần backup vượt giới hạn kích thước." }
            output.write(buffer, 0, count)
        }
        return copied
    }

    private const val MAX_ENTRIES = 20_100
    private const val MAX_DOCUMENT_BYTES = 2_000_000L
    private const val MAX_ASSET_BYTES = 20L * 1024L * 1024L
    private const val MAX_TOTAL_ASSET_BYTES = 100L * 1024L * 1024L
    private const val MAX_TOTAL_ARCHIVE_BYTES = 102L * 1024L * 1024L
}
