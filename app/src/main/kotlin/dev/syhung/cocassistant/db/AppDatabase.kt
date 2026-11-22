package dev.syhung.cocassistant.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.syhung.cocassistant.db.entity.*
import dev.syhung.cocassistant.db.dao.ContextDao
import dev.syhung.cocassistant.db.dao.ArtifactDao
import dev.syhung.cocassistant.db.dao.SkillDao
import dev.syhung.cocassistant.db.dao.LearnedSkillDao
import dev.syhung.cocassistant.db.dao.WorldStateDao
import dev.syhung.cocassistant.db.dao.WorldLayoutAnchorDao
import dev.syhung.cocassistant.db.dao.AccountProfileDao
import dev.syhung.cocassistant.db.dao.ExecutionLogDao
import dev.syhung.cocassistant.db.dao.ResearchRecordDao

@Database(entities = [
    ContextEntity::class,
    ArtifactEntity::class,
    SkillEntity::class,
    LearnedSkillEntity::class,
    WorldStateEntity::class,
    WorldLayoutAnchorEntity::class,
    AccountProfileEntity::class,
    ExecutionLogEntity::class,
    ResearchRecordEntity::class
], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contextDao(): ContextDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun skillDao(): SkillDao
    abstract fun learnedSkillDao(): LearnedSkillDao
    abstract fun worldStateDao(): WorldStateDao
    abstract fun worldLayoutAnchorDao(): WorldLayoutAnchorDao
    abstract fun accountProfileDao(): AccountProfileDao
    abstract fun executionLogDao(): ExecutionLogDao
    abstract fun researchRecordDao(): ResearchRecordDao

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
