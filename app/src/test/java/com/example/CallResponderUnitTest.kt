package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.remote.GeminiCandidateDto
import com.example.data.remote.GeminiContentDto
import com.example.data.remote.GeminiPartDto
import com.example.data.remote.GeminiRequestDto
import com.example.data.remote.GeminiResponseDto
import com.example.data.remote.GeminiRestApi
import com.example.data.repository.CallLogRepository
import com.example.data.repository.CallerContextInfo
import com.example.data.repository.DefaultAiResponderRepository
import com.example.data.repository.OfflineArabicResponderEngine
import com.example.domain.model.AiProviderMode
import com.example.domain.model.CallLogItem
import com.example.domain.model.CallOutcome
import com.example.domain.model.CallerCategory
import com.example.domain.model.CallerProfile
import com.example.domain.model.ConnectivityPolicy
import com.example.domain.model.ConversationTurn
import com.example.domain.model.HandlingStrategy
import com.example.domain.model.PhoneActionType
import com.example.domain.model.ResponderSettings
import com.example.domain.model.ScheduledReminder
import com.example.domain.model.Speaker
import com.example.domain.model.TrainedQaRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CallResponderUnitTest {

    private lateinit var database: AppDatabase
    private lateinit var callLogRepository: CallLogRepository
    private lateinit var offlineEngine: OfflineArabicResponderEngine

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        callLogRepository = CallLogRepository(
            dao = database.callLogDao(),
            callerProfileDao = database.callerProfileDao(),
            learningInsightDao = database.learningInsightDao(),
            trainedQaDao = database.trainedQaDao(),
            scheduledReminderDao = database.scheduledReminderDao()
        )
        offlineEngine = OfflineArabicResponderEngine(callLogRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `save and retrieve call log preserves arabic transcript and offline flag`() = runTest {
        val item = CallLogItem(
            callerNumber = "+966500001122",
            callerName = "أحمد",
            callerCategory = CallerCategory.CLIENT,
            strategyUsed = HandlingStrategy.PROFESSIONAL_CLIENT,
            startedAtMillis = System.currentTimeMillis(),
            durationSeconds = 42,
            outcome = CallOutcome.COMPLETED,
            summary = "استفسار عن موعد التسليم",
            turns = listOf(
                ConversationTurn(id = "1", speaker = Speaker.ASSISTANT, text = "مرحباً بك!"),
                ConversationTurn(id = "2", speaker = Speaker.CALLER, text = "متى موعد التسليم؟")
            ),
            isTestSession = false,
            endedByAi = true,
            handledOffline = true
        )

        callLogRepository.saveCallLog(item)
        val saved = callLogRepository.allLogs.first()

        assertEquals(1, saved.size)
        assertEquals("+966500001122", saved.first().callerNumber)
        assertEquals(CallerCategory.CLIENT, saved.first().callerCategory)
        assertTrue(saved.first().endedByAi)
        assertTrue(saved.first().handledOffline)
        assertEquals("متى موعد التسليم؟", saved.first().turns[1].text)
    }

    @Test
    fun `retention query deletes calls older than 3 days`() = runTest {
        val now = System.currentTimeMillis()
        val fourDaysAgo = now - (4L * 24L * 60L * 60L * 1000L)
        val threeDaysCutoff = now - (3L * 24L * 60L * 60L * 1000L)

        callLogRepository.saveCallLog(
            CallLogItem(
                callerNumber = "0501111111",
                startedAtMillis = fourDaysAgo,
                durationSeconds = 20,
                outcome = CallOutcome.COMPLETED,
                summary = "مكالمة قديمة",
                turns = emptyList()
            )
        )
        callLogRepository.saveCallLog(
            CallLogItem(
                callerNumber = "0502222222",
                startedAtMillis = now,
                durationSeconds = 15,
                outcome = CallOutcome.COMPLETED,
                summary = "مكالمة حديثة",
                turns = emptyList()
            )
        )

        val deleted = database.callLogDao().deleteOlderThan(threeDaysCutoff)
        val remaining = callLogRepository.allLogs.first()

        assertEquals(1, deleted)
        assertEquals(1, remaining.size)
        assertEquals("0502222222", remaining.first().callerNumber)
    }

    @Test
    fun `caller profiles and self-learning insights persist cleanly`() = runTest {
        callLogRepository.upsertCallerProfile(
            CallerProfile(
                phoneNumber = "0559998877",
                displayName = "شركة تسويق",
                category = CallerCategory.SPAM_SALES,
                notes = "اتصالات متكررة لعروض القروض",
                callCount = 2,
                isDeflectMode = true
            )
        )
        callLogRepository.addLearningInsight("0559998877", "اعتذر بلباقة وأنهِ المكالمة خلال رد واحد.")

        val profile = callLogRepository.getCallerProfile("0559998877")
        val insights = callLogRepository.getRecentInsights(5)

        assertEquals(CallerCategory.SPAM_SALES, profile?.category)
        assertTrue(profile?.isDeflectMode == true)
        assertEquals(1, insights.size)
    }

    @Test
    fun `offline trained QA rule matches arabic query and increments hit count`() = runTest {
        callLogRepository.saveTrainedRule(
            TrainedQaRule(
                triggerKeywords = "دوام, موقع, مكتب",
                replyText = "مكتبنا مفتوح يومياً من التاسعة صباحاً حتى الخامسة مساءً.",
                category = CallerCategory.CLIENT,
                strategy = HandlingStrategy.PROFESSIONAL_CLIENT,
                shouldEndCall = false
            )
        )

        val fakeApi = object : GeminiRestApi {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: GeminiRequestDto
            ): Response<GeminiResponseDto> {
                error("Should not call remote API in OFFLINE_ONLY mode")
            }
        }
        val repo = DefaultAiResponderRepository(
            context = null,
            geminiRestApi = fakeApi,
            offlineEngine = offlineEngine,
            callLogRepository = callLogRepository
        )
        val settings = ResponderSettings(connectivityPolicy = ConnectivityPolicy.OFFLINE_ONLY)

        val result = repo.generateStructuredReply(
            callerInput = "أين موقع المكتب وما هي ساعات الدوام؟",
            history = emptyList(),
            settings = settings,
            callerContext = CallerContextInfo(phoneNumber = "0509988776")
        )

        assertTrue(result.isSuccess)
        val reply = result.getOrThrow()
        assertTrue(reply.isOfflineResponse)
        assertTrue(reply.replyText.contains("التاسعة صباحاً"))
        val rules = callLogRepository.getAllTrainedRulesSnapshot()
        assertEquals(1, rules.first().hitCount)
    }

    @Test
    fun `offline engine extracts alarm and reminder phone actions`() = runTest {
        val settings = ResponderSettings(
            connectivityPolicy = ConnectivityPolicy.OFFLINE_ONLY,
            allowCallerReminders = true
        )

        val alarmReply = offlineEngine.respondOffline(
            callerInput = "اضبط لي منبه الساعة 7 مساء",
            settings = settings,
            callerContext = CallerContextInfo(phoneNumber = "0501234567")
        )

        assertTrue(alarmReply.isOfflineResponse)
        assertNotNull(alarmReply.phoneAction)
        assertEquals(PhoneActionType.SET_ALARM, alarmReply.phoneAction?.type)
        assertEquals(19, alarmReply.phoneAction?.hour)

        val reminderId = callLogRepository.saveScheduledReminder(
            ScheduledReminder(
                title = "تذكير اختبار",
                triggerAtMillis = System.currentTimeMillis() + 600_000L,
                type = PhoneActionType.CREATE_REMINDER,
                sourceCaller = "0501234567"
            )
        )
        assertTrue(reminderId > 0L)
        assertEquals(1, callLogRepository.allScheduledReminders.first().size)
    }

    @Test
    fun `custom ai studio mode fails gracefully when key is empty in online preferred`() = runTest {
        val fakeApi = object : GeminiRestApi {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: GeminiRequestDto
            ): Response<GeminiResponseDto> {
                error("Should not be called without API key")
            }
        }
        val repo = DefaultAiResponderRepository(
            context = null,
            geminiRestApi = fakeApi,
            offlineEngine = offlineEngine,
            callLogRepository = callLogRepository
        )
        val settings = ResponderSettings(
            aiProviderMode = AiProviderMode.GOOGLE_AI_STUDIO_KEY,
            connectivityPolicy = ConnectivityPolicy.ONLINE_PREFERRED,
            customApiKey = ""
        )

        val result = repo.generateStructuredReply(
            callerInput = "مرحباً",
            history = emptyList(),
            settings = settings,
            callerContext = CallerContextInfo(phoneNumber = "0500000000")
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `structured json reply parses deflection, hangup, and learning tip`() = runTest {
        var capturedKey = ""
        val fakeJson = """
            {
              "replyText": "**نعتذر** منك حالياً لا نحتاج لأي عروض تمويل، يومك سعيد ومع السلامة.",
              "detectedCategory": "SPAM_SALES",
              "strategy": "DIPLOMATIC_DEFLECTION",
              "intentSummary": "عرض تسويقي غير مرغوب",
              "shouldEndCall": true,
              "selfLearningTip": "صرف عروض التمويل بلباقة دون الدخول في تفاصيل.",
              "phoneActionType": "NONE"
            }
        """.trimIndent()

        val fakeApi = object : GeminiRestApi {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: GeminiRequestDto
            ): Response<GeminiResponseDto> {
                capturedKey = apiKey
                return Response.success(
                    GeminiResponseDto(
                        candidates = listOf(
                            GeminiCandidateDto(
                                content = GeminiContentDto(
                                    role = "model",
                                    parts = listOf(GeminiPartDto(text = fakeJson))
                                )
                            )
                        )
                    )
                )
            }
        }
        val repo = DefaultAiResponderRepository(
            context = null,
            geminiRestApi = fakeApi,
            offlineEngine = offlineEngine,
            callLogRepository = callLogRepository
        )
        val settings = ResponderSettings(
            aiProviderMode = AiProviderMode.GOOGLE_AI_STUDIO_KEY,
            connectivityPolicy = ConnectivityPolicy.ONLINE_PREFERRED,
            customApiKey = "test-key-123"
        )

        val result = repo.generateStructuredReply(
            callerInput = "لدينا عرض بطاقة ائتمان ممتاز",
            history = emptyList(),
            settings = settings,
            callerContext = CallerContextInfo(phoneNumber = "0590000000")
        )

        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("test-key-123", capturedKey)
        assertEquals(CallerCategory.SPAM_SALES, parsed.detectedCategory)
        assertEquals(HandlingStrategy.DIPLOMATIC_DEFLECTION, parsed.strategy)
        assertTrue(parsed.shouldEndCall)
        assertEquals("نعتذر منك حالياً لا نحتاج لأي عروض تمويل، يومك سعيد ومع السلامة.", parsed.replyText)
        assertTrue(parsed.selfLearningTip.contains("التمويل"))
    }
}
