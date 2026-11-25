package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Shared WakeLock bridge ensuring the CPU stays awake from AlarmTriggerReceiver handoff
 * until AlarmRingingService is safely running in foreground with alarm audio started.
 */
object AlarmWakeLockHolder {
    private val lock = Any()
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire(context: Context, timeoutMs: Long = 30_000L) {
        synchronized(lock) {
            val existing = wakeLock
            if (existing != null && existing.isHeld) {
                runCatching { existing.acquire(timeoutMs) }
                return
            }
            val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wl = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Utto::ExactAlarmWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
            runCatching { wl?.acquire(timeoutMs) }
            wakeLock = wl
        }
    }

    fun release() {
        synchronized(lock) {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    runCatching { wl.release() }
                }
            }
            wakeLock = null
        }
    }
}

class AlarmTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AlarmWakeLockHolder.acquire(context, 30_000L)

        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_START_RINGING
            putExtra(
                UttoAlarmScheduler.EXTRA_ALARM_ID,
                intent.getStringExtra(UttoAlarmScheduler.EXTRA_ALARM_ID)
            )
            putExtra(
                UttoAlarmScheduler.EXTRA_ALARM_LABEL,
                intent.getStringExtra(UttoAlarmScheduler.EXTRA_ALARM_LABEL)
            )
            putExtra(
                UttoAlarmScheduler.EXTRA_SUNRISE_TIME_TEXT,
                intent.getStringExtra(UttoAlarmScheduler.EXTRA_SUNRISE_TIME_TEXT)
            )
            putExtra(
                UttoAlarmScheduler.EXTRA_IS_SNOOZE,
                intent.getBooleanExtra(UttoAlarmScheduler.EXTRA_IS_SNOOZE, false)
            )
            putExtra(
                UttoAlarmScheduler.EXTRA_IS_TEST,
                intent.getBooleanExtra(UttoAlarmScheduler.EXTRA_IS_TEST, false)
            )
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        const val ACTION_TRIGGER_ALARM = "com.example.alarm.ACTION_TRIGGER_ALARM"
    }
}
