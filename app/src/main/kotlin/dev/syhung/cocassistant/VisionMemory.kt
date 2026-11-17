package dev.syhung.cocassistant

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** Stores original and center-crop assets in app-private storage; CADSS stores references only. */
object VisionMemory {
    fun save(context: Context, bitmap: Bitmap, hash: String): JSONObject {
        require(!bitmap.isRecycled && bitmap.width in 1..8_192 && bitmap.height in 1..8_192)
        require(bitmap.width.toLong() * bitmap.height.toLong() <= MAX_PIXELS)
        require(hash.matches(Regex("[0-9a-fA-F]{16}"))) { "pHash không hợp lệ." }

        val directory = File(context.applicationContext.filesDir, DIRECTORY).apply {
            if (!exists() && !mkdirs()) throw IOException("Không thể tạo thư mục Vision Memory.")
        }
        val id = UUID.randomUUID().toString()
        val original = File(directory, "$id-original.jpg")
        val cropFile = File(directory, "$id-center.jpg")
        var originalWritten = false
        var cropWritten = false
        try {
            originalWritten = original.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
            if (!originalWritten) throw IOException("Không thể lưu ảnh gốc.")
            val side = minOf(bitmap.width, bitmap.height)
            val left = (bitmap.width - side) / 2
            val top = (bitmap.height - side) / 2
            val center = Bitmap.createBitmap(bitmap, left, top, side, side)
            try {
                cropWritten = cropFile.outputStream().use {
                    center.compress(Bitmap.CompressFormat.JPEG, 88, it)
                }
            } finally {
                if (center !== bitmap) center.recycle()
            }
            if (!cropWritten) throw IOException("Không thể lưu ảnh crop.")
            return JSONObject()
                .put("id", id)
                .put("hash", hash.lowercase())
                .put("original", original.name)
                .put("crop", cropFile.name)
                .put("width", bitmap.width)
                .put("height", bitmap.height)
                .put("createdAt", System.currentTimeMillis())
                .put("label", "Ảnh đã phân tích")
        } catch (error: Exception) {
            if (originalWritten) original.delete()
            if (cropWritten) cropFile.delete()
            throw error
        }
    }

    fun resolve(context: Context, fileName: String): File? {
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return null
        val root = File(context.applicationContext.filesDir, DIRECTORY).canonicalFile
        val file = File(root, fileName).canonicalFile
        return if (file.parentFile == root && file.isFile) file else null
    }

    fun delete(context: Context, fileName: String): Boolean =
        resolve(context, fileName)?.delete() ?: false

    fun deleteAssets(context: Context, record: JSONObject) {
        delete(context, record.optString("original"))
        delete(context, record.optString("crop"))
    }

    private const val DIRECTORY = "vision-memory"
    private const val MAX_PIXELS = 32_000_000L
}
