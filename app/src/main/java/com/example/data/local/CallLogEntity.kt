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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callerNumber: String,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val outcomeName: String,
    val summary: String,
    val turnsJson: String,
    val isTestSession: Boolean
)

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY startedAtMillis DESC")
    fun observeAll(): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CallLogEntity): Long

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM call_logs")
    suspend fun deleteAll()
}

@Database(
    entities = [CallLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callLogDao(): CallLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_call_responder.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
