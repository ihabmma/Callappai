package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Wraps Android TextToSpeech configured for Arabic voice synthesis and call audio routing.
 */
class TtsAudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "TtsAudioEngine"
        private const val INIT_TIMEOUT_MS = 8_000L
        private const val SPEAK_TIMEOUT_MS = 30_000L
    }

    private var tts: TextToSpeech? = null
    private var initDeferred: CompletableDeferred<Boolean>? = null
    private var isReady = false

    private suspend fun ensureInitialized(): Boolean {
        if (isReady && tts != null) return true

        val existingDeferred = initDeferred
        if (existingDeferred != null && !existingDeferred.isCompleted) {
            return withTimeoutOrNull(INIT_TIMEOUT_MS) { existingDeferred.await() } ?: false
        }

        val deferred = CompletableDeferred<Boolean>()
        initDeferred = deferred

        withContext(Dispatchers.Main) {
            try {
                tts?.shutdown()
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        configureArabicLanguageAndAttributes()
                        isReady = true
                        deferred.complete(true)
                    } else {
                        Log.e(TAG, "TextToSpeech init failed with status=$status")
                        isReady = false
                        deferred.complete(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception creating TextToSpeech", e)
                isReady = false
                deferred.complete(false)
            }
        }

        return withTimeoutOrNull(INIT_TIMEOUT_MS) { deferred.await() } ?: false
    }

    private fun configureArabicLanguageAndAttributes() {
        val engine = tts ?: return
        try {
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(audioAttrs)

            val preferredLocales = listOf(
                Locale("ar", "SA"),
                Locale("ar", "AE"),
                Locale("ar", "EG"),
                Locale("ar")
            )
            for (locale in preferredLocales) {
                val availability = engine.isLanguageAvailable(locale)
                if (availability >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = locale
                    Log.d(TAG, "TTS language set to $locale")
                    return
                }
            }
            Log.w(TAG, "Arabic TTS language not explicitly installed; using default engine locale")
        } catch (e: Exception) {
            Log.e(TAG, "Failed configuring TTS attributes", e)
        }
    }

    suspend fun speakAndAwait(
        text: String,
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f
    ): Boolean {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return false
        if (!ensureInitialized()) return false

        val engine = tts ?: return false
        val utteranceId = UUID.randomUUID().toString()

        val completed = withTimeoutOrNull(SPEAK_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                engine.setSpeechRate(speechRate.coerceIn(0.6f, 1.6f))
                engine.setPitch(pitch.coerceIn(0.7f, 1.4f))

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        Log.d(TAG, "TTS started utterance=$id")
                    }

                    override fun onDone(id: String?) {
                        if (id == utteranceId && continuation.isActive) {
                            continuation.resume(true)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        if (id == utteranceId && continuation.isActive) {
                            continuation.resume(false)
                        }
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        Log.e(TAG, "TTS error utterance=$id code=$errorCode")
                        if (id == utteranceId && continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                })

                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }

                val result = engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                    continuation.resume(false)
                }

                continuation.invokeOnCancellation {
                    try {
                        engine.stop()
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return completed ?: false
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        } finally {
            tts = null
            isReady = false
            initDeferred = null
        }
    }
}
