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
import com.example.data.repository.DefaultAiResponderRepository
import com.example.domain.model.AiProviderMode
import com.example.domain.model.CallLogItem
import com.example.domain.model.CallOutcome
import com.example.domain.model.ConversationTurn
import com.example.domain.model.ResponderSettings
import com.example.domain.model.Speaker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        callLogRepository = CallLogRepository(database.callLogDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `save and retrieve call log preserves arabic transcript`() = runTest {
        val item = CallLogItem(
            callerNumber = "+966500001122",
            startedAtMillis = 1700000000000L,
            durationSeconds = 42,
            outcome = CallOutcome.COMPLETED,
            summary = "استفسار عن موعد التسليم",
            turns = listOf(
                ConversationTurn(id = "1", speaker = Speaker.ASSISTANT, text = "مرحباً بك!"),
                ConversationTurn(id = "2", speaker = Speaker.CALLER, text = "متى موعد التسليم؟")
            ),
            isTestSession = false
        )

        callLogRepository.saveCallLog(item)
        val saved = callLogRepository.allLogs.first()

        assertEquals(1, saved.size)
        assertEquals("+966500001122", saved.first().callerNumber)
        assertEquals(2, saved.first().turns.size)
        assertEquals("متى موعد التسليم؟", saved.first().turns[1].text)
    }

    @Test
    fun `custom ai studio mode fails gracefully when key is empty`() = runTest {
        val fakeApi = object : GeminiRestApi {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: GeminiRequestDto
            ): Response<GeminiResponseDto> {
                error("Should not be called without API key")
            }
        }
        val repo = DefaultAiResponderRepository(fakeApi)
        val settings = ResponderSettings(
            aiProviderMode = AiProviderMode.GOOGLE_AI_STUDIO_KEY,
            customApiKey = ""
        )

        val result = repo.generateReply("مرحباً", emptyList(), settings)
        assertTrue(result.isFailure)
    }

    @Test
    fun `custom ai studio mode sanitizes markdown and passes history`() = runTest {
        var capturedKey = ""
        var capturedTurns = 0
        val fakeApi = object : GeminiRestApi {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: GeminiRequestDto
            ): Response<GeminiResponseDto> {
                capturedKey = apiKey
                capturedTurns = request.contents.size
                return Response.success(
                    GeminiResponseDto(
                        candidates = listOf(
                            GeminiCandidateDto(
                                content = GeminiContentDto(
                                    role = "model",
                                    parts = listOf(GeminiPartDto(text = "**أهلاً بك** سأبلغه فوراً."))
                                )
                            )
                        )
                    )
                )
            }
        }
        val repo = DefaultAiResponderRepository(fakeApi)
        val settings = ResponderSettings(
            aiProviderMode = AiProviderMode.GOOGLE_AI_STUDIO_KEY,
            customApiKey = "test-key-123"
        )
        val history = listOf(
            ConversationTurn(id = "1", speaker = Speaker.ASSISTANT, text = "مرحباً")
        )

        val result = repo.generateReply("أريد ترك رسالة", history, settings)
        assertTrue(result.isSuccess)
        assertEquals("test-key-123", capturedKey)
        assertEquals(2, capturedTurns)
        assertEquals("أهلاً بك سأبلغه فوراً.", result.getOrNull())
    }
}
