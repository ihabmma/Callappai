package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores the low-power background call responder service automatically after device reboot
 * or app update whenever the user left the responder enabled.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ServiceLocator.init(context.applicationContext)
                val settings = ServiceLocator.settingsDataStore.getCurrentSettings()
                if (settings.isServiceEnabled) {
                    Log.d(TAG, "Restoring CallResponderService after $action")
                    CallResponderService.startService(context.applicationContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed restoring service on $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
