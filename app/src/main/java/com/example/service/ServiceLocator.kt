package com.example.service

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.local.StorageRetentionManager
import com.example.data.remote.GeminiRestApi
import com.example.data.repository.AiResponderRepository
import com.example.data.repository.CallLogRepository
import com.example.data.repository.DefaultAiResponderRepository
import com.example.data.repository.OfflineArabicResponderEngine

object ServiceLocator {
    @Volatile
    private var initialized = false

    lateinit var settingsDataStore: SettingsDataStore
        private set
    lateinit var callLogRepository: CallLogRepository
        private set
    lateinit var storageRetentionManager: StorageRetentionManager
        private set
    lateinit var offlineArabicResponderEngine: OfflineArabicResponderEngine
        private set
    lateinit var aiResponderRepository: AiResponderRepository
        private set
    lateinit var callerIdentifier: CallerIdentifier
        private set
    lateinit var phoneActionExecutor: PhoneActionExecutor
        private set
    lateinit var callOverlayManager: CallOverlayManager
        private set
    lateinit var callSessionManager: CallSessionManager
        private set
    lateinit var ttsAudioEngine: TtsAudioEngine
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            val database = AppDatabase.getInstance(appContext)
            settingsDataStore = SettingsDataStore(appContext)
            callLogRepository = CallLogRepository(
                dao = database.callLogDao(),
                callerProfileDao = database.callerProfileDao(),
                learningInsightDao = database.learningInsightDao(),
                trainedQaDao = database.trainedQaDao(),
                scheduledReminderDao = database.scheduledReminderDao()
            )
            storageRetentionManager = StorageRetentionManager(
                context = appContext,
                callLogDao = database.callLogDao(),
                learningInsightDao = database.learningInsightDao(),
                settingsDataStore = settingsDataStore
            )
            offlineArabicResponderEngine = OfflineArabicResponderEngine(callLogRepository)
            aiResponderRepository = DefaultAiResponderRepository(
                context = appContext,
                geminiRestApi = GeminiRestApi.create(),
                offlineEngine = offlineArabicResponderEngine,
                callLogRepository = callLogRepository
            )
            callerIdentifier = CallerIdentifier(appContext, callLogRepository)
            phoneActionExecutor = PhoneActionExecutor(appContext, callLogRepository)
            callOverlayManager = CallOverlayManager(appContext)
            val audioCaptureEngine = AudioCaptureEngine(appContext)
            ttsAudioEngine = TtsAudioEngine(appContext)
            val speechInputEngine = SpeechInputEngine(appContext)
            callSessionManager = CallSessionManager(
                settingsDataStore = settingsDataStore,
                aiResponderRepository = aiResponderRepository,
                callLogRepository = callLogRepository,
                storageRetentionManager = storageRetentionManager,
                callerIdentifier = callerIdentifier,
                audioCaptureEngine = audioCaptureEngine,
                ttsAudioEngine = ttsAudioEngine,
                speechInputEngine = speechInputEngine,
                phoneActionExecutor = phoneActionExecutor,
                callOverlayManager = callOverlayManager
            )
            initialized = true
        }
    }
}
