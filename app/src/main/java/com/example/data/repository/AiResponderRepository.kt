package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.remote.GeminiContentDto
import com.example.data.remote.GeminiGenerationConfigDto
import com.example.data.remote.GeminiPartDto
import com.example.data.remote.GeminiRequestDto
import com.example.data.remote.GeminiRestApi
import com.example.domain.model.AiProviderMode
import com.example.domain.model.CallerCategory
import com.example.domain.model.CallerProfile
import com.example.domain.model.ConnectivityPolicy
import com.example.domain.model.ConversationTurn
import com.example.domain.model.HandlingStrategy
import com.example.domain.model.LearningInsight
import com.example.domain.model.PhoneActionPayload
import com.example.domain.model.PhoneActionType
import com.example.domain.model.ResponderSettings
import com.example.domain.model.Speaker
import com.example.domain.model.StructuredAiReply
import com.example.domain.model.TrainedQaRule
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class CallerContextInfo(
    val phoneNumber: String,
    val resolvedName: String = "",
    val savedProfile: CallerProfile? = null,
    val isInContacts: Boolean = false,
    val recentInsights: List<LearningInsight> = emptyList()
)

interface AiResponderRepository {
    suspend fun generateStructuredReply(
        callerInput: String,
        history: List<ConversationTurn>,
        settings: ResponderSettings,
        callerContext: CallerContextInfo
    ): Result<StructuredAiReply>

    suspend fun summarizeCall(
        turns: List<ConversationTurn>,
        settings: ResponderSettings
    ): String

    suspend fun testConnection(settings: ResponderSettings): Result<String>
}

