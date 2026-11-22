package dev.syhung.cocassistant.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.syhung.cocassistant.models.*
import dev.syhung.cocassistant.repositories.*

/**
 * Room Database cho CoC-AI-Assistant
 */
import dev.syhung.cocassistant.database.dao.TemplateDao
import dev.syhung.cocassistant.database.entity.TemplateEntity

// ...

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
        TemplateEntity::class
    ],
    version = 6,
    exportSchema = false
)
// ...

    abstract fun stateTransitionDao(): StateTransitionDao
    abstract fun templateDao(): TemplateDao
    abstract fun recoveryPatternDao(): RecoveryPatternDao
        ManualScriptEntity::class,
        ManualActionEntity::class,

    abstract fun manualScriptDao(): ManualScriptDao
// ... inside companion object
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `vision_templates` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `imageData` BLOB NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `state_transitions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fromState` TEXT NOT NULL, `actionType` TEXT NOT NULL, `toState` TEXT NOT NULL, `isSuccess` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recovery_patterns ADD COLUMN isApproved INTEGER NOT NULL DEFAULT 0")
            }
        }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `state_transitions` ADD COLUMN `skillId` TEXT")
                db.execSQL("ALTER TABLE `state_transitions` ADD COLUMN `args` TEXT")
                db.execSQL("ALTER TABLE `state_transitions` ADD COLUMN `featureDescriptor` TEXT")
            }
        }
// ... add to databaseBuilder
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)


