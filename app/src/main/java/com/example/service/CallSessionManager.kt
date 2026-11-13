package com.example.service

import android.util.Log
import com.example.data.local.SettingsDataStore
import com.example.data.repository.AiResponderRepository
import com.example.data.repository.CallLogRepository
import com.example.domain.model.CallLogItem
import com.example.domain.model.CallOutcome
import com.example.domain.model.CallResponderStatus
import com.example.domain.model.ConversationTurn
import com.example.domain.model.LiveCallState
import com.example.domain.model.ResponderSettings
import com.example.domain.model.Speaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Coordinates the live call pipeline:
 * AudioCaptureEngine -> SpeechInputEngine -> AiResponderRepository -> TtsAudioEngine -> CallLogRepository.
 */
class CallSessionManager(
    private val settingsDataStore: SettingsDataStore,
    private val aiResponderRepository: AiResponderRepository,
    private val callLogRepository: CallLogRepository,
    private val audioCaptureEngine: AudioCaptureEngine,
    private val ttsAudioEngine: TtsAudioEngine,
    private val speechInputEngine: SpeechInputEngine
) {
    companion object {
        private const val TAG = "CallSessionManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _liveState = MutableStateFlow(LiveCallState())
    val liveState: StateFlow<LiveCallState> = _liveState.asStateFlow()

    private var sessionJob: Job? = null
    private var amplitudeCollectorJob: Job? = null
    private var sessionStartTimeMillis: Long = 0L

    init {
        amplitudeCollectorJob = scope.launch {
            audioCaptureEngine.amplitude.collect { level ->
                _liveState.update { current ->
                    if (current.status == CallResponderStatus.INACTIVE ||
                        current.status == CallResponderStatus.LISTENING
                    ) {
                        current.copy(audioAmplitude = 0f)
                    } else {
                        current.copy(audioAmplitude = level)
                    }
                }
            }
        }
    }

    fun onServiceStateChanged(enabled: Boolean) {
        if (sessionJob?.isActive == true) return
        _liveState.update {
            it.copy(
                status = if (enabled) CallResponderStatus.LISTENING else CallResponderStatus.INACTIVE,
                statusNote = if (enabled) "في انتظار المكالمات الواردة" else "المجيب الآلي متوقف",
                errorMessage = null,
                audioAmplitude = 0f
            )
        }
    }

    fun onIncomingCallRinging(callerNumber: String) {
        if (sessionJob?.isActive == true) return
        _liveState.update {
            it.copy(
                status = CallResponderStatus.RINGING,
                callerNumber = callerNumber.ifBlank { "رقم غير معروف" },
                isTestSession = false,
                statusNote = "مكالمة واردة — جاري التحضير للرد التلقائي...",
                errorMessage = null
            )
        }
    }

    fun onIncomingCallMissed(callerNumber: String) {
        if (sessionJob?.isActive == true) return
        scope.launch {
            callLogRepository.saveCallLog(
                CallLogItem(
                    callerNumber = callerNumber.ifBlank { "رقم غير معروف" },
                    startedAtMillis = System.currentTimeMillis(),
                    durationSeconds = 0,
                    outcome = CallOutcome.MISSED,
                    summary = "مكالمة لم يُرد عليها قبل بدء الرد الآلي.",
                    turns = emptyList(),
                    isTestSession = false
                )
            )
            val enabled = settingsDataStore.getCurrentSettings().isServiceEnabled
            onServiceStateChanged(enabled)
        }
    }

    fun startActiveSession(
        callerNumber: String,
        isTestSession: Boolean,
        enableContinuousMicLoop: Boolean = true
    ) {
        if (sessionJob?.isActive == true) {
            Log.w(TAG, "Session already active; ignoring duplicate start")
            return
        }

        sessionStartTimeMillis = System.currentTimeMillis()
        _liveState.value = LiveCallState(
            status = CallResponderStatus.ACTIVE_CALL,
            callerNumber = callerNumber.ifBlank { if (isTestSession) "جلسة اختبار مباشر" else "رقم غير معروف" },
            isTestSession = isTestSession,
            statusNote = "تم فتح الخط — جاري نطق رسالة الترحيب...",
            turns = emptyList()
        )

        sessionJob = scope.launch {
            val settings = settingsDataStore.getCurrentSettings()
            try {
                audioCaptureEngine.configureCallAudioRouting(
                    useSpeakerphone = if (isTestSession) true else settings.useSpeakerphoneForReply
                )
                audioCaptureEngine.startLevelMonitoring(this)

                // 1. Greet the caller via TTS
                val greetingTurn = ConversationTurn(
                    id = UUID.randomUUID().toString(),
                    speaker = Speaker.ASSISTANT,
                    text = settings.greetingMessage
                )
                appendTurn(greetingTurn)
                updateStatus(
                    CallResponderStatus.SPEAKING,
                    "المجيب يتحدث: رسالة الترحيب..."
                )
                ttsAudioEngine.speakAndAwait(
                    text = settings.greetingMessage,
                    speechRate = settings.ttsSpeechRate,
                    pitch = settings.ttsPitch
                )

                // 2. Continuous voice listening loop
                if (enableContinuousMicLoop) {
                    runVoiceConversationLoop(settings)
                } else {
                    updateStatus(
                        CallResponderStatus.ACTIVE_CALL,
                        "الجلسة جاهزة — يمكنك التحدث أو إرسال نص تجريبي."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error during active call session", e)
                _liveState.update {
                    it.copy(
                        status = CallResponderStatus.ERROR,
                        errorMessage = e.localizedMessage ?: "حدث خطأ غير متوقع أثناء المكالمة."
                    )
                }
            }
        }
    }

    private suspend fun CoroutineScope.runVoiceConversationLoop(settings: ResponderSettings) {
        var silentCycles = 0
        while (isActive) {
            _liveState.update {
                it.copy(
                    status = CallResponderStatus.ACTIVE_CALL,
                    isListeningForSpeech = true,
                    partialCallerSpeech = "",
                    statusNote = "جاري الاستماع لصوت المتصل..."
                )
            }

            val listenResult = speechInputEngine.listenOnce(
                localeTag = settings.recognitionLocale,
                onPartialResult = { partial ->
                    _liveState.update { it.copy(partialCallerSpeech = partial) }
                }
            )

            _liveState.update { it.copy(isListeningForSpeech = false, partialCallerSpeech = "") }

            when (listenResult) {
                is SpeechListenResult.Recognized -> {
                    silentCycles = 0
                    handleCallerMessageInternal(listenResult.text, settings)
                }
                is SpeechListenResult.NoSpeech -> {
                    silentCycles++
                    if (!isActive) break
                    delay(350L)
                    if (silentCycles >= 6) {
                        updateStatus(
                            CallResponderStatus.ACTIVE_CALL,
                            "بانتظار حديث المتصل (يمكنك التحدث أو الكتابة أدناه)..."
                        )
                    }
                }
                is SpeechListenResult.Unavailable -> {
                    appendTurn(
                        ConversationTurn(
                            id = UUID.randomUUID().toString(),
                            speaker = Speaker.SYSTEM,
                            text = "${listenResult.reason} يمكنك متابعة الاختبار عبر الإدخال النصي."
                        )
                    )
                    updateStatus(
                        CallResponderStatus.ACTIVE_CALL,
                        "التعرف الصوتي غير متاح هنا — استخدم الإدخال النصي أو أعد المحاولة."
                    )
                    break
                }
            }
        }
    }

    /**
     * Allows injecting a caller utterance manually (useful during live testing or emulator testing).
     */
    fun submitCallerTextTurn(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (sessionJob?.isActive != true) {
            startActiveSession(
                callerNumber = "جلسة اختبار مباشر",
                isTestSession = true,
                enableContinuousMicLoop = false
            )
        }
        scope.launch {
            val settings = settingsDataStore.getCurrentSettings()
            speechInputEngine.cancelListening()
            handleCallerMessageInternal(clean, settings)
        }
    }

    private suspend fun handleCallerMessageInternal(
        callerText: String,
        settings: ResponderSettings
    ) {
        val callerTurn = ConversationTurn(
            id = UUID.randomUUID().toString(),
            speaker = Speaker.CALLER,
            text = callerText
        )
        appendTurn(callerTurn)

        updateStatus(
            CallResponderStatus.PROCESSING_AI,
            "جاري توليد رد ذكي عبر Gemini..."
        )

        val historyBeforeLatest = _liveState.value.turns.dropLast(1)
        val replyResult = aiResponderRepository.generateReply(
            callerInput = callerText,
            history = historyBeforeLatest,
            settings = settings
        )

        val replyText = replyResult.getOrElse { error ->
            Log.e(TAG, "AI reply failed, using fallback", error)
            _liveState.update {
                it.copy(errorMessage = error.localizedMessage ?: "تعذر الاتصال بمحرك الذكاء الاصطناعي.")
            }
            settings.fallbackMessage
        }

        val assistantTurn = ConversationTurn(
            id = UUID.randomUUID().toString(),
            speaker = Speaker.ASSISTANT,
            text = replyText
        )
        appendTurn(assistantTurn)

        updateStatus(
            CallResponderStatus.SPEAKING,
            "بث الرد الصوتي إلى سماعة المكالمة..."
        )
        ttsAudioEngine.speakAndAwait(
            text = replyText,
            speechRate = settings.ttsSpeechRate,
            pitch = settings.ttsPitch
        )

        updateStatus(
            CallResponderStatus.ACTIVE_CALL,
            "جاهز لمداخلة المتصل التالية..."
        )
    }

    fun endActiveSession() {
        val wasRunning = sessionJob != null ||
            (_liveState.value.status != CallResponderStatus.INACTIVE &&
                _liveState.value.status != CallResponderStatus.LISTENING)
        if (!wasRunning) return

        val snapshot = _liveState.value
        val startedAt = if (sessionStartTimeMillis > 0L) sessionStartTimeMillis else System.currentTimeMillis()
        val durationSec = ((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(1)

        sessionJob?.cancel()
        sessionJob = null

        scope.launch {
            try {
                speechInputEngine.cancelListening()
                ttsAudioEngine.stopSpeaking()
                audioCaptureEngine.restoreAudioRouting()
            } catch (e: Exception) {
                Log.w(TAG, "Cleanup warning when ending session", e)
            }

            val settings = settingsDataStore.getCurrentSettings()
            if (snapshot.turns.isNotEmpty()) {
                val summary = aiResponderRepository.summarizeCall(snapshot.turns, settings)
                val outcome = when {
                    snapshot.isTestSession -> CallOutcome.TEST_SESSION
                    snapshot.errorMessage != null && snapshot.turns.none { it.speaker == Speaker.ASSISTANT } -> CallOutcome.ERROR
                    else -> CallOutcome.COMPLETED
                }
                callLogRepository.saveCallLog(
                    CallLogItem(
                        callerNumber = snapshot.callerNumber.ifBlank { "غير معروف" },
                        startedAtMillis = startedAt,
                        durationSeconds = durationSec,
                        outcome = outcome,
                        summary = summary,
                        turns = snapshot.turns,
                        isTestSession = snapshot.isTestSession
                    )
                )
            }

            sessionStartTimeMillis = 0L
            onServiceStateChanged(settings.isServiceEnabled)
        }
    }

    private fun appendTurn(turn: ConversationTurn) {
        _liveState.update { current ->
            current.copy(turns = current.turns + turn)
        }
    }

    private fun updateStatus(status: CallResponderStatus, note: String) {
        _liveState.update { current ->
            current.copy(status = status, statusNote = note)
        }
    }
}
