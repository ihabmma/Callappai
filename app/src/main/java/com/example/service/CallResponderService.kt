package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.CallResponderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallResponderService : Service() {

    companion object {
        private const val TAG = "CallResponderService"
        private const val CHANNEL_ID = "call_responder_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.action.START_RESPONDER"
        const val ACTION_STOP = "com.example.action.STOP_RESPONDER"
        const val ACTION_PHONE_STATE_HINT = "com.example.action.PHONE_STATE_HINT"
        const val EXTRA_PHONE_STATE = "extra_phone_state"
        const val EXTRA_INCOMING_NUMBER = "extra_incoming_number"

        fun startService(context: Context) {
            val intent = Intent(context, CallResponderService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallResponderService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var telephonyManager: TelephonyManager? = null
    private var legacyPhoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: Any? = null

    private var autoAnswerJob: Job? = null
    private var statusObserverJob: Job? = null

    private var lastCallState: Int = TelephonyManager.CALL_STATE_IDLE
    private var lastCallerNumber: String = ""
    private var callWasAnswered: Boolean = false
    private var activeCallWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(applicationContext)
        createNotificationChannel()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    ServiceLocator.settingsDataStore.setServiceEnabled(false)
                    ServiceLocator.callSessionManager.endActiveSession()
                    ServiceLocator.callSessionManager.onServiceStateChanged(false)
                    stopSelfSafely()
                }
                return START_NOT_STICKY
            }

            ACTION_PHONE_STATE_HINT -> {
                val stateStr = intent.getStringExtra(EXTRA_PHONE_STATE)
                val number = intent.getStringExtra(EXTRA_INCOMING_NUMBER).orEmpty()
                if (number.isNotBlank()) {
                    lastCallerNumber = number
                }
                val mappedState = when (stateStr) {
                    TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                    TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
                    else -> null
                }
                if (mappedState != null) {
                    ensureForeground(includeMicrophone = false)
                    handleCallStateTransition(mappedState, lastCallerNumber)
                }
                return START_STICKY
            }

            else -> {
                ensureForeground(includeMicrophone = false)
                startMonitoring()
                return START_STICKY
            }
        }
    }

    private fun startMonitoring() {
        ServiceLocator.callSessionManager.onServiceStateChanged(true)
        registerCallStateListener()

        if (statusObserverJob?.isActive != true) {
            statusObserverJob = serviceScope.launch {
                ServiceLocator.callSessionManager.liveState.collect { live ->
                    val statusText = when (live.status) {
                        CallResponderStatus.RINGING -> "مكالمة واردة من ${live.callerNumber}"
                        CallResponderStatus.ACTIVE_CALL -> "الرد الذكي نشط: جاري الاستماع"
                        CallResponderStatus.PROCESSING_AI -> "الذكاء الاصطناعي يجهّز الرد..."
                        CallResponderStatus.SPEAKING -> "بث الرد الصوتي للمتصل..."
                        CallResponderStatus.ERROR -> "تنبيه: ${live.errorMessage ?: "خطأ في الجلسة"}"
                        CallResponderStatus.LISTENING -> "المجيب الذكي نشط — في انتظار المكالمات"
                        CallResponderStatus.INACTIVE -> "المجيب الذكي متوقف"
                    }
                    updateNotification(statusText)
                }
            }
        }
    }

    private fun ensureForeground(includeMicrophone: Boolean) {
        val notification = buildNotification("المجيب الذكي نشط — في انتظار المكالمات")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasMicPerm = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                val type = if (includeMicrophone && hasMicPerm &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "FGS microphone type denied, falling back to dataSync", se)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun registerCallStateListener() {
        val tm = telephonyManager ?: return
        val hasPhonePerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPhonePerm) {
            Log.w(TAG, "READ_PHONE_STATE permission missing; waiting for user grant.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (telephonyCallback != null) return
            try {
                val callback = ModernCallStateCallback { state ->
                    handleCallStateTransition(state, lastCallerNumber)
                }
                tm.registerTelephonyCallback(mainExecutor, callback)
                telephonyCallback = callback
                Log.d(TAG, "Registered TelephonyCallback (API 31+)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed registering TelephonyCallback", e)
            }
        } else {
            registerLegacyPhoneStateListener(tm)
        }
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyPhoneStateListener(tm: TelephonyManager) {
        if (legacyPhoneStateListener != null) return
        try {
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (!phoneNumber.isNullOrBlank()) {
                        lastCallerNumber = phoneNumber
                    }
                    handleCallStateTransition(state, lastCallerNumber)
                }
            }
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            legacyPhoneStateListener = listener
            Log.d(TAG, "Registered legacy PhoneStateListener")
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering legacy PhoneStateListener", e)
        }
    }

    private fun handleCallStateTransition(state: Int, callerNumber: String) {
        if (state == lastCallState) return
        val previousState = lastCallState
        lastCallState = state

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                callWasAnswered = false
                Log.d(TAG, "Incoming call ringing from: $callerNumber")
                ServiceLocator.callSessionManager.onIncomingCallRinging(callerNumber)
                scheduleAutoAnswerIfEnabled()
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                autoAnswerJob?.cancel()
                callWasAnswered = true
                Log.d(TAG, "Call offhook (active), starting AI responder session")
                acquireCallWakeLock()
                ensureForeground(includeMicrophone = true)
                ServiceLocator.callSessionManager.startActiveSession(
                    callerNumber = callerNumber,
                    isTestSession = false,
                    enableContinuousMicLoop = true
                )
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                autoAnswerJob?.cancel()
                releaseCallWakeLock()
                if (previousState == TelephonyManager.CALL_STATE_RINGING && !callWasAnswered) {
                    ServiceLocator.callSessionManager.onIncomingCallMissed(callerNumber)
                } else if (callWasAnswered) {
                    ServiceLocator.callSessionManager.endActiveSession()
                }
                callWasAnswered = false
                lastCallerNumber = ""
                ensureForeground(includeMicrophone = false)
            }
        }
    }

    private fun acquireCallWakeLock() {
        try {
            if (activeCallWakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            activeCallWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$TAG:ActiveCallLock"
            ).apply {
                setReferenceCounted(false)
                acquire(15 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire WakeLock", e)
        }
    }

    private fun releaseCallWakeLock() {
        try {
            activeCallWakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to release WakeLock", e)
        } finally {
            activeCallWakeLock = null
        }
    }

    private fun scheduleAutoAnswerIfEnabled() {
        autoAnswerJob?.cancel()
        autoAnswerJob = serviceScope.launch {
            val settings = ServiceLocator.settingsDataStore.getCurrentSettings()
            if (!settings.isServiceEnabled || !settings.autoAnswerEnabled) return@launch

            delay(settings.autoAnswerDelaySeconds * 1000L)
            if (lastCallState != TelephonyManager.CALL_STATE_RINGING) return@launch

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                ContextCompat.checkSelfPermission(
                    this@CallResponderService,
                    Manifest.permission.ANSWER_PHONE_CALLS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    val telecom = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    @Suppress("DEPRECATION")
                    telecom?.acceptRingingCall()
                    Log.d(TAG, "Auto-answered incoming call via TelecomManager")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed auto-answering incoming call", e)
                }
            } else {
                Log.w(TAG, "Cannot auto-answer: missing ANSWER_PHONE_CALLS or API < 26")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private class ModernCallStateCallback(
        private val onState: (Int) -> Unit
    ) : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            onState(state)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CallResponderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    @Suppress("DEPRECATION")
    private fun unregisterCallListener() {
        val tm = telephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                try {
                    tm.unregisterTelephonyCallback(it)
                } catch (_: Exception) {
                }
            }
            telephonyCallback = null
        } else {
            legacyPhoneStateListener?.let {
                try {
                    tm.listen(it, PhoneStateListener.LISTEN_NONE)
                } catch (_: Exception) {
                }
            }
            legacyPhoneStateListener = null
        }
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        releaseCallWakeLock()
        unregisterCallListener()
        autoAnswerJob?.cancel()
        statusObserverJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
