package com.example.service

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.remote.GeminiRestApi
import com.example.data.repository.AiResponderRepository
import com.example.data.repository.CallLogRepository
import com.example.data.repository.DefaultAiResponderRepository

object ServiceLocator {
    @Volatile
    private var initialized = false

    lateinit var settingsDataStore: SettingsDataStore
        private set
    lateinit var callLogRepository: CallLogRepository
        private set
    lateinit var aiResponderRepository: AiResponderRepository
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
            callLogRepository = CallLogRepository(database.callLogDao())
            aiResponderRepository = DefaultAiResponderRepository(GeminiRestApi.create())
            val audioCaptureEngine = AudioCaptureEngine(appContext)
            ttsAudioEngine = TtsAudioEngine(appContext)
            val speechInputEngine = SpeechInputEngine(appContext)
            callSessionManager = CallSessionManager(
                settingsDataStore = settingsDataStore,
                aiResponderRepository = aiResponderRepository,
                callLogRepository = callLogRepository,
                audioCaptureEngine = audioCaptureEngine,
                ttsAudioEngine = ttsAudioEngine,
                speechInputEngine = speechInputEngine
            )
            initialized = true
        }
    }
}
