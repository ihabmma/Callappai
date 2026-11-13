package com.example.data.repository

import android.util.Log
import com.example.data.remote.GeminiCandidateDto
import com.example.data.remote.GeminiContentDto
import com.example.data.remote.GeminiGenerationConfigDto
import com.example.data.remote.GeminiPartDto
import com.example.data.remote.GeminiRequestDto
import com.example.data.remote.GeminiRestApi
import com.example.domain.model.AiProviderMode
import com.example.domain.model.ConversationTurn
import com.example.domain.model.ResponderSettings
import com.example.domain.model.Speaker
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

interface AiResponderRepository {
    suspend fun generateReply(
        callerInput: String,
        history: List<ConversationTurn>,
        settings: ResponderSettings
    ): Result<String>

    suspend fun summarizeCall(
        turns: List<ConversationTurn>,
        settings: ResponderSettings
    ): String

    suspend fun testConnection(settings: ResponderSettings): Result<String>
}

class DefaultAiResponderRepository(
    private val geminiRestApi: GeminiRestApi
) : AiResponderRepository {

    companion object {
        private const val TAG = "AiResponderRepo"
        private const val FIREBASE_DEFAULT_MODEL = "gemini-3.8-flash"
        private const val AI_TIMEOUT_MS = 35_000L
    }

    private fun buildSystemPrompt(settings: ResponderSettings): String {
        return buildString {
            append(settings.systemPersona.trim())
            append("\n\n")
            append("قواعد مهمة للمكالمة الصوتية:\n")
            append("- أجب باللغة العربية بشكل طبيعي، مهذب، وواضح.\n")
            append("- اجعل ردك مختصراً (جملة إلى ثلاث جمل كحد أقصى) ليكون مناسباً للاستماع عبر الهاتف.\n")
            append("- لا تستخدم رموز تنسيق مثل النجوم أو القوائم أو الإيموجي لأن الرد سيُقرأ صوتياً.")
        }
    }

    override suspend fun generateReply(
        callerInput: String,
        history: List<ConversationTurn>,
        settings: ResponderSettings
    ): Result<String> {
        val cleanedInput = callerInput.trim()
        if (cleanedInput.isEmpty()) {
            return Result.failure(IllegalArgumentException("النص المرسل فارغ."))
        }

        return try {
            val reply = withTimeout(AI_TIMEOUT_MS) {
                when (settings.aiProviderMode) {
                    AiProviderMode.FIREBASE_AI_LOGIC -> {
                        generateViaFirebaseAi(cleanedInput, history, settings)
                    }
                    AiProviderMode.GOOGLE_AI_STUDIO_KEY -> {
                        generateViaGoogleAiStudioRest(cleanedInput, history, settings)
                    }
                }
            }
            val normalized = sanitizeForSpeech(reply)
            if (normalized.isBlank()) {
                Result.failure(IllegalStateException("أعاد نموذج الذكاء الاصطناعي رداً فارغاً."))
            } else {
                Result.success(normalized)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out", e)
            Result.failure(Exception("انتهت مهلة الاتصال بنموذج الذكاء الاصطناعي. يرجى المحاولة مرة أخرى."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI generation failed", e)
            Result.failure(e)
        }
    }

    private suspend fun generateViaFirebaseAi(
        callerInput: String,
        history: List<ConversationTurn>,
        settings: ResponderSettings
    ): String {
        val model = Firebase.ai.generativeModel(
            modelName = FIREBASE_DEFAULT_MODEL,
            generationConfig = generationConfig {
                temperature = 0.65f
                maxOutputTokens = 280
            },
            systemInstruction = content {
                text(buildSystemPrompt(settings))
            }
        )

        val chatHistory: List<Content> = history
            .filter { it.speaker == Speaker.CALLER || it.speaker == Speaker.ASSISTANT }
            .map { turn ->
                val role = if (turn.speaker == Speaker.CALLER) "user" else "model"
                content(role) { text(turn.text) }
            }

        val chat = model.startChat(history = chatHistory)
        val response = chat.sendMessage(callerInput)
        return response.text.orEmpty()
    }

    private suspend fun generateViaGoogleAiStudioRest(
        callerInput: String,
        history: List<ConversationTurn>,
        settings: ResponderSettings
    ): String {
        val apiKey = settings.customApiKey.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("مفتاح Google AI Studio API غير مُدخل في الإعدادات.")
        }

        val contents = mutableListOf<GeminiContentDto>()
        history.filter { it.speaker == Speaker.CALLER || it.speaker == Speaker.ASSISTANT }
            .forEach { turn ->
                val role = if (turn.speaker == Speaker.CALLER) "user" else "model"
                contents.add(
                    GeminiContentDto(
                        role = role,
                        parts = listOf(GeminiPartDto(text = turn.text))
                    )
                )
            }
        contents.add(
            GeminiContentDto(
                role = "user",
                parts = listOf(GeminiPartDto(text = callerInput))
            )
        )

        val request = GeminiRequestDto(
            systemInstruction = GeminiContentDto(
                role = "user",
                parts = listOf(GeminiPartDto(text = buildSystemPrompt(settings)))
            ),
            contents = contents,
            generationConfig = GeminiGenerationConfigDto(
                temperature = 0.65,
                maxOutputTokens = 280
            )
        )

        val modelName = settings.customModelName.trim().ifBlank { "gemini-2.5-flash" }
        val response = geminiRestApi.generateContent(
            model = modelName,
            apiKey = apiKey,
            request = request
        )

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val statusCode = response.code()
            throw IllegalStateException(
                "خطأ من Google AI Studio ($statusCode): ${errorBody.take(180)}"
            )
        }

        val body = response.body()
        val firstCandidate: GeminiCandidateDto? = body?.candidates?.firstOrNull()
        val text = firstCandidate?.content?.parts
            ?.mapNotNull { it.text }
            ?.joinToString(" ")
            .orEmpty()

        if (text.isBlank()) {
            val errMsg = body?.error?.message
            throw IllegalStateException(errMsg ?: "لم يتم استلام نص من واجهة Gemini API.")
        }
        return text
    }

    override suspend fun summarizeCall(
        turns: List<ConversationTurn>,
        settings: ResponderSettings
    ): String {
        val conversationTurns = turns.filter {
            it.speaker == Speaker.CALLER || it.speaker == Speaker.ASSISTANT
        }
        if (conversationTurns.isEmpty()) {
            return "تم الرد برسالة الترحيب دون حوار إضافي من المتصل."
        }

        val transcriptText = conversationTurns.joinToString("\n") { turn ->
            val label = if (turn.speaker == Speaker.CALLER) "المتصل" else "المساعد"
            "$label: ${turn.text}"
        }
        val prompt = "لخّص هذه المكالمة الهاتفية في جملة عربية واحدة واضحة ومختصرة توضح سبب الاتصال:\n$transcriptText"

        return try {
            withTimeout(20_000L) {
                when (settings.aiProviderMode) {
                    AiProviderMode.FIREBASE_AI_LOGIC -> {
                        val model = Firebase.ai.generativeModel(FIREBASE_DEFAULT_MODEL)
                        model.generateContent(prompt).text?.trim().orEmpty()
                    }
                    AiProviderMode.GOOGLE_AI_STUDIO_KEY -> {
                        if (settings.customApiKey.isBlank()) {
                            ""
                        } else {
                            val req = GeminiRequestDto(
                                contents = listOf(
                                    GeminiContentDto(
                                        role = "user",
                                        parts = listOf(GeminiPartDto(text = prompt))
                                    )
                                ),
                                generationConfig = GeminiGenerationConfigDto(
                                    temperature = 0.3,
                                    maxOutputTokens = 120
                                )
                            )
                            val res = geminiRestApi.generateContent(
                                model = settings.customModelName.ifBlank { "gemini-2.5-flash" },
                                apiKey = settings.customApiKey.trim(),
                                request = req
                            )
                            res.body()?.candidates?.firstOrNull()?.content?.parts
                                ?.mapNotNull { it.text }?.joinToString(" ")?.trim().orEmpty()
                        }
                    }
                }
            }.ifBlank {
                "محادثة من ${conversationTurns.size} رسائل."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Call summary fallback", e)
            "محادثة مكتملة (${conversationTurns.count { it.speaker == Speaker.CALLER }} مداخلات من المتصل)."
        }
    }

    override suspend fun testConnection(settings: ResponderSettings): Result<String> {
        return generateReply(
            callerInput = "مرحباً، هذا اختبار سريع للتأكد من جاهزية المجيب الآلي.",
            history = emptyList(),
            settings = settings
        )
    }

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
