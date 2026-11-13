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

enum class CallOutcome {
    COMPLETED,
    MISSED,
    ERROR,
    TEST_SESSION
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

data class CallLogItem(
    val id: Long = 0,
    val callerNumber: String,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val outcome: CallOutcome,
    val summary: String,
    val turns: List<ConversationTurn>,
    val isTestSession: Boolean = false
)

data class ResponderSettings(
    val isServiceEnabled: Boolean = false,
    val autoAnswerEnabled: Boolean = true,
    val autoAnswerDelaySeconds: Int = 3,
    val useSpeakerphoneForReply: Boolean = true,
    val aiProviderMode: AiProviderMode = AiProviderMode.FIREBASE_AI_LOGIC,
    val customApiKey: String = "",
    val customModelName: String = "gemini-2.5-flash",
    val greetingMessage: String = "مرحباً بك، أنا المساعد الذكي الشخصي. كيف يمكنني مساعدتك اليوم؟",
    val systemPersona: String = "أنت مساعد هاتفي ذكي ومهذب تتحدث العربية بوضوح واختصار وبطريقة طبيعية تشبه البشر. أجب على المتصل بلطف، واجمع سبب الاتصال أو أجب عن استفساره في جمل قصيرة وواضحة مناسبة للمكالمات الصوتية.",
    val fallbackMessage: String = "عذراً، واجهت مشكلة مؤقتة في الاتصال. سأقوم بإبلاغ صاحب الهاتف ليتواصل معك في أقرب وقت.",
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val recognitionLocale: String = "ar-SA"
)

data class LiveCallState(
    val status: CallResponderStatus = CallResponderStatus.INACTIVE,
    val callerNumber: String = "",
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
    val postNotifications: Boolean = false
) {
    val coreGranted: Boolean
        get() = readPhoneState && recordAudio

    val allGranted: Boolean
        get() = readPhoneState && recordAudio && answerPhoneCalls && readCallLog && postNotifications
}
