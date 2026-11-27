package dev.syhung.cocassistant.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlatformSQLiteDatabase
import android.util.Log
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
import dev.syhung.cocassistant.database.entity.ContextEntity
import dev.syhung.cocassistant.database.entity.AgentExecutionLogEntity
import dev.syhung.cocassistant.database.dao.StateTransitionDao
import dev.syhung.cocassistant.database.dao.TemplateDao
import dev.syhung.cocassistant.database.dao.RecoveryPatternDao
import dev.syhung.cocassistant.database.dao.ManualScriptDao
import dev.syhung.cocassistant.database.dao.ManualActionDao
import dev.syhung.cocassistant.database.dao.ContextDao
import dev.syhung.cocassistant.database.dao.AgentExecutionLogDao
import dev.syhung.cocassistant.repositories.WorldStateRepository
import dev.syhung.cocassistant.repositories.AnchorRepository
import dev.syhung.cocassistant.repositories.ResearchRecordRepository
/**
 * Canonical Room database for the application.
 *
 * Data used by the newer agent runtime is stored alongside the established
 * application schema. The previous coc_assistant_db file is imported once
 * after the v7 -> v8 schema migration; the source file is deliberately kept
 * as a recovery copy rather than deleted automatically.
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
        ManualActionEntity::class,
        ContextEntity::class,
        AgentExecutionLogEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun stateTransitionDao(): StateTransitionDao
    abstract fun templateDao(): TemplateDao
    abstract fun recoveryPatternDao(): RecoveryPatternDao
    abstract fun manualScriptDao(): ManualScriptDao
    abstract fun manualActionDao(): ManualActionDao
    abstract fun contextDao(): ContextDao
    abstract fun agentExecutionLogDao(): AgentExecutionLogDao
    abstract fun worldStateRepository(): WorldStateRepository
    abstract fun anchorRepository(): AnchorRepository
    abstract fun researchRecordRepository(): ResearchRecordRepository

    companion object {
        private const val TAG = "AppDatabase"
        private const val IMPORT_KEY = "coc_assistant_db_v3"

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
                    MIGRATION_6_7,
                    MIGRATION_7_8
                ).addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        importAgentDataOnce(context.applicationContext, db)
                    }
                }).build().also { INSTANCE = it }
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

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `contexts` (" +
                        "`key` TEXT NOT NULL, `value` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agent_execution_log` (" +
                        "`id` INTEGER NOT NULL, `skillId` TEXT NOT NULL, `actionType` TEXT NOT NULL, " +
                        "`success` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, `errorDetails` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `legacy_imports` (" +
                        "`importKey` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, PRIMARY KEY(`importKey`))"
                )
            }
        }

        /**
         * Copies the only live tables from the former agent database into the
         * canonical database. The source file remains untouched for recovery.
         * Missing or incompatible source tables are skipped safely.
         */
        private fun importAgentDataOnce(context: Context, target: SupportSQLiteDatabase) {
            val marker = target.query(
                "SELECT importKey FROM legacy_imports WHERE importKey = '$IMPORT_KEY'"
            )
            val alreadyImported = try {
                marker.moveToFirst()
            } finally {
                marker.close()
            }
            if (alreadyImported) return

            val sourceFile = context.getDatabasePath("coc_assistant_db")
            if (!sourceFile.isFile) return

            var source: PlatformSQLiteDatabase? = null
            try {
                source = PlatformSQLiteDatabase.openDatabase(
                    sourceFile.absolutePath,
                    null,
                    PlatformSQLiteDatabase.OPEN_READONLY
                )
                target.beginTransaction()
                try {
                    importContexts(source, target)
                    importExecutionLogs(source, target)
                    val markerValues = ContentValues().apply {
                        put("importKey", IMPORT_KEY)
                        put("importedAt", System.currentTimeMillis())
                    }
                    target.insert("legacy_imports", PlatformSQLiteDatabase.CONFLICT_REPLACE, markerValues)
                    target.setTransactionSuccessful()
                } finally {
                    target.endTransaction()
                }
            } catch (error: Exception) {
                Log.e(TAG, "Could not import legacy agent database; source file was preserved.", error)
            } finally {
                source?.close()
            }
        }

        private fun importContexts(source: PlatformSQLiteDatabase, target: SupportSQLiteDatabase) {
            if (!hasTable(source, "contexts")) return
            source.rawQuery("SELECT `key`, value, category, updatedAt FROM contexts", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val values = ContentValues().apply {
                        put("key", cursor.getString(0))
                        put("value", cursor.getString(1))
                        put("category", cursor.getString(2))
                        put("updatedAt", cursor.getLong(3))
                    }
                    target.insert("contexts", PlatformSQLiteDatabase.CONFLICT_REPLACE, values)
                }
            }
        }

        private fun importExecutionLogs(source: PlatformSQLiteDatabase, target: SupportSQLiteDatabase) {
            if (!hasTable(source, "execution_log")) return
            source.rawQuery(
                "SELECT id, skillId, actionType, success, durationMs, timestamp, errorDetails FROM execution_log",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val values = ContentValues().apply {
                        put("id", cursor.getLong(0))
                        put("skillId", cursor.getString(1))
                        put("actionType", cursor.getString(2))
                        put("success", cursor.getInt(3))
                        put("durationMs", cursor.getLong(4))
                        put("timestamp", cursor.getLong(5))
                        if (cursor.isNull(6)) putNull("errorDetails") else put("errorDetails", cursor.getString(6))
                    }
                    target.insert("agent_execution_log", PlatformSQLiteDatabase.CONFLICT_REPLACE, values)
                }
            }
        }

        private fun hasTable(source: PlatformSQLiteDatabase, tableName: String): Boolean {
            source.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(tableName)
            ).use { return it.moveToFirst() }
        }
    }
}
