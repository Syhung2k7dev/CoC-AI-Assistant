package dev.syhung.cocassistant.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.syhung.cocassistant.models.Skill
import dev.syhung.cocassistant.models.LearnedSkill
import dev.syhung.cocassistant.models.WorldState
import dev.syhung.cocassistant.models.WorldLayoutAnchor
import dev.syhung.cocassistant.models.AccountProfile
import dev.syhung.cocassistant.models.ExecutionLog
import dev.syhung.cocassistant.models.ResearchRecord
import dev.syhung.cocassistant.database.entity.StateTransitionEntity
import dev.syhung.cocassistant.database.entity.TemplateEntity
import dev.syhung.cocassistant.database.entity.RecoveryPatternEntity
import dev.syhung.cocassistant.database.entity.ManualScriptEntity
import dev.syhung.cocassistant.database.entity.ManualActionEntity
import dev.syhung.cocassistant.database.dao.StateTransitionDao
import dev.syhung.cocassistant.database.dao.TemplateDao
import dev.syhung.cocassistant.database.dao.RecoveryPatternDao
import dev.syhung.cocassistant.database.dao.ManualScriptDao
import dev.syhung.cocassistant.database.dao.ManualActionDao
import dev.syhung.cocassistant.repositories.WorldStateRepository
import dev.syhung.cocassistant.repositories.AnchorRepository
import dev.syhung.cocassistant.repositories.ResearchRecordRepository

/**
 * Room Database cho CoC-AI-Assistant
 */
@TypeConverters(Converters::class)
@Database(
    entities = [
        Skill::class,
        LearnedSkill::class,
        WorldState::class,
        WorldLayoutAnchor::class,
        AccountProfile::class,
        ExecutionLog::class,
        ResearchRecord::class,
        StateTransitionEntity::class,
        TemplateEntity::class,
        RecoveryPatternEntity::class,
        ManualScriptEntity::class,
        ManualActionEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun stateTransitionDao(): StateTransitionDao
    abstract fun templateDao(): TemplateDao
    abstract fun recoveryPatternDao(): RecoveryPatternDao
    abstract fun manualScriptDao(): ManualScriptDao
    abstract fun manualActionDao(): ManualActionDao
    abstract fun worldStateRepository(): WorldStateRepository
    abstract fun anchorRepository(): AnchorRepository
    abstract fun researchRecordRepository(): ResearchRecordRepository

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = getDatabase(context)

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "coc_assistant_legacy_db"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7
                ).build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `vision_templates` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `imageData` BLOB NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `state_transitions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fromState` TEXT NOT NULL, `actionType` TEXT NOT NULL, `toState` TEXT NOT NULL, `isSuccess` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recovery_patterns ADD COLUMN isApproved INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE state_transitions ADD COLUMN skillId TEXT")
                db.execSQL("ALTER TABLE state_transitions ADD COLUMN args TEXT")
                db.execSQL("ALTER TABLE state_transitions ADD COLUMN featureDescriptor TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE manual_actions ADD COLUMN anchorId TEXT")
                db.execSQL("ALTER TABLE manual_actions ADD COLUMN offsetX REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE manual_actions ADD COLUMN offsetY REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE manual_actions ADD COLUMN duration INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE manual_actions ADD COLUMN delay INTEGER NOT NULL DEFAULT 500")
                db.execSQL("ALTER TABLE manual_actions ADD COLUMN repeatCount INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}

