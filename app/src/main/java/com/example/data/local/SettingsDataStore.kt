package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.ApiKeyCipher
import com.example.domain.model.AiProviderMode
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
        val SPEAKERPHONE_REPLY = booleanPreferencesKey("speakerphone_reply")
        val AI_PROVIDER_MODE = stringPreferencesKey("ai_provider_mode")
        val CUSTOM_API_KEY_ENC = stringPreferencesKey("custom_api_key_enc")
        val CUSTOM_MODEL_NAME = stringPreferencesKey("custom_model_name")
        val GREETING_MESSAGE = stringPreferencesKey("greeting_message")
        val SYSTEM_PERSONA = stringPreferencesKey("system_persona")
        val FALLBACK_MESSAGE = stringPreferencesKey("fallback_message")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val RECOGNITION_LOCALE = stringPreferencesKey("recognition_locale")
    }

    private val defaults = ResponderSettings()

    val settingsFlow: Flow<ResponderSettings> = context.settingsDataStore.data.map { prefs ->
        val providerMode = prefs[Keys.AI_PROVIDER_MODE]?.let { raw ->
            runCatching { AiProviderMode.valueOf(raw) }.getOrNull()
        } ?: defaults.aiProviderMode

        val encryptedKey = prefs[Keys.CUSTOM_API_KEY_ENC].orEmpty()
        val decryptedKey = if (encryptedKey.isNotBlank()) ApiKeyCipher.decrypt(encryptedKey) else ""

        ResponderSettings(
            isServiceEnabled = prefs[Keys.SERVICE_ENABLED] ?: defaults.isServiceEnabled,
            autoAnswerEnabled = prefs[Keys.AUTO_ANSWER_ENABLED] ?: defaults.autoAnswerEnabled,
            autoAnswerDelaySeconds = (prefs[Keys.AUTO_ANSWER_DELAY] ?: defaults.autoAnswerDelaySeconds)
                .coerceIn(1, 10),
            useSpeakerphoneForReply = prefs[Keys.SPEAKERPHONE_REPLY] ?: defaults.useSpeakerphoneForReply,
            aiProviderMode = providerMode,
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
                ?: defaults.recognitionLocale
        )
    }

    suspend fun getCurrentSettings(): ResponderSettings = settingsFlow.first()

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SERVICE_ENABLED] = enabled
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
            prefs[Keys.SPEAKERPHONE_REPLY] = updated.useSpeakerphoneForReply
            prefs[Keys.AI_PROVIDER_MODE] = updated.aiProviderMode.name
            prefs[Keys.CUSTOM_API_KEY_ENC] = encryptedKey
            prefs[Keys.CUSTOM_MODEL_NAME] = updated.customModelName.trim().ifBlank { defaults.customModelName }
            prefs[Keys.GREETING_MESSAGE] = updated.greetingMessage.trim().ifBlank { defaults.greetingMessage }
            prefs[Keys.SYSTEM_PERSONA] = updated.systemPersona.trim().ifBlank { defaults.systemPersona }
            prefs[Keys.FALLBACK_MESSAGE] = updated.fallbackMessage.trim().ifBlank { defaults.fallbackMessage }
            prefs[Keys.TTS_SPEECH_RATE] = updated.ttsSpeechRate.coerceIn(0.6f, 1.6f)
            prefs[Keys.TTS_PITCH] = updated.ttsPitch.coerceIn(0.7f, 1.4f)
            prefs[Keys.RECOGNITION_LOCALE] = updated.recognitionLocale
        }
    }
}
