package dev.syhung.cocassistant.database

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.syhung.cocassistant.database.entity.TemplateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Quản lý Template cho Vision Engine
 */
class TemplateManager(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val dao = db.templateDao()

    suspend fun saveTemplate(id: String, label: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        dao.insert(TemplateEntity(id, label, byteArray))
    }

    suspend fun getAllTemplates(): Map<String, Bitmap> = withContext(Dispatchers.IO) {
        dao.getAll().associate { entity ->
            val bitmap = BitmapFactory.decodeByteArray(entity.imageData, 0, entity.imageData.size)
            entity.id to bitmap
        }
    }

    suspend fun deleteTemplate(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }
}