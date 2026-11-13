package com.example.data.local

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.example.domain.model.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * Automatically deletes old call logs based on retention days (e.g., 3 days),
 * enforces max record limits, and protects device storage when free space gets low.
 */
class StorageRetentionManager(
    private val context: Context,
    private val callLogDao: CallLogDao,
    private val learningInsightDao: LearningInsightDao,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "StorageRetention"
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        private const val LOW_STORAGE_THRESHOLD_MB = 200L
        private const val EMERGENCY_KEEP_COUNT = 25
        private const val MAX_INSIGHTS_KEEP = 30
    }

    val storageStatsFlow: Flow<StorageStats> = combine(
        callLogDao.observeCount(),
        callLogDao.observeEstimatedBytes(),
        settingsDataStore.lastCleanupMillisFlow
    ) { count, bytes, lastCleanup ->
        StorageStats(
            callLogsCount = count,
            estimatedBytes = bytes,
            lastCleanupMillis = lastCleanup,
            availableDeviceMb = readAvailableMb()
        )
    }

    suspend fun enforceRetentionPolicy(): Int = withContext(Dispatchers.IO) {
        try {
            val settings = settingsDataStore.getCurrentSettings()
            val now = System.currentTimeMillis()
            val cutoffMillis = now - (settings.retentionDays * MILLIS_PER_DAY)

            var deleted = callLogDao.deleteOlderThan(cutoffMillis)

            val freeMb = readAvailableMb()
            val effectiveCap = if (freeMb in 1 until LOW_STORAGE_THRESHOLD_MB) {
                minOf(settings.maxStoredCalls, EMERGENCY_KEEP_COUNT)
            } else {
                settings.maxStoredCalls
            }

            deleted += callLogDao.trimToLatest(effectiveCap)
            learningInsightDao.trimToLatest(MAX_INSIGHTS_KEEP)
            settingsDataStore.setLastCleanupMillis(now)

            if (deleted > 0) {
                Log.i(TAG, "Retention cleanup removed $deleted old call log entries (freeMb=$freeMb)")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Storage retention cleanup failed", e)
            0
        }
    }

    private fun readAvailableMb(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            (stat.availableBytes / (1024L * 1024L)).coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }
}
