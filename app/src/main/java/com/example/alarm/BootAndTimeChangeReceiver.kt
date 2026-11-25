package com.example.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.UttoPreferencesRepository
import com.example.widget.SolarSummaryWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Automatically recalculates and reschedules active Utto sunrise/manual alarms and refreshes
 * pinned home screen solar widgets after device reboot, system clock/timezone/date changes,
 * app update, or exact alarm permission grant.
 */
class BootAndTimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val relevant = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED

        if (!relevant) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val repo = UttoPreferencesRepository(appContext)
                val settings = repo.getSettings()
                if (settings.allAlarms.any { it.isEnabled }) {
                    UttoAlarmScheduler.syncAlarmSchedule(appContext, settings)
                }
                SolarSummaryWidgetProvider.requestUpdateAll(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
