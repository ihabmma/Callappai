package com.example.data.repository

import com.example.data.local.CallLogDao
import com.example.data.local.CallLogEntity
import com.example.domain.model.CallLogItem
import com.example.domain.model.CallOutcome
import com.example.domain.model.ConversationTurn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CallLogRepository(
    private val dao: CallLogDao,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    val allLogs: Flow<List<CallLogItem>> = dao.observeAll().map { list ->
        list.map { entity -> entity.toDomain() }
    }

    suspend fun saveCallLog(item: CallLogItem): Long {
        return dao.insert(item.toEntity())
    }

    suspend fun deleteCallLog(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAllLogs() {
        dao.deleteAll()
    }

    private fun CallLogEntity.toDomain(): CallLogItem {
        val parsedTurns = runCatching {
            json.decodeFromString<List<ConversationTurn>>(turnsJson)
        }.getOrDefault(emptyList())

        val parsedOutcome = runCatching {
            CallOutcome.valueOf(outcomeName)
        }.getOrDefault(CallOutcome.COMPLETED)

        return CallLogItem(
            id = id,
            callerNumber = callerNumber,
            startedAtMillis = startedAtMillis,
            durationSeconds = durationSeconds,
            outcome = parsedOutcome,
            summary = summary,
            turns = parsedTurns,
            isTestSession = isTestSession
        )
    }

    private fun CallLogItem.toEntity(): CallLogEntity {
        val encodedTurns = json.encodeToString(turns)
        return CallLogEntity(
            id = id,
            callerNumber = callerNumber,
            startedAtMillis = startedAtMillis,
            durationSeconds = durationSeconds,
            outcomeName = outcome.name,
            summary = summary,
            turnsJson = encodedTurns,
            isTestSession = isTestSession
        )
    }
}
