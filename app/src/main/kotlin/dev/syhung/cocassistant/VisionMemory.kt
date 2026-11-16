package dev.syhung.cocassistant

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import java.io.File

/** Keeps original and center-crop assets in app-private storage; CADSS stores their references. */
object VisionMemory {
    fun save(context: Context, bitmap: Bitmap, hash: String): JSONObject {
        val directory = File(context.filesDir, "vision-memory").apply { mkdirs() }
        val id = System.currentTimeMillis().toString()
        val original = File(directory, "$id-original.jpg")
        val cropFile = File(directory, "$id-center.jpg")
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val side = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        val center = Bitmap.createBitmap(bitmap, left, top, side, side)
        try {
            cropFile.outputStream().use { center.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        } finally { if (center !== bitmap) center.recycle() }
        return JSONObject()
            .put("id", id)
            .put("hash", hash)
            .put("original", original.name)
            .put("crop", cropFile.name)
            .put("width", bitmap.width)
            .put("height", bitmap.height)
            .put("createdAt", System.currentTimeMillis())
            .put("label", "Ảnh đã phân tích")
    }

    fun resolve(context: Context, fileName: String): File? {
        val root = File(context.filesDir, "vision-memory").canonicalFile
        val file = File(root, fileName).canonicalFile
        return if (file.parentFile == root && file.exists()) file else null
    }
}
