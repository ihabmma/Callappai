package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Manages call audio routing (AudioManager) and real-time PCM microphone capture (AudioRecord)
 * to monitor caller audio stream levels safely.
 */
class AudioCaptureEngine(private val context: Context) {

    companion object {
        private const val TAG = "AudioCaptureEngine"
        private const val SAMPLE_RATE_HZ = 16_000
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    private var previousAudioMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    fun configureCallAudioRouting(useSpeakerphone: Boolean) {
        try {
            if (previousAudioMode == null) {
                previousAudioMode = audioManager.mode
                previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
            }

            requestVoiceFocus()
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = useSpeakerphone
            Log.d(TAG, "Audio routing configured: mode=IN_COMMUNICATION, speaker=$useSpeakerphone")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure call audio routing", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestVoiceFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not request audio focus", e)
        }
    }

    fun startLevelMonitoring(scope: CoroutineScope) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO not granted; skipping AudioRecord level monitor")
            return
        }

        if (captureJob?.isActive == true) return

        captureJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val sources = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC
            )

            var record: AudioRecord? = null
            for (source in sources) {
                try {
                    val candidate = AudioRecord(
                        source,
                        SAMPLE_RATE_HZ,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuf
                    )
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        record = candidate
                        break
                    } else {
                        candidate.release()
                    }
                } catch (se: SecurityException) {
                    Log.e(TAG, "SecurityException creating AudioRecord for source $source", se)
                } catch (e: Exception) {
                    Log.w(TAG, "AudioRecord init failed for source $source", e)
                }
            }

            if (record == null) {
                Log.w(TAG, "Unable to initialize AudioRecord on this device")
                _amplitude.value = 0f
                return@launch
            }

            audioRecord = record
            val buffer = ShortArray(minBuf / 2)
            try {
                record.startRecording()
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toDouble()
                            sumSquares += sample * sample
                        }
                        val rms = sqrt(sumSquares / read)
                        val normalized = (rms / 8000.0).toFloat().coerceIn(0f, 1f)
                        _amplitude.value = normalized
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord capture loop ended", e)
            } finally {
                stopAudioRecordInternal()
            }
        }
    }

    fun stopLevelMonitoring() {
        captureJob?.cancel()
        captureJob = null
        stopAudioRecordInternal()
        _amplitude.value = 0f
    }

    private fun stopAudioRecordInternal() {
        try {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
                rec.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }

    @Suppress("DEPRECATION")
    fun restoreAudioRouting() {
        stopLevelMonitoring()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                audioManager.abandonAudioFocus(null)
            }
            previousAudioMode?.let { audioManager.mode = it }
            previousSpeakerphoneOn?.let { audioManager.isSpeakerphoneOn = it }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore audio routing", e)
        } finally {
            previousAudioMode = null
            previousSpeakerphoneOn = null
        }
    }
}
