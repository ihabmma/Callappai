package com.example.service

import android.util.Log
import com.example.data.local.SettingsDataStore
import com.example.data.local.StorageRetentionManager
import com.example.data.repository.AiResponderRepository
import com.example.data.repository.CallLogRepository
import com.example.data.repository.CallerContextInfo
import com.example.domain.model.CallLogItem
import com.example.domain.model.CallOutcome
import com.example.domain.model.CallResponderStatus
import com.example.domain.model.CallerCategory
import com.example.domain.model.CallerProfile
import com.example.domain.model.ConnectivityPolicy
import com.example.domain.model.ConversationTurn
import com.example.domain.model.HandlingStrategy
import com.example.domain.model.LiveCallState
import com.example.domain.model.ResponderSettings
import com.example.domain.model.Speaker
import com.example.domain.model.StructuredAiReply
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
 * Coordinates the complete AI Call Team pipeline:
 * CallerIdentifier -> AudioCaptureEngine -> SpeechInputEngine -> AiResponderRepository (Online/Offline) ->
 * PhoneActionExecutor (Alarms/Timers/Reminders) -> TtsAudioEngine -> CallOverlayManager ->
 * Auto-Hangup & Self-Learning Profile Update -> StorageRetentionManager.
 */
class CallSessionManager(
    private val settingsDataStore: SettingsDataStore,
    private val aiResponderRepository: AiResponderRepository,
    private val callLogRepository: CallLogRepository,
    private val storageRetentionManager: StorageRetentionManager,
    private val callerIdentifier: CallerIdentifier,
    private val audioCaptureEngine: AudioCaptureEngine,
    private val ttsAudioEngine: TtsAudioEngine,
    private val speechInputEngine: SpeechInputEngine,
    private val phoneActionExecutor: PhoneActionExecutor? = null,
    private val callOverlayManager: CallOverlayManager? = null
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
    private var activeCallerContext: CallerContextInfo = CallerContextInfo(phoneNumber = "")
    private var endedByAiFlag: Boolean = false
    private var usedOfflineEngineFlag: Boolean = false
    private var hangupCallback: (() -> Unit)? = null

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
        scope.launch {
            storageRetentionManager.enforceRetentionPolicy()
        }
    }

    fun registerHangupHandler(handler: (() -> Unit)?) {
        hangupCallback = handler
    }

    fun onServiceStateChanged(enabled: Boolean) {
        if (sessionJob?.isActive == true) return
        _liveState.update {
            it.copy(
                status = if (enabled) CallResponderStatus.LISTENING else CallResponderStatus.INACTIVE,
                statusNote = if (enabled) "الفريق الذكي في انتظار المكالمات الواردة" else "المجيب الآلي متوقف",
                errorMessage = null,
                audioAmplitude = 0f
            )
        }
    }

    fun onIncomingCallRinging(callerNumber: String) {
        if (sessionJob?.isActive == true) return
        scope.launch {
            val identified = callerIdentifier.identifyCaller(callerNumber)
            _liveState.update {
                it.copy(
                    status = CallResponderStatus.RINGING,
                    callerNumber = callerNumber.ifBlank { "رقم غير معروف" },
                    callerName = identified.resolvedName,
                    detectedCategory = identified.savedProfile?.category ?: CallerCategory.UNKNOWN,
                    isTestSession = false,
                    statusNote = if (identified.resolvedName.isNotBlank()) {
                        "مكالمة واردة من ${identified.resolvedName}"
                    } else {
                        "مكالمة واردة — جاري التحضير للرد التلقائي..."
                    },
                    errorMessage = null
                )
            }
        }
    }

    fun onIncomingCallMissed(callerNumber: String) {
        if (sessionJob?.isActive == true) return
        scope.launch {
            val identified = callerIdentifier.identifyCaller(callerNumber)
            callLogRepository.saveCallLog(
                CallLogItem(
                    callerNumber = callerNumber.ifBlank { "رقم غير معروف" },
                    callerName = identified.resolvedName,
                    callerCategory = identified.savedProfile?.category ?: CallerCategory.UNKNOWN,
                    startedAtMillis = System.currentTimeMillis(),
                    durationSeconds = 0,
                    outcome = CallOutcome.MISSED,
                    summary = "مكالمة لم يُرد عليها قبل انتهاء الرنين.",
                    turns = emptyList(),
                    isTestSession = false
                )
            )
            storageRetentionManager.enforceRetentionPolicy()
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
        endedByAiFlag = false
        usedOfflineEngineFlag = false

        val initialLabel = callerNumber.ifBlank {
            if (isTestSession) "جلسة اختبار مباشر" else "رقم غير معروف"
        }

        _liveState.value = LiveCallState(
            status = CallResponderStatus.ACTIVE_CALL,
            callerNumber = initialLabel,
            isTestSession = isTestSession,
            statusNote = "تم فتح الخط — جاري التعرف على المتصل ونطق الترحيب...",
            turns = emptyList()
        )

        sessionJob = scope.launch {
            val settings = settingsDataStore.getCurrentSettings()
            val offlineDefault = settings.connectivityPolicy == ConnectivityPolicy.OFFLINE_ONLY
            usedOfflineEngineFlag = offlineDefault
            try {
                activeCallerContext = callerIdentifier.identifyCaller(initialLabel)
                val initialCategory = activeCallerContext.savedProfile?.category ?: CallerCategory.UNKNOWN

                _liveState.update {
                    it.copy(
                        callerName = activeCallerContext.resolvedName,
                        detectedCategory = initialCategory,
                        isOfflineActive = offlineDefault
                    )
                }

                syncOverlayIfEnabled(settings)

                audioCaptureEngine.configureCallAudioRouting(
                    useSpeakerphone = if (isTestSession) true else settings.useSpeakerphoneForReply
                )
                audioCaptureEngine.startLevelMonitoring(this)

                val personalizedGreeting = buildPersonalizedGreeting(settings, activeCallerContext)
                val greetingTurn = ConversationTurn(
                    id = UUID.randomUUID().toString(),
                    speaker = Speaker.ASSISTANT,
                    text = personalizedGreeting
                )
                appendTurn(greetingTurn)
                updateStatus(
                    CallResponderStatus.SPEAKING,
                    "المتحدث الطبيعي يرحب بالمتصل...",
                    settings
                )
                ttsAudioEngine.speakAndAwait(
                    text = personalizedGreeting,
                    speechRate = settings.ttsSpeechRate,
                    pitch = settings.ttsPitch
                )

                if (enableContinuousMicLoop) {
                    runVoiceConversationLoop(settings)
                } else {
                    updateStatus(
                        CallResponderStatus.ACTIVE_CALL,
                        "الجلسة جاهزة — تحدث أو أرسل جملة تجريبية أدناه.",
                        settings
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
                syncOverlayIfEnabled(settings)
            }
        }
    }

    private fun buildPersonalizedGreeting(
        settings: ResponderSettings,
        contextInfo: CallerContextInfo
    ): String {
        val name = contextInfo.resolvedName
        val isDeflect = contextInfo.savedProfile?.isDeflectMode == true ||
            contextInfo.savedProfile?.category == CallerCategory.SPAM_SALES
        return when {
            isDeflect -> "مرحباً، صاحب الهاتف غير متاح حالياً لاستقبال المكالمات. هل هناك أمر ضروري؟"
            name.isNotBlank() && contextInfo.savedProfile?.category == CallerCategory.FRIEND ->
                "أهلاً $name، منور! صاحب الهاتف مشغول حالياً قليلاً، أخبرني إذا تحب أبلغه بشيء."
            name.isNotBlank() -> "أهلاً بك يا $name. ${settings.greetingMessage}"
            else -> settings.greetingMessage
        }
    }

    private suspend fun CoroutineScope.runVoiceConversationLoop(settings: ResponderSettings) {
        var silentCycles = 0
        val preferOfflineSpeech = settings.connectivityPolicy == ConnectivityPolicy.OFFLINE_ONLY
        while (isActive) {
            _liveState.update {
                it.copy(
                    status = CallResponderStatus.ACTIVE_CALL,
                    isListeningForSpeech = true,
                    partialCallerSpeech = "",
                    statusNote = "جاري الاستماع وفهم المتصل..."
                )
            }
            syncOverlayIfEnabled(settings)

            val listenResult = speechInputEngine.listenOnce(
                localeTag = settings.recognitionLocale,
                preferOffline = preferOfflineSpeech,
                onPartialResult = { partial ->
                    _liveState.update { it.copy(partialCallerSpeech = partial) }
                }
            )

            _liveState.update { it.copy(isListeningForSpeech = false, partialCallerSpeech = "") }

            when (listenResult) {
                is SpeechListenResult.Recognized -> {
                    silentCycles = 0
                    val shouldTerminate = handleCallerMessageInternal(listenResult.text, settings)
                    if (shouldTerminate) break
                }
                is SpeechListenResult.NoSpeech -> {
                    silentCycles++
                    if (!isActive) break
                    delay(350L)
                    if (silentCycles >= 4 && settings.autoHangupEnabled) {
                        val closing = "يبدو أن الخط هادئ، سأنهي المكالمة الآن ونتواصل معك لاحقاً. مع السلامة."
                        appendTurn(
                            ConversationTurn(
                                id = UUID.randomUUID().toString(),
                                speaker = Speaker.ASSISTANT,
                                text = closing
                            )
                        )
                        ttsAudioEngine.speakAndAwait(closing, settings.ttsSpeechRate, settings.ttsPitch)
                        endedByAiFlag = true
                        triggerHangupOrEnd()
                        break
                    }
                }
                is SpeechListenResult.Unavailable -> {
                    appendTurn(
                        ConversationTurn(
                            id = UUID.randomUUID().toString(),
                            speaker = Speaker.SYSTEM,
                            text = "${listenResult.reason} يمكنك متابعة الاختبار عبر الإدخال النصي أو القوالب السريعة."
                        )
                    )
                    updateStatus(
                        CallResponderStatus.ACTIVE_CALL,
                        "التعرف الصوتي غير متاح هنا — استخدم الإدخال النصي أدناه.",
                        settings
                    )
                    break
                }
            }
        }
    }

    fun submitCallerTextTurn(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        scope.launch {
            if (sessionJob?.isActive != true) {
                startActiveSession(
                    callerNumber = "جلسة اختبار مباشر",
                    isTestSession = true,
                    enableContinuousMicLoop = false
                )
                delay(250L)
            }
            val settings = settingsDataStore.getCurrentSettings()
            speechInputEngine.cancelListening()
            handleCallerMessageInternal(clean, settings)
        }
    }

    /**
     * Returns true if the AI decided the call should now end automatically.
     */
    private suspend fun handleCallerMessageInternal(
        callerText: String,
        settings: ResponderSettings
    ): Boolean {
        val callerTurn = ConversationTurn(
            id = UUID.randomUUID().toString(),
            speaker = Speaker.CALLER,
            text = callerText
        )
        appendTurn(callerTurn)

        updateStatus(
            CallResponderStatus.PROCESSING_AI,
            "فريق الذكاء الاصطناعي يحلل المتصل ويصيغ الرد...",
            settings
        )

        val historyBeforeLatest = _liveState.value.turns.dropLast(1)
        val result = aiResponderRepository.generateStructuredReply(
            callerInput = callerText,
            history = historyBeforeLatest,
            settings = settings,
            callerContext = activeCallerContext
        )

        val callerTurnsCount = _liveState.value.turns.count { it.speaker == Speaker.CALLER }
        val reachedTurnLimit = callerTurnsCount >= settings.maxTurnsPerCall

        val reply = result.getOrElse { error ->
            Log.e(TAG, "AI reply failed, using fallback", error)
            _liveState.update {
                it.copy(errorMessage = error.localizedMessage ?: "تعذر الاتصال بمحرك الذكاء الاصطناعي.")
            }
            StructuredAiReply(
                replyText = settings.fallbackMessage,
                detectedCategory = activeCallerContext.savedProfile?.category ?: CallerCategory.UNKNOWN,
                strategy = HandlingStrategy.PROFESSIONAL_CLIENT,
                shouldEndCall = false
            )
        }

        if (reply.isOfflineResponse) {
            usedOfflineEngineFlag = true
        }

        _liveState.update {
            it.copy(
                detectedCategory = reply.detectedCategory,
                activeStrategy = reply.strategy,
                isOfflineActive = reply.isOfflineResponse
            )
        }

        // Execute requested phone action (alarm / timer / reminder) if permitted
        val actionPayload = reply.phoneAction
        if (settings.allowCallerReminders &&
            actionPayload != null &&
            phoneActionExecutor != null
        ) {
            val callerSource = activeCallerContext.resolvedName.ifBlank {
                activeCallerContext.phoneNumber
            }
            val actionOutcome = phoneActionExecutor.execute(actionPayload, callerSource)
            if (actionOutcome.executed && actionOutcome.arabicSummary.isNotBlank()) {
                appendTurn(
                    ConversationTurn(
                        id = UUID.randomUUID().toString(),
                        speaker = Speaker.SYSTEM,
                        text = actionOutcome.arabicSummary
                    )
                )
            }
        }

        if (settings.enableSelfLearning && reply.selfLearningTip.isNotBlank()) {
            callLogRepository.addLearningInsight(
                callerNumber = activeCallerContext.phoneNumber,
                insightText = reply.selfLearningTip
            )
        }

        val assistantTurn = ConversationTurn(
            id = UUID.randomUUID().toString(),
            speaker = Speaker.ASSISTANT,
            text = reply.replyText
        )
        appendTurn(assistantTurn)

        val modeLabel = if (reply.isOfflineResponse) "أوفلاين" else "أونلاين"
        updateStatus(
            CallResponderStatus.SPEAKING,
            "${reply.strategy.arabicLabel} ($modeLabel) — جاري النطق...",
            settings
        )
        ttsAudioEngine.speakAndAwait(
            text = reply.replyText,
            speechRate = settings.ttsSpeechRate,
            pitch = settings.ttsPitch
        )

        val shouldHangup = settings.autoHangupEnabled && (reply.shouldEndCall || reachedTurnLimit)
        if (shouldHangup) {
            endedByAiFlag = true
            appendTurn(
                ConversationTurn(
                    id = UUID.randomUUID().toString(),
                    speaker = Speaker.SYSTEM,
                    text = "تم إنهاء المكالمة تلقائياً بعد إتمام الحوار (${reply.strategy.arabicLabel})."
                )
            )
            triggerHangupOrEnd()
            return true
        }

        updateStatus(
            CallResponderStatus.ACTIVE_CALL,
            "التصنيف: ${reply.detectedCategory.arabicLabel} • $modeLabel • جاهز للرد التالي...",
            settings
        )
        return false
    }

    private fun triggerHangupOrEnd() {
        val isRealCall = !_liveState.value.isTestSession
        if (isRealCall && hangupCallback != null) {
            hangupCallback?.invoke()
        }
        endActiveSession()
    }

    fun endActiveSession() {
        val wasRunning = sessionJob != null ||
            (_liveState.value.status != CallResponderStatus.INACTIVE &&
                _liveState.value.status != CallResponderStatus.LISTENING)
        if (!wasRunning) {
            callOverlayManager?.dismiss()
            return
        }

        val snapshot = _liveState.value
        val startedAt = if (sessionStartTimeMillis > 0L) sessionStartTimeMillis else System.currentTimeMillis()
        val durationSec = ((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(1)

        sessionJob?.cancel()
        sessionJob = null
        callOverlayManager?.dismiss()

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
                        callerName = snapshot.callerName,
                        callerCategory = snapshot.detectedCategory,
                        strategyUsed = snapshot.activeStrategy,
                        startedAtMillis = startedAt,
                        durationSeconds = durationSec,
                        outcome = outcome,
                        summary = summary,
                        turns = snapshot.turns,
                        isTestSession = snapshot.isTestSession,
                        endedByAi = endedByAiFlag,
                        handledOffline = usedOfflineEngineFlag || snapshot.isOfflineActive
                    )
                )

                // Self-learning: update caller profile for real calls so AI recognizes them next time
                if (!snapshot.isTestSession &&
                    snapshot.callerNumber.isNotBlank() &&
                    snapshot.callerNumber != "رقم غير معروف"
                ) {
                    val existing = callLogRepository.getCallerProfile(snapshot.callerNumber)
                    val updatedProfile = CallerProfile(
                        phoneNumber = snapshot.callerNumber,
                        displayName = snapshot.callerName.ifBlank { existing?.displayName.orEmpty() },
                        category = if (snapshot.detectedCategory != CallerCategory.UNKNOWN) {
                            snapshot.detectedCategory
                        } else {
                            existing?.category ?: CallerCategory.UNKNOWN
                        },
                        notes = summary.take(180),
                        callCount = (existing?.callCount ?: 0) + 1,
                        lastCallMillis = System.currentTimeMillis(),
                        isDeflectMode = existing?.isDeflectMode
                            ?: (snapshot.detectedCategory == CallerCategory.SPAM_SALES)
                    )
                    callLogRepository.upsertCallerProfile(updatedProfile)
                }

                // Auto-enforce storage retention policy (e.g., delete > 3 days or excess space)
                storageRetentionManager.enforceRetentionPolicy()
            }

            sessionStartTimeMillis = 0L
            endedByAiFlag = false
            usedOfflineEngineFlag = false
            onServiceStateChanged(settings.isServiceEnabled)
        }
    }

    private fun appendTurn(turn: ConversationTurn) {
        _liveState.update { current ->
            current.copy(turns = current.turns + turn)
        }
    }

    private fun updateStatus(
        status: CallResponderStatus,
        note: String,
        settings: ResponderSettings? = null
    ) {
        _liveState.update { current ->
            current.copy(status = status, statusNote = note)
        }
        if (settings != null) {
            syncOverlayIfEnabled(settings)
        }
    }

    private fun syncOverlayIfEnabled(settings: ResponderSettings) {
        val manager = callOverlayManager ?: return
        if (!settings.floatingOverlayEnabled || !manager.canDrawOverlays()) return
        manager.showOrUpdate(
            state = _liveState.value,
            onHangupClick = { triggerHangupOrEnd() }
        )
    }
}