class DefaultAiResponderRepository(
    private val context: Context?,
    private val geminiRestApi: GeminiRestApi,
    private val offlineEngine: OfflineArabicResponderEngine? = null,
    private val callLogRepository: CallLogRepository? = null
) : AiResponderRepository {

    companion object {
        private const val TAG = "AiResponderRepo"
        private const val FIREBASE_DEFAULT_MODEL = "gemini-3.8-flash"
        private const val AI_TIMEOUT_MS = 35_000L
    }

    private val replySchema by lazy {
        Schema.obj(
            properties = mapOf(
                "replyText" to Schema.string(
                    description = "Natural conversational Arabic response to speak aloud to the caller"
                ),
                "detectedCategory" to Schema.enumeration(
                    values = CallerCategory.entries.map { it.name },
                    description = "Classification of the caller"
                ),
                "strategy" to Schema.enumeration(
                    values = HandlingStrategy.entries.map { it.name },
                    description = "Conversation handling strategy"
                ),
                "intentSummary" to Schema.string(
                    description = "Short Arabic summary of what the caller wants"
                ),
                "shouldEndCall" to Schema.boolean(
                    description = "True if caller said goodbye, goal is completed, or spam caller was politely dismissed"
                ),
                "selfLearningTip" to Schema.string(
                    description = "Short Arabic self-improvement tip or preference remembered about this caller"
                ),
                "phoneActionType" to Schema.enumeration(
                    values = PhoneActionType.entries.map { it.name },
                    description = "Optional phone action requested (SET_ALARM, SET_TIMER, CREATE_REMINDER, or NONE)"
                ),
                "phoneActionTitle" to Schema.string(
                    description = "Title for the reminder or alarm"
                ),
                "phoneActionHour" to Schema.integer(
                    description = "Hour in 24h format (0..23) if SET_ALARM"
                ),
                "phoneActionMinute" to Schema.integer(
                    description = "Minute (0..59) if SET_ALARM"
                ),
                "phoneActionDelayMinutes" to Schema.integer(
                    description = "Delay in minutes if SET_TIMER or CREATE_REMINDER"
                )
            ),
            optionalProperties = listOf(
                "selfLearningTip",
                "intentSummary",
                "phoneActionType",
                "phoneActionTitle",
                "phoneActionHour",
                "phoneActionMinute",
                "phoneActionDelayMinutes"
            )
        )
    }

    private fun isNetworkConnected(): Boolean {
        val ctx = context ?: return true
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun buildTeamSystemPrompt(
        settings: ResponderSettings,
        callerContext: CallerContextInfo
    ): String {
        return buildString {
            append(settings.systemPersona.trim())
            append("\n\n")
            append("أنت تقود فريق استقبال هاتفي متكامل من 4 أدوار:\n")
            append("1. المُصنّف والمُحلِّل: ميّز فوراً هل المتصل عميل (CLIENT)، صديق أو قريب (FRIEND)، سائل عام (INQUIRER)، شخصية مهمة (VIP)، أو مكالمة تسويق/إزعاج (SPAM_SALES).\n")
            append("2. المُهندِل والمُراوغ: اختر الأسلوب المناسب:\n")
            append("   - للأصدقاء والأهل: ترحيب دافئ ولغة ودودة طبيعية (WARM_FRIENDLY).\n")
            append("   - للعملاء: احترافية واهتمام وأخذ التفاصيل أو الموعد بلباقة (PROFESSIONAL_CLIENT).\n")
            if (settings.enableDiplomaticDeflection) {
                append("   - للتسويق والإزعاج أو المكالمات غير المرغوبة: مراوغة دبلوماسية ذكية وهندلة محترمة تُنهي الاتصال دون إحراج أو إفشاء معلومات شخصية (DIPLOMATIC_DEFLECTION)، وحدد shouldEndCall=true بعد الوداع.\n")
            }
            append("   - للأمور العاجلة: طمأنة ووعد بإبلاغ صاحب الهاتف فوراً (URGENT_ESCALATION).\n")
            append("3. المتحدث الطبيعي: صُغ جملة عربية بشرية سلسة ومختصرة (1-3 جمل) خالية تماماً من الرموز مثل * أو # أو الإيموجي لأنها ستُنطق صوتياً.\n")
            append("4. الإغلاق التلقائي وإجراءات الهاتف والتطوير الذاتي:\n")
            append("   - اضبط shouldEndCall=true إذا ودّع المتصل أو انتهى الغرض من المكالمة.\n")
            if (settings.allowCallerReminders) {
                append("   - إذا طلب المتصل أو المستخدم ضبط منبه (SET_ALARM)، مؤقت (SET_TIMER)، أو تذكير بمكالمة/موعد (CREATE_REMINDER)، حدد phoneActionType والتفاصيل المناسبة.\n")
            }
            append("   - سجّل في selfLearningTip معلومة مفيدة لتحسين الرد مستقبلاً.\n\n")

            append("بيانات المتصل الحالية:\n")
            append("- الرقم: ${callerContext.phoneNumber.ifBlank { "غير معروف" }}\n")
            if (callerContext.resolvedName.isNotBlank()) {
                append("- الاسم المعروف: ${callerContext.resolvedName}\n")
            }
            if (callerContext.isInContacts) {
                append("- مسجل في جهات الاتصال الخاصة بالهاتف.\n")
            }
            callerContext.savedProfile?.let { profile ->
                append("- تصنيف سابق: ${profile.category.name} (${profile.category.arabicLabel})\n")
                append("- عدد المكالمات السابقة: ${profile.callCount}\n")
                if (profile.notes.isNotBlank()) {
                    append("- ملاحظات سابقة: ${profile.notes}\n")
                }
                if (profile.isDeflectMode) {
                    append("- تنبيه: هذا الرقم مفعّل له وضع الهندلة والمراوغة المهذبة وإنهاء المكالمة بلباقة.\n")
                }
            }

            if (settings.enableSelfLearning && callerContext.recentInsights.isNotEmpty()) {
                append("\nدروس وتطويرات ذاتية مكتسبة من المكالمات السابقة:\n")
                callerContext.recentInsights.take(4).forEach { insight ->
                    append("- ${insight.insightText}\n")
                }
            }
        }
    }

    override suspend fun generateStructuredReply(
        callerInput: String,
        history: List<ConversationTurn>,
        settings: ResponderSettings,
        callerContext: CallerContextInfo
    ): Result<StructuredAiReply> {
        val cleanedInput = callerInput.trim()
        if (cleanedInput.isEmpty()) {
            return Result.failure(IllegalArgumentException("النص المرسل فارغ."))
        }

        // Offline-only or Hybrid offline fallback check
        val shouldUseOfflineDirectly = settings.connectivityPolicy == ConnectivityPolicy.OFFLINE_ONLY ||
            (settings.connectivityPolicy == ConnectivityPolicy.HYBRID_SMART && !isNetworkConnected())

        if (shouldUseOfflineDirectly && offlineEngine != null) {
            return Result.success(offlineEngine.respondOffline(cleanedInput, settings, callerContext))
        }

        return try {
            val rawText = withTimeout(AI_TIMEOUT_MS) {
                when (settings.aiProviderMode) {
                    AiProviderMode.FIREBASE_AI_LOGIC -> {
                        generateViaFirebaseAi(cleanedInput, history, settings, callerContext)
                    }
                    AiProviderMode.GOOGLE_AI_STUDIO_KEY -> {
                        generateViaGoogleAiStudioRest(cleanedInput, history, settings, callerContext)
                    }
                }
            }

            val parsed = parseStructuredJson(rawText, callerContext.savedProfile)
            if (parsed.replyText.isBlank()) {
                if (offlineEngine != null && settings.connectivityPolicy == ConnectivityPolicy.HYBRID_SMART) {
                    Result.success(offlineEngine.respondOffline(cleanedInput, settings, callerContext))
                } else {
                    Result.failure(IllegalStateException("أعاد نموذج الذكاء الاصطناعي رداً فارغاً."))
                }
            } else {
                // Auto-train the offline engine from successful online Gemini responses
                val localEngine = offlineEngine
                val localRepo = callLogRepository
                if (settings.autoTrainOfflineFromOnline &&
                    localEngine != null &&
                    localRepo != null &&
                    parsed.phoneAction == null
                ) {
                    val keywords = localEngine.extractKeywordsForTraining(cleanedInput)
                    if (keywords.isNotBlank()) {
                        runCatching {
                            localRepo.saveTrainedRule(
                                TrainedQaRule(
                                    triggerKeywords = keywords,
                                    replyText = parsed.replyText,
                                    category = parsed.detectedCategory,
                                    strategy = parsed.strategy,
                                    shouldEndCall = parsed.shouldEndCall
                                )
                            )
                        }
                    }
                }
                Result.success(parsed)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out", e)
            if (offlineEngine != null && settings.connectivityPolicy == ConnectivityPolicy.HYBRID_SMART) {
                Result.success(offlineEngine.respondOffline(cleanedInput, settings, callerContext))
            } else {
                Result.failure(Exception("انتهت مهلة الاتصال بنموذج الذكاء الاصطناعي."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI structured generation failed", e)
            if (offlineEngine != null && settings.connectivityPolicy == ConnectivityPolicy.HYBRID_SMART) {
                Result.success(offlineEngine.respondOffline(cleanedInput, settings, callerContext))
            } else {
                Result.failure(e)
            }
        }
    }

    private suspend fun generateViaFirebaseAi(
        callerInput: String,
        history: List<ConversationTurn>,
        settings: ResponderSettings,
        callerContext: CallerContextInfo
    ): String {
        val model = Firebase.ai.generativeModel(
            modelName = FIREBASE_DEFAULT_MODEL,
            generationConfig = generationConfig {
                temperature = 0.65f
                maxOutputTokens = 480
                responseMimeType = "application/json"
                responseSchema = replySchema
            },
            systemInstruction = content {
                text(buildTeamSystemPrompt(settings, callerContext))
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
        settings: ResponderSettings,
        callerContext: CallerContextInfo
    ): String {
        val apiKey = settings.customApiKey.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("مفتاح Google AI Studio API غير مُدخل في الإعدادات.")
        }

        val jsonSchemaInstruction = buildString {
            append(buildTeamSystemPrompt(settings, callerContext))
            append("\n\nأعد النتيجة بصيغة JSON صالحة فقط بدون أي نص إضافي بهذه المفاتيح:\n")
            append("""{"replyText":"...","detectedCategory":"CLIENT|FRIEND|INQUIRER|VIP|SPAM_SALES|UNKNOWN","strategy":"WARM_FRIENDLY|PROFESSIONAL_CLIENT|DIPLOMATIC_DEFLECTION|URGENT_ESCALATION","intentSummary":"...","shouldEndCall":false,"selfLearningTip":"...","phoneActionType":"NONE|SET_ALARM|SET_TIMER|CREATE_REMINDER","phoneActionTitle":"...","phoneActionHour":8,"phoneActionMinute":0,"phoneActionDelayMinutes":10}""")
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
                parts = listOf(GeminiPartDto(text = jsonSchemaInstruction))
            ),
            contents = contents,
            generationConfig = GeminiGenerationConfigDto(
                temperature = 0.65,
                maxOutputTokens = 480,
                responseMimeType = "application/json"
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
            throw IllegalStateException(
                "خطأ من Google AI Studio (${response.code()}): ${errorBody.take(180)}"
            )
        }

        val body = response.body()
        val text = body?.candidates?.firstOrNull()?.content?.parts
            ?.mapNotNull { it.text }
            ?.joinToString(" ")
            .orEmpty()

        if (text.isBlank()) {
            throw IllegalStateException(body?.error?.message ?: "لم يتم استلام رد من Gemini API.")
        }
        return text
    }

    internal fun parseStructuredJson(
        rawText: String,
        savedProfile: CallerProfile?
    ): StructuredAiReply {
        val trimmed = rawText.trim()
        return try {
            val jsonCandidate = if (trimmed.startsWith("{")) {
                trimmed
            } else {
                val start = trimmed.indexOf('{')
                val end = trimmed.lastIndexOf('}')
                if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
            }

            val obj = JSONObject(jsonCandidate)
            val reply = sanitizeForSpeech(obj.optString("replyText", ""))
            val categoryRaw = obj.optString("detectedCategory", savedProfile?.category?.name ?: "UNKNOWN")
            val strategyRaw = obj.optString("strategy", "PROFESSIONAL_CLIENT")
            val intentSummary = obj.optString("intentSummary", "").trim()
            val shouldEndCall = obj.optBoolean("shouldEndCall", false)
            val learningTip = obj.optString("selfLearningTip", "").trim()

            val actionTypeRaw = obj.optString("phoneActionType", "NONE")
            val actionType = runCatching { PhoneActionType.valueOf(actionTypeRaw) }
                .getOrDefault(PhoneActionType.NONE)

            val phoneAction = if (actionType != PhoneActionType.NONE) {
                PhoneActionPayload(
                    type = actionType,
                    title = obj.optString("phoneActionTitle", "تذكير من المجيب الذكي").trim(),
                    hour = if (obj.has("phoneActionHour") && !obj.isNull("phoneActionHour")) {
                        obj.optInt("phoneActionHour")
                    } else null,
                    minute = if (obj.has("phoneActionMinute") && !obj.isNull("phoneActionMinute")) {
                        obj.optInt("phoneActionMinute")
                    } else null,
                    delayMinutes = if (obj.has("phoneActionDelayMinutes") && !obj.isNull("phoneActionDelayMinutes")) {
                        obj.optInt("phoneActionDelayMinutes")
                    } else null
                )
            } else {
                null
            }

            val category = runCatching { CallerCategory.valueOf(categoryRaw) }
                .getOrDefault(savedProfile?.category ?: CallerCategory.UNKNOWN)
            val strategy = runCatching { HandlingStrategy.valueOf(strategyRaw) }
                .getOrDefault(HandlingStrategy.PROFESSIONAL_CLIENT)

            StructuredAiReply(
                replyText = reply,
                detectedCategory = category,
                strategy = strategy,
                intentSummary = intentSummary,
                shouldEndCall = shouldEndCall,
                selfLearningTip = learningTip,
                isOfflineResponse = false,
                phoneAction = phoneAction
            )
        } catch (e: Exception) {
            Log.w(TAG, "Falling back to plain-text parsing", e)
            StructuredAiReply(
                replyText = sanitizeForSpeech(trimmed),
                detectedCategory = savedProfile?.category ?: CallerCategory.UNKNOWN,
                strategy = HandlingStrategy.PROFESSIONAL_CLIENT,
                shouldEndCall = false,
                isOfflineResponse = false
            )
        }
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

        if (settings.connectivityPolicy == ConnectivityPolicy.OFFLINE_ONLY || !isNetworkConnected()) {
            val firstCaller = conversationTurns.firstOrNull { it.speaker == Speaker.CALLER }?.text.orEmpty()
            return if (firstCaller.isNotBlank()) {
                "ملخص أوفلاين: ${firstCaller.take(80)}"
            } else {
                "محادثة محلية من ${conversationTurns.size} رسائل."
            }
        }

        val transcriptText = conversationTurns.joinToString("\n") { turn ->
            val label = if (turn.speaker == Speaker.CALLER) "المتصل" else "المساعد"
            "$label: ${turn.text}"
        }
        val prompt = "لخّص هذه المكالمة الهاتفية في جملة عربية واحدة واضحة توضح نوع المتصل وسبب الاتصال والنتيجة:\n$transcriptText"

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
        return generateStructuredReply(
            callerInput = "مرحباً، هذا اختبار سريع لفريق المجيب الآلي.",
            history = emptyList(),
            settings = settings,
            callerContext = CallerContextInfo(phoneNumber = "اختبار النظام")
        ).map { structured ->
            val modeTag = if (structured.isOfflineResponse) "أوفلاين" else "أونلاين"
            "${structured.replyText}\n[$modeTag • التصنيف: ${structured.detectedCategory.arabicLabel} • الأسلوب: ${structured.strategy.arabicLabel}]"
        }
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
