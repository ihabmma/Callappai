package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Backup broadcast receiver for ACTION_PHONE_STATE_CHANGED to wake or notify CallResponderService
 * when an incoming call arrives while the responder is enabled.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneStateReceiver"
    }

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        ServiceLocator.init(context.applicationContext)
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER).orEmpty()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = ServiceLocator.settingsDataStore.getCurrentSettings()
                if (settings.isServiceEnabled) {
                    val serviceIntent = Intent(context, CallResponderService::class.java).apply {
                        action = CallResponderService.ACTION_PHONE_STATE_HINT
                        putExtra(CallResponderService.EXTRA_PHONE_STATE, state)
                        putExtra(CallResponderService.EXTRA_INCOMING_NUMBER, incomingNumber)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling phone state broadcast", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
