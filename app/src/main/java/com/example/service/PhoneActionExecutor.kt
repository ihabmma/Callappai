package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import com.example.data.repository.CallLogRepository
import com.example.domain.model.PhoneActionPayload
import com.example.domain.model.PhoneActionType
import com.example.domain.model.ScheduledReminder
import java.util.Calendar

data class ExecutedPhoneActionResult(
    val executed: Boolean,
    val arabicSummary: String
)

/**
 * Executes phone actions (alarms, timers, and scheduled reminders) using native Android
 * AlarmClock intents and AlarmManager, while persisting every scheduled action in Room.
 */
class PhoneActionExecutor(
    private val context: Context,
    private val callLogRepository: CallLogRepository
) {
    companion object {
        private const val TAG = "PhoneActionExecutor"
    }

    suspend fun execute(
        payload: PhoneActionPayload,
        sourceCaller: String
    ): ExecutedPhoneActionResult {
        if (payload.type == PhoneActionType.NONE) {
            return ExecutedPhoneActionResult(false, "")
        }

        val now = System.currentTimeMillis()
        val triggerMillis = computeTriggerTimeMillis(now, payload)
        val cleanTitle = payload.title.trim().ifBlank {
            when (payload.type) {
                PhoneActionType.SET_ALARM -> "منبه من المجيب الذكي"
                PhoneActionType.SET_TIMER -> "مؤقت من المجيب الذكي"
                PhoneActionType.CREATE_REMINDER -> "تذكير مكالمة"
                PhoneActionType.NONE -> ""
            }
        }

        val savedId = callLogRepository.saveScheduledReminder(
            ScheduledReminder(
                title = cleanTitle,
                triggerAtMillis = triggerMillis,
                type = payload.type,
                sourceCaller = sourceCaller,
                isCompleted = false
            )
        )

        scheduleLocalAlarmNotification(
            reminderId = savedId,
            triggerAtMillis = triggerMillis,
            title = cleanTitle,
            actionLabel = payload.type.arabicLabel,
            sourceCaller = sourceCaller
        )

        var systemClockLaunched = false
        when (payload.type) {
            PhoneActionType.SET_ALARM -> {
                val cal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
                val hour = payload.hour?.coerceIn(0, 23) ?: cal.get(Calendar.HOUR_OF_DAY)
                val minute = payload.minute?.coerceIn(0, 59) ?: cal.get(Calendar.MINUTE)
                systemClockLaunched = launchSystemAlarm(hour, minute, cleanTitle)
            }

            PhoneActionType.SET_TIMER -> {
                val delayMinutes = (payload.delayMinutes ?: 5).coerceIn(1, 720)
                systemClockLaunched = launchSystemTimer(delayMinutes * 60, cleanTitle)
            }

            PhoneActionType.CREATE_REMINDER,
            PhoneActionType.NONE -> Unit
        }

        val summary = when (payload.type) {
            PhoneActionType.SET_ALARM -> {
                val cal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
                val h = cal.get(Calendar.HOUR_OF_DAY)
                val m = cal.get(Calendar.MINUTE)
                val formatted = String.format(java.util.Locale.US, "%02d:%02d", h, m)
                if (systemClockLaunched) {
                    "تم ضبط منبه الساعة ($cleanTitle) على $formatted في ساعة الهاتف وتنبيه محلي."
                } else {
                    "تم جدولة منبه محلي ($cleanTitle) على الساعة $formatted."
                }
            }
            PhoneActionType.SET_TIMER -> {
                val mins = (payload.delayMinutes ?: 5).coerceIn(1, 720)
                if (systemClockLaunched) {
                    "تم تشغيل مؤقت لمدة $mins دقيقة ($cleanTitle) في ساعة الهاتف."
                } else {
                    "تم جدولة تنبيه مؤقت بعد $mins دقيقة ($cleanTitle)."
                }
            }
            PhoneActionType.CREATE_REMINDER -> {
                val mins = ((triggerMillis - now) / 60_000L).coerceAtLeast(1L)
                "تم جدولة تذكير ($cleanTitle) بعد $mins دقيقة."
            }
            PhoneActionType.NONE -> ""
        }

        return ExecutedPhoneActionResult(true, summary)
    }

    fun cancelLocalSchedule(reminderId: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ReminderAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                reminderId.toInt(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to cancel alarm for reminderId=$reminderId", e)
        }
    }

    private fun computeTriggerTimeMillis(now: Long, payload: PhoneActionPayload): Long {
        if (payload.hour != null) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, payload.hour.coerceIn(0, 23))
                set(Calendar.MINUTE, (payload.minute ?: 0).coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            return cal.timeInMillis
        }

        val delayMin = (payload.delayMinutes ?: 15).coerceIn(1, 10_080)
        return now + delayMin * 60_000L
    }

    private fun scheduleLocalAlarmNotification(
        reminderId: Long,
        triggerAtMillis: Long,
        title: String,
        actionLabel: String,
        sourceCaller: String
    ) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
                putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_TITLE, title)
                putExtra(ReminderAlarmReceiver.EXTRA_ACTION_LABEL, actionLabel)
                putExtra(ReminderAlarmReceiver.EXTRA_CALLER_SOURCE, sourceCaller)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed scheduling AlarmManager notification", e)
        }
    }

    private fun launchSystemAlarm(hour: Int, minute: Int, message: String): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch system AlarmClock ACTION_SET_ALARM", e)
            false
        }
    }

    private fun launchSystemTimer(seconds: Int, message: String): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch system AlarmClock ACTION_SET_TIMER", e)
            false
        }
    }
}
