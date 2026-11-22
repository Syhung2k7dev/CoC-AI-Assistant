package dev.syhung.cocassistant.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.syhung.cocassistant.models.Skill
import java.util.Date

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromLongList(value: List<Long>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toLongList(value: String?): List<Long>? {
        val listType = object : TypeToken<List<Long>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromAnchorList(value: List<Skill.Anchor>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toAnchorList(value: String?): List<Skill.Anchor>? {
        val listType = object : TypeToken<List<Skill.Anchor>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromTemplateImageList(value: List<Skill.TemplateImage>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toTemplateImageList(value: String?): List<Skill.TemplateImage>? {
        val listType = object : TypeToken<List<Skill.TemplateImage>>() {}.type
        return gson.fromJson(value, listType)
    }
}
