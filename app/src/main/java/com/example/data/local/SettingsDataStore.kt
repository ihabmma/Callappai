package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.ApiKeyCipher
import com.example.domain.model.AiProviderMode
import com.example.domain.model.ConnectivityPolicy
import com.example.domain.model.ResponderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "responder_settings"
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val AUTO_ANSWER_ENABLED = booleanPreferencesKey("auto_answer_enabled")
        val AUTO_ANSWER_DELAY = intPreferencesKey("auto_answer_delay")
        val AUTO_HANGUP_ENABLED = booleanPreferencesKey("auto_hangup_enabled")
        val MAX_TURNS_PER_CALL = intPreferencesKey("max_turns_per_call")
        val SPEAKERPHONE_REPLY = booleanPreferencesKey("speakerphone_reply")
        val AI_PROVIDER_MODE = stringPreferencesKey("ai_provider_mode")
        val CONNECTIVITY_POLICY = stringPreferencesKey("connectivity_policy")
        val CUSTOM_API_KEY_ENC = stringPreferencesKey("custom_api_key_enc")
        val CUSTOM_MODEL_NAME = stringPreferencesKey("custom_model_name")
        val GREETING_MESSAGE = stringPreferencesKey("greeting_message")
        val SYSTEM_PERSONA = stringPreferencesKey("system_persona")
        val FALLBACK_MESSAGE = stringPreferencesKey("fallback_message")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val RECOGNITION_LOCALE = stringPreferencesKey("recognition_locale")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val MAX_STORED_CALLS = intPreferencesKey("max_stored_calls")
        val ENABLE_SELF_LEARNING = booleanPreferencesKey("enable_self_learning")
        val ENABLE_DIPLOMATIC_DEFLECTION = booleanPreferencesKey("enable_diplomatic_deflection")
        val STEALTH_ZERO_DRAIN = booleanPreferencesKey("stealth_zero_drain")
        val FLOATING_OVERLAY_ENABLED = booleanPreferencesKey("floating_overlay_enabled")
        val AUTO_TRAIN_OFFLINE = booleanPreferencesKey("auto_train_offline")
        val ALLOW_CALLER_REMINDERS = booleanPreferencesKey("allow_caller_reminders")
        val LAST_CLEANUP_MILLIS = longPreferencesKey("last_cleanup_millis")
    }

    private val defaults = ResponderSettings()

    val settingsFlow: Flow<ResponderSettings> = context.settingsDataStore.data.map { prefs ->
        val providerMode = prefs[Keys.AI_PROVIDER_MODE]?.let { raw ->
            runCatching { AiProviderMode.valueOf(raw) }.getOrNull()
        } ?: defaults.aiProviderMode

        val connectivityPolicy = prefs[Keys.CONNECTIVITY_POLICY]?.let { raw ->
            runCatching { ConnectivityPolicy.valueOf(raw) }.getOrNull()
        } ?: defaults.connectivityPolicy

        val encryptedKey = prefs[Keys.CUSTOM_API_KEY_ENC].orEmpty()
        val decryptedKey = if (encryptedKey.isNotBlank()) ApiKeyCipher.decrypt(encryptedKey) else ""

        ResponderSettings(
            isServiceEnabled = prefs[Keys.SERVICE_ENABLED] ?: defaults.isServiceEnabled,
            autoAnswerEnabled = prefs[Keys.AUTO_ANSWER_ENABLED] ?: defaults.autoAnswerEnabled,
            autoAnswerDelaySeconds = (prefs[Keys.AUTO_ANSWER_DELAY] ?: defaults.autoAnswerDelaySeconds)
                .coerceIn(1, 10),
            autoHangupEnabled = prefs[Keys.AUTO_HANGUP_ENABLED] ?: defaults.autoHangupEnabled,
            maxTurnsPerCall = (prefs[Keys.MAX_TURNS_PER_CALL] ?: defaults.maxTurnsPerCall)
                .coerceIn(3, 20),
            useSpeakerphoneForReply = prefs[Keys.SPEAKERPHONE_REPLY] ?: defaults.useSpeakerphoneForReply,
            aiProviderMode = providerMode,
            connectivityPolicy = connectivityPolicy,
            customApiKey = decryptedKey,
            customModelName = prefs[Keys.CUSTOM_MODEL_NAME]?.takeIf { it.isNotBlank() }
                ?: defaults.customModelName,
            greetingMessage = prefs[Keys.GREETING_MESSAGE]?.takeIf { it.isNotBlank() }
                ?: defaults.greetingMessage,
            systemPersona = prefs[Keys.SYSTEM_PERSONA]?.takeIf { it.isNotBlank() }
                ?: defaults.systemPersona,
            fallbackMessage = prefs[Keys.FALLBACK_MESSAGE]?.takeIf { it.isNotBlank() }
                ?: defaults.fallbackMessage,
            ttsSpeechRate = (prefs[Keys.TTS_SPEECH_RATE] ?: defaults.ttsSpeechRate)
                .coerceIn(0.6f, 1.6f),
            ttsPitch = (prefs[Keys.TTS_PITCH] ?: defaults.ttsPitch)
                .coerceIn(0.7f, 1.4f),
            recognitionLocale = prefs[Keys.RECOGNITION_LOCALE]?.takeIf { it.isNotBlank() }
                ?: defaults.recognitionLocale,
            retentionDays = (prefs[Keys.RETENTION_DAYS] ?: defaults.retentionDays)
                .coerceIn(1, 30),
            maxStoredCalls = (prefs[Keys.MAX_STORED_CALLS] ?: defaults.maxStoredCalls)
                .coerceIn(20, 500),
            enableSelfLearning = prefs[Keys.ENABLE_SELF_LEARNING] ?: defaults.enableSelfLearning,
            enableDiplomaticDeflection = prefs[Keys.ENABLE_DIPLOMATIC_DEFLECTION]
                ?: defaults.enableDiplomaticDeflection,
            stealthZeroDrainMode = prefs[Keys.STEALTH_ZERO_DRAIN] ?: defaults.stealthZeroDrainMode,
            floatingOverlayEnabled = prefs[Keys.FLOATING_OVERLAY_ENABLED]
                ?: defaults.floatingOverlayEnabled,
            autoTrainOfflineFromOnline = prefs[Keys.AUTO_TRAIN_OFFLINE]
                ?: defaults.autoTrainOfflineFromOnline,
            allowCallerReminders = prefs[Keys.ALLOW_CALLER_REMINDERS]
                ?: defaults.allowCallerReminders
        )
    }

    val lastCleanupMillisFlow: Flow<Long> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.LAST_CLEANUP_MILLIS] ?: 0L
    }

    suspend fun getCurrentSettings(): ResponderSettings = settingsFlow.first()

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setLastCleanupMillis(timestamp: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.LAST_CLEANUP_MILLIS] = timestamp
        }
    }

    suspend fun updateSettings(updated: ResponderSettings) {
        val encryptedKey = if (updated.customApiKey.isNotBlank()) {
            ApiKeyCipher.encrypt(updated.customApiKey.trim())
        } else {
            ""
        }

        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SERVICE_ENABLED] = updated.isServiceEnabled
            prefs[Keys.AUTO_ANSWER_ENABLED] = updated.autoAnswerEnabled
            prefs[Keys.AUTO_ANSWER_DELAY] = updated.autoAnswerDelaySeconds.coerceIn(1, 10)
            prefs[Keys.AUTO_HANGUP_ENABLED] = updated.autoHangupEnabled
            prefs[Keys.MAX_TURNS_PER_CALL] = updated.maxTurnsPerCall.coerceIn(3, 20)
            prefs[Keys.SPEAKERPHONE_REPLY] = updated.useSpeakerphoneForReply
            prefs[Keys.AI_PROVIDER_MODE] = updated.aiProviderMode.name
            prefs[Keys.CONNECTIVITY_POLICY] = updated.connectivityPolicy.name
            prefs[Keys.CUSTOM_API_KEY_ENC] = encryptedKey
            prefs[Keys.CUSTOM_MODEL_NAME] = updated.customModelName.trim().ifBlank { defaults.customModelName }
            prefs[Keys.GREETING_MESSAGE] = updated.greetingMessage.trim().ifBlank { defaults.greetingMessage }
            prefs[Keys.SYSTEM_PERSONA] = updated.systemPersona.trim().ifBlank { defaults.systemPersona }
            prefs[Keys.FALLBACK_MESSAGE] = updated.fallbackMessage.trim().ifBlank { defaults.fallbackMessage }
            prefs[Keys.TTS_SPEECH_RATE] = updated.ttsSpeechRate.coerceIn(0.6f, 1.6f)
            prefs[Keys.TTS_PITCH] = updated.ttsPitch.coerceIn(0.7f, 1.4f)
            prefs[Keys.RECOGNITION_LOCALE] = updated.recognitionLocale
            prefs[Keys.RETENTION_DAYS] = updated.retentionDays.coerceIn(1, 30)
            prefs[Keys.MAX_STORED_CALLS] = updated.maxStoredCalls.coerceIn(20, 500)
            prefs[Keys.ENABLE_SELF_LEARNING] = updated.enableSelfLearning
            prefs[Keys.ENABLE_DIPLOMATIC_DEFLECTION] = updated.enableDiplomaticDeflection
            prefs[Keys.STEALTH_ZERO_DRAIN] = updated.stealthZeroDrainMode
            prefs[Keys.FLOATING_OVERLAY_ENABLED] = updated.floatingOverlayEnabled
            prefs[Keys.AUTO_TRAIN_OFFLINE] = updated.autoTrainOfflineFromOnline
            prefs[Keys.ALLOW_CALLER_REMINDERS] = updated.allowCallerReminders
        }
    }
}
