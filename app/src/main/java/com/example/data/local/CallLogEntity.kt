package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callerNumber: String,
    val callerName: String = "",
    val callerCategory: String = "UNKNOWN",
    val strategyUsed: String = "PROFESSIONAL_CLIENT",
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val outcomeName: String,
    val summary: String,
    val turnsJson: String,
    val isTestSession: Boolean,
    val endedByAi: Boolean = false,
    val handledOffline: Boolean = false,
    val payloadBytes: Int = 0
)

@Entity(tableName = "caller_profiles")
data class CallerProfileEntity(
    @PrimaryKey
    val phoneNumber: String,
    val displayName: String,
    val categoryName: String,
    val notes: String,
    val callCount: Int,
    val lastCallMillis: Long,
    val isDeflectMode: Boolean
)

@Entity(tableName = "learning_insights")
data class LearningInsightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val createdAtMillis: Long,
    val callerNumber: String,
    val insightText: String
)

@Entity(tableName = "trained_qa_rules")
data class TrainedQaRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val triggerKeywords: String,
    val replyText: String,
    val categoryName: String,
    val strategyName: String,
    val shouldEndCall: Boolean,
    val hitCount: Int = 0
)

@Entity(tableName = "scheduled_reminders")
data class ScheduledReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val triggerAtMillis: Long,
    val typeName: String,
    val sourceCaller: String,
    val isCompleted: Boolean = false
)

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY startedAtMillis DESC")
    fun observeAll(): Flow<List<CallLogEntity>>

    @Query("SELECT COUNT(*) FROM call_logs")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(payloadBytes), 0) FROM call_logs")
    fun observeEstimatedBytes(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CallLogEntity): Long

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM call_logs WHERE startedAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query(
        "DELETE FROM call_logs WHERE id NOT IN " +
            "(SELECT id FROM call_logs ORDER BY startedAtMillis DESC LIMIT :keepCount)"
    )
    suspend fun trimToLatest(keepCount: Int): Int

    @Query("DELETE FROM call_logs")
    suspend fun deleteAll()
}

@Dao
interface CallerProfileDao {
    @Query("SELECT * FROM caller_profiles ORDER BY lastCallMillis DESC")
    fun observeAll(): Flow<List<CallerProfileEntity>>

    @Query("SELECT * FROM caller_profiles WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getByNumber(phoneNumber: String): CallerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: CallerProfileEntity)

    @Query("DELETE FROM caller_profiles WHERE phoneNumber = :phoneNumber")
    suspend fun deleteByNumber(phoneNumber: String)
}

@Dao
interface LearningInsightDao {
    @Query("SELECT * FROM learning_insights ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<LearningInsightEntity>>

    @Query("SELECT * FROM learning_insights ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<LearningInsightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(insight: LearningInsightEntity): Long

    @Query(
        "DELETE FROM learning_insights WHERE id NOT IN " +
            "(SELECT id FROM learning_insights ORDER BY createdAtMillis DESC LIMIT :keepCount)"
    )
    suspend fun trimToLatest(keepCount: Int)

    @Query("DELETE FROM learning_insights WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM learning_insights")
    suspend fun deleteAll()
}

@Dao
interface TrainedQaDao {
    @Query("SELECT * FROM trained_qa_rules ORDER BY hitCount DESC, id DESC")
    fun observeAll(): Flow<List<TrainedQaRuleEntity>>

    @Query("SELECT * FROM trained_qa_rules ORDER BY hitCount DESC, id DESC")
    suspend fun getAll(): List<TrainedQaRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: TrainedQaRuleEntity): Long

    @Query("UPDATE trained_qa_rules SET hitCount = hitCount + 1 WHERE id = :id")
    suspend fun incrementHit(id: Long)

    @Query("DELETE FROM trained_qa_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ScheduledReminderDao {
    @Query("SELECT * FROM scheduled_reminders ORDER BY triggerAtMillis ASC")
    fun observeAll(): Flow<List<ScheduledReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ScheduledReminderEntity): Long

    @Query("UPDATE scheduled_reminders SET isCompleted = 1 WHERE id = :id")
    suspend fun markCompleted(id: Long)

    @Query("DELETE FROM scheduled_reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(
    entities = [
        CallLogEntity::class,
        CallerProfileEntity::class,
        LearningInsightEntity::class,
        TrainedQaRuleEntity::class,
        ScheduledReminderEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callLogDao(): CallLogDao
    abstract fun callerProfileDao(): CallerProfileDao
    abstract fun learningInsightDao(): LearningInsightDao
    abstract fun trainedQaDao(): TrainedQaDao
    abstract fun scheduledReminderDao(): ScheduledReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE call_logs ADD COLUMN callerName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE call_logs ADD COLUMN callerCategory TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE call_logs ADD COLUMN strategyUsed TEXT NOT NULL DEFAULT 'PROFESSIONAL_CLIENT'")
                db.execSQL("ALTER TABLE call_logs ADD COLUMN endedByAi INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE call_logs ADD COLUMN payloadBytes INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `caller_profiles` (
                        `phoneNumber` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `callCount` INTEGER NOT NULL,
                        `lastCallMillis` INTEGER NOT NULL,
                        `isDeflectMode` INTEGER NOT NULL,
                        PRIMARY KEY(`phoneNumber`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `learning_insights` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `callerNumber` TEXT NOT NULL,
                        `insightText` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE call_logs ADD COLUMN handledOffline INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trained_qa_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `triggerKeywords` TEXT NOT NULL,
                        `replyText` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `strategyName` TEXT NOT NULL,
                        `shouldEndCall` INTEGER NOT NULL,
                        `hitCount` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scheduled_reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `triggerAtMillis` INTEGER NOT NULL,
                        `typeName` TEXT NOT NULL,
                        `sourceCaller` TEXT NOT NULL,
                        `isCompleted` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_call_responder.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
