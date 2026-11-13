package com.example.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface SpeechListenResult {
    data class Recognized(val text: String) : SpeechListenResult
    data object NoSpeech : SpeechListenResult
    data class Unavailable(val reason: String) : SpeechListenResult
}

/**
 * Wraps Android SpeechRecognizer on the Main thread to transcribe caller speech turn-by-turn.
 */
class SpeechInputEngine(private val context: Context) {

    companion object {
        private const val TAG = "SpeechInputEngine"
        private const val LISTEN_TIMEOUT_MS = 14_000L
    }

    private var activeRecognizer: SpeechRecognizer? = null

    suspend fun listenOnce(
        localeTag: String,
        preferOffline: Boolean = false,
        onPartialResult: (String) -> Unit
    ): SpeechListenResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return SpeechListenResult.Unavailable("صلاحية الميكروفون غير ممنوحة.")
        }

        val available = withContext(Dispatchers.Main) {
            SpeechRecognizer.isRecognitionAvailable(context)
        }
        if (!available) {
            return SpeechListenResult.Unavailable("خدمة التعرف الصوتي غير متوفرة على هذا الجهاز.")
        }

        val result = withTimeoutOrNull(LISTEN_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val recognizer = try {
                        SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create SpeechRecognizer", e)
                        if (continuation.isActive) {
                            continuation.resume(
                                SpeechListenResult.Unavailable("تعذر تهيئة محرك التعرف على الصوت.")
                            )
                        }
                        return@suspendCancellableCoroutine
                    }

                    activeRecognizer = recognizer

                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                            if (partial.isNotBlank()) {
                                onPartialResult(partial)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val finalText = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            cleanupRecognizer(recognizer)
                            if (continuation.isActive) {
                                if (finalText.isNotBlank()) {
                                    continuation.resume(SpeechListenResult.Recognized(finalText))
                                } else {
                                    continuation.resume(SpeechListenResult.NoSpeech)
                                }
                            }
                        }

                        override fun onError(error: Int) {
                            cleanupRecognizer(recognizer)
                            if (!continuation.isActive) return
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                    continuation.resume(SpeechListenResult.NoSpeech)
                                }
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                    continuation.resume(
                                        SpeechListenResult.Unavailable("صلاحية الميكروفون مطلوبة للتعرف الصوتي.")
                                    )
                                }
                                else -> {
                                    Log.w(TAG, "SpeechRecognizer error code=$error")
                                    continuation.resume(SpeechListenResult.NoSpeech)
                                }
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                        if (preferOffline) {
                            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        }
                    }

                    try {
                        recognizer.startListening(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "startListening failed", e)
                        cleanupRecognizer(recognizer)
                        if (continuation.isActive) {
                            continuation.resume(
                                SpeechListenResult.Unavailable("تعذر بدء الاستماع الصوتي.")
                            )
                        }
                    }

                    continuation.invokeOnCancellation {
                        cleanupRecognizer(recognizer)
                    }
                }
            }
        }

        if (result == null) {
            withContext(Dispatchers.Main) {
                activeRecognizer?.let { cleanupRecognizer(it) }
            }
            return SpeechListenResult.NoSpeech
        }

        return result
    }

    fun cancelListening() {
        try {
            activeRecognizer?.cancel()
            activeRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling SpeechRecognizer", e)
        } finally {
            activeRecognizer = null
        }
    }

    private fun cleanupRecognizer(recognizer: SpeechRecognizer) {
        try {
            recognizer.destroy()
        } catch (_: Exception) {
        }
        if (activeRecognizer === recognizer) {
            activeRecognizer = null
        }
    }
}
