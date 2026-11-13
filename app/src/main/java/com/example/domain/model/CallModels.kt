package com.example.domain.model

import kotlinx.serialization.Serializable

enum class CallResponderStatus {
    INACTIVE,
    LISTENING,
    RINGING,
    ACTIVE_CALL,
    PROCESSING_AI,
    SPEAKING,
    ERROR
}

enum class AiProviderMode {
    FIREBASE_AI_LOGIC,
    GOOGLE_AI_STUDIO_KEY
}

enum class ConnectivityPolicy(val arabicLabel: String) {
    HYBRID_SMART("ذكي: أونلاين + أوفلاين تلقائي"),
    OFFLINE_ONLY("أوفلاين بالكامل (بدون إنترنت)"),
    ONLINE_PREFERRED("أونلاين مفضل")
}

enum class CallOutcome {
    COMPLETED,
    MISSED,
    ERROR,
    TEST_SESSION
}

enum class CallerCategory(val arabicLabel: String) {
    CLIENT("عميل"),
    FRIEND("صديق / أهل"),
    INQUIRER("سائل / استفسار"),
    VIP("مهم VIP"),
    SPAM_SALES("تسويق / إزعاج"),
    UNKNOWN("غير مصنف")
}

enum class HandlingStrategy(val arabicLabel: String) {
    WARM_FRIENDLY("ترحيب ودي دافئ"),
    PROFESSIONAL_CLIENT("احترافية وخدمة عملاء"),
    DIPLOMATIC_DEFLECTION("مراوغة وهندلة لبقة"),
    URGENT_ESCALATION("أولوية وتوجيه عاجل")
}

enum class PhoneActionType(val arabicLabel: String) {
    NONE("بدون إجراء"),
    SET_ALARM("ضبط منبه"),
    SET_TIMER("ضبط مؤقت"),
    CREATE_REMINDER("تذكير مجدول")
}

@Serializable
enum class Speaker {
    CALLER,
    ASSISTANT,
    SYSTEM
}

@Serializable
data class ConversationTurn(
    val id: String,
    val speaker: Speaker,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class PhoneActionPayload(
    val type: PhoneActionType = PhoneActionType.NONE,
    val title: String = "",
    val hour: Int? = null,
    val minute: Int? = null,
    val delayMinutes: Int? = null
)

data class StructuredAiReply(
    val replyText: String,
    val detectedCategory: CallerCategory = CallerCategory.UNKNOWN,
    val strategy: HandlingStrategy = HandlingStrategy.PROFESSIONAL_CLIENT,
    val intentSummary: String = "",
    val shouldEndCall: Boolean = false,
    val selfLearningTip: String = "",
    val isOfflineResponse: Boolean = false,
    val phoneAction: PhoneActionPayload? = null
)

data class CallerProfile(
    val phoneNumber: String,
    val displayName: String,
    val category: CallerCategory = CallerCategory.UNKNOWN,
    val notes: String = "",
    val callCount: Int = 0,
    val lastCallMillis: Long = System.currentTimeMillis(),
    val isDeflectMode: Boolean = false
)

data class LearningInsight(
    val id: Long = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val callerNumber: String = "",
    val insightText: String
)

data class TrainedQaRule(
    val id: Long = 0,
    val triggerKeywords: String,
    val replyText: String,
    val category: CallerCategory = CallerCategory.INQUIRER,
    val strategy: HandlingStrategy = HandlingStrategy.PROFESSIONAL_CLIENT,
    val shouldEndCall: Boolean = false,
    val hitCount: Int = 0
)

data class ScheduledReminder(
    val id: Long = 0,
    val title: String,
    val triggerAtMillis: Long,
    val type: PhoneActionType = PhoneActionType.CREATE_REMINDER,
    val sourceCaller: String = "",
    val isCompleted: Boolean = false
)

data class StorageStats(
    val callLogsCount: Int = 0,
    val estimatedBytes: Long = 0L,
    val lastCleanupMillis: Long = 0L,
    val availableDeviceMb: Long = 0L
)

data class CallLogItem(
    val id: Long = 0,
    val callerNumber: String,
    val callerName: String = "",
    val callerCategory: CallerCategory = CallerCategory.UNKNOWN,
    val strategyUsed: HandlingStrategy = HandlingStrategy.PROFESSIONAL_CLIENT,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val outcome: CallOutcome,
    val summary: String,
    val turns: List<ConversationTurn>,
    val isTestSession: Boolean = false,
    val endedByAi: Boolean = false,
    val handledOffline: Boolean = false
)

data class ResponderSettings(
    val isServiceEnabled: Boolean = false,
    val autoAnswerEnabled: Boolean = true,
    val autoAnswerDelaySeconds: Int = 3,
    val autoHangupEnabled: Boolean = true,
    val maxTurnsPerCall: Int = 8,
    val useSpeakerphoneForReply: Boolean = true,
    val aiProviderMode: AiProviderMode = AiProviderMode.FIREBASE_AI_LOGIC,
    val connectivityPolicy: ConnectivityPolicy = ConnectivityPolicy.HYBRID_SMART,
    val customApiKey: String = "",
    val customModelName: String = "gemini-2.5-flash",
    val greetingMessage: String = "مرحباً بك، أهلاً وسهلاً. أنا المساعد الشخصي، كيف أقدر أخدمك اليوم؟",
    val systemPersona: String = "أنت عضو في فريق استقبال ذكي يتحدث العربية بطلاقة وطبيعية تشبه البشر تماماً. تميّز بذكاء بين العملاء والأصدقاء وأصحاب الاستفسارات ومتصلي التسويق؛ تتعامل بلطف وحفاوة مع الأصدقاء، وباحترافية مع العملاء، وبمراوغة وهندلة دبلوماسية مهذبة مع المتصلين المزعجين.",
    val fallbackMessage: String = "عذراً، صاحب الهاتف غير متاح حالياً. تم تسجيل رسالتك وسيتواصل معك في أقرب وقت.",
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val recognitionLocale: String = "ar-SA",
    val retentionDays: Int = 3,
    val maxStoredCalls: Int = 100,
    val enableSelfLearning: Boolean = true,
    val enableDiplomaticDeflection: Boolean = true,
    val stealthZeroDrainMode: Boolean = true,
    val floatingOverlayEnabled: Boolean = false,
    val autoTrainOfflineFromOnline: Boolean = true,
    val allowCallerReminders: Boolean = true
)

data class LiveCallState(
    val status: CallResponderStatus = CallResponderStatus.INACTIVE,
    val callerNumber: String = "",
    val callerName: String = "",
    val detectedCategory: CallerCategory = CallerCategory.UNKNOWN,
    val activeStrategy: HandlingStrategy = HandlingStrategy.PROFESSIONAL_CLIENT,
    val isOfflineActive: Boolean = false,
    val isTestSession: Boolean = false,
    val audioAmplitude: Float = 0f,
    val partialCallerSpeech: String = "",
    val turns: List<ConversationTurn> = emptyList(),
    val statusNote: String = "",
    val errorMessage: String? = null,
    val isListeningForSpeech: Boolean = false
)

data class PermissionSummary(
    val readPhoneState: Boolean = false,
    val recordAudio: Boolean = false,
    val answerPhoneCalls: Boolean = false,
    val readCallLog: Boolean = false,
    val readContacts: Boolean = false,
    val postNotifications: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = false
) {
    val coreGranted: Boolean
        get() = readPhoneState && recordAudio

    val allGranted: Boolean
        get() = readPhoneState && recordAudio && answerPhoneCalls &&
            readCallLog && readContacts && postNotifications
}
