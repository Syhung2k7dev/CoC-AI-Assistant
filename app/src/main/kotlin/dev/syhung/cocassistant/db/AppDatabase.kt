package dev.syhung.cocassistant.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.syhung.cocassistant.db.entity.*
import dev.syhung.cocassistant.db.dao.ContextDao
import dev.syhung.cocassistant.db.dao.ArtifactDao

@Database(entities = [
    ContextEntity::class,
    ArtifactEntity::class
], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contextDao(): ContextDao
    abstract fun artifactDao(): ArtifactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "coc_assistant_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
