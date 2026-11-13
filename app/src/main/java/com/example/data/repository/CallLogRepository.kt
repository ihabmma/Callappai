package com.example.data.repository

import com.example.data.local.CallLogDao
import com.example.data.local.CallLogEntity
import com.example.data.local.CallerProfileDao
import com.example.data.local.CallerProfileEntity
import com.example.data.local.LearningInsightDao
import com.example.data.local.LearningInsightEntity
import com.example.data.local.ScheduledReminderDao
import com.example.data.local.ScheduledReminderEntity
import com.example.data.local.TrainedQaDao
import com.example.data.local.TrainedQaRuleEntity
import com.example.domain.model.CallLogItem
import com.example.domain.model.CallOutcome
import com.example.domain.model.CallerCategory
import com.example.domain.model.CallerProfile
import com.example.domain.model.ConversationTurn
import com.example.domain.model.HandlingStrategy
import com.example.domain.model.LearningInsight
import com.example.domain.model.PhoneActionType
import com.example.domain.model.ScheduledReminder
import com.example.domain.model.TrainedQaRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CallLogRepository(
    private val dao: CallLogDao,
    private val callerProfileDao: CallerProfileDao,
    private val learningInsightDao: LearningInsightDao,
    private val trainedQaDao: TrainedQaDao,
    private val scheduledReminderDao: ScheduledReminderDao,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    val allLogs: Flow<List<CallLogItem>> = dao.observeAll().map { list ->
        list.map { entity -> entity.toDomain() }
    }

    val allProfiles: Flow<List<CallerProfile>> = callerProfileDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val allLearningInsights: Flow<List<LearningInsight>> = learningInsightDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val allTrainedRules: Flow<List<TrainedQaRule>> = trainedQaDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val allScheduledReminders: Flow<List<ScheduledReminder>> = scheduledReminderDao.observeAll().map { list ->
        list.map { it.toDomain() }
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

    suspend fun getCallerProfile(phoneNumber: String): CallerProfile? {
        return callerProfileDao.getByNumber(phoneNumber)?.toDomain()
    }

    suspend fun upsertCallerProfile(profile: CallerProfile) {
        callerProfileDao.upsert(profile.toEntity())
    }

    suspend fun deleteCallerProfile(phoneNumber: String) {
        callerProfileDao.deleteByNumber(phoneNumber)
    }

    suspend fun addLearningInsight(callerNumber: String, insightText: String) {
        val clean = insightText.trim()
        if (clean.isBlank()) return
        learningInsightDao.insert(
            LearningInsightEntity(
                createdAtMillis = System.currentTimeMillis(),
                callerNumber = callerNumber,
                insightText = clean
            )
        )
    }

    suspend fun getRecentInsights(limit: Int = 5): List<LearningInsight> {
        return learningInsightDao.getRecent(limit).map { it.toDomain() }
    }

    suspend fun deleteInsight(id: Long) {
        learningInsightDao.deleteById(id)
    }

    suspend fun clearInsights() {
        learningInsightDao.deleteAll()
    }

    suspend fun getAllTrainedRulesSnapshot(): List<TrainedQaRule> {
        return trainedQaDao.getAll().map { it.toDomain() }
    }

    suspend fun saveTrainedRule(rule: TrainedQaRule): Long {
        return trainedQaDao.insert(rule.toEntity())
    }

    suspend fun incrementTrainedRuleHit(id: Long) {
        trainedQaDao.incrementHit(id)
    }

    suspend fun deleteTrainedRule(id: Long) {
        trainedQaDao.deleteById(id)
    }

    suspend fun saveScheduledReminder(reminder: ScheduledReminder): Long {
        return scheduledReminderDao.insert(reminder.toEntity())
    }

    suspend fun markReminderCompleted(id: Long) {
        scheduledReminderDao.markCompleted(id)
    }

    suspend fun deleteScheduledReminder(id: Long) {
        scheduledReminderDao.deleteById(id)
    }

    private fun CallLogEntity.toDomain(): CallLogItem {
        val parsedTurns = runCatching {
            json.decodeFromString<List<ConversationTurn>>(turnsJson)
        }.getOrDefault(emptyList())

        val parsedOutcome = runCatching {
            CallOutcome.valueOf(outcomeName)
        }.getOrDefault(CallOutcome.COMPLETED)

        val parsedCategory = runCatching {
            CallerCategory.valueOf(callerCategory)
        }.getOrDefault(CallerCategory.UNKNOWN)

        val parsedStrategy = runCatching {
            HandlingStrategy.valueOf(strategyUsed)
        }.getOrDefault(HandlingStrategy.PROFESSIONAL_CLIENT)

        return CallLogItem(
            id = id,
            callerNumber = callerNumber,
            callerName = callerName,
            callerCategory = parsedCategory,
            strategyUsed = parsedStrategy,
            startedAtMillis = startedAtMillis,
            durationSeconds = durationSeconds,
            outcome = parsedOutcome,
            summary = summary,
            turns = parsedTurns,
            isTestSession = isTestSession,
            endedByAi = endedByAi,
            handledOffline = handledOffline
        )
    }

    private fun CallLogItem.toEntity(): CallLogEntity {
        val encodedTurns = json.encodeToString(turns)
        val approxBytes = encodedTurns.toByteArray(Charsets.UTF_8).size +
            summary.toByteArray(Charsets.UTF_8).size +
            callerNumber.length + callerName.length + 128

        return CallLogEntity(
            id = id,
            callerNumber = callerNumber,
            callerName = callerName,
            callerCategory = callerCategory.name,
            strategyUsed = strategyUsed.name,
            startedAtMillis = startedAtMillis,
            durationSeconds = durationSeconds,
            outcomeName = outcome.name,
            summary = summary,
            turnsJson = encodedTurns,
            isTestSession = isTestSession,
            endedByAi = endedByAi,
            handledOffline = handledOffline,
            payloadBytes = approxBytes
        )
    }

    private fun CallerProfileEntity.toDomain(): CallerProfile {
        val category = runCatching { CallerCategory.valueOf(categoryName) }
            .getOrDefault(CallerCategory.UNKNOWN)
        return CallerProfile(
            phoneNumber = phoneNumber,
            displayName = displayName,
            category = category,
            notes = notes,
            callCount = callCount,
            lastCallMillis = lastCallMillis,
            isDeflectMode = isDeflectMode
        )
    }

    private fun CallerProfile.toEntity(): CallerProfileEntity {
        return CallerProfileEntity(
            phoneNumber = phoneNumber,
            displayName = displayName,
            categoryName = category.name,
            notes = notes,
            callCount = callCount,
            lastCallMillis = lastCallMillis,
            isDeflectMode = isDeflectMode
        )
    }

    private fun LearningInsightEntity.toDomain(): LearningInsight {
        return LearningInsight(
            id = id,
            createdAtMillis = createdAtMillis,
            callerNumber = callerNumber,
            insightText = insightText
        )
    }

    private fun TrainedQaRuleEntity.toDomain(): TrainedQaRule {
        val cat = runCatching { CallerCategory.valueOf(categoryName) }
            .getOrDefault(CallerCategory.INQUIRER)
        val strat = runCatching { HandlingStrategy.valueOf(strategyName) }
            .getOrDefault(HandlingStrategy.PROFESSIONAL_CLIENT)
        return TrainedQaRule(
            id = id,
            triggerKeywords = triggerKeywords,
            replyText = replyText,
            category = cat,
            strategy = strat,
            shouldEndCall = shouldEndCall,
            hitCount = hitCount
        )
    }

    private fun TrainedQaRule.toEntity(): TrainedQaRuleEntity {
        return TrainedQaRuleEntity(
            id = id,
            triggerKeywords = triggerKeywords,
            replyText = replyText,
            categoryName = category.name,
            strategyName = strategy.name,
            shouldEndCall = shouldEndCall,
            hitCount = hitCount
        )
    }

    private fun ScheduledReminderEntity.toDomain(): ScheduledReminder {
        val actionType = runCatching { PhoneActionType.valueOf(typeName) }
            .getOrDefault(PhoneActionType.CREATE_REMINDER)
        return ScheduledReminder(
            id = id,
            title = title,
            triggerAtMillis = triggerAtMillis,
            type = actionType,
            sourceCaller = sourceCaller,
            isCompleted = isCompleted
        )
    }

    private fun ScheduledReminder.toEntity(): ScheduledReminderEntity {
        return ScheduledReminderEntity(
            id = id,
            title = title,
            triggerAtMillis = triggerAtMillis,
            typeName = type.name,
            sourceCaller = sourceCaller,
            isCompleted = isCompleted
        )
    }
}
