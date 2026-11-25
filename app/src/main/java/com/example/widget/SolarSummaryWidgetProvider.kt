package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.alarm.UttoAlarmScheduler
import com.example.data.UttoPreferencesRepository
import com.example.data.UttoSettings
import com.example.solar.NoaaSolarCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Home screen widget mirroring Utto's main Date/Time & Solar summary card:
 * Live Date & Time (via native TextClock), Sunrise, Solar Noon, Sunset, and Day/Night Duration.
 *
 * Updates automatically on placement, resize, midnight rollover, date/timezone/boot changes,
 * and whenever the user updates their location in the app.
 */
class SolarSummaryWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == ACTION_REFRESH_SOLAR_WIDGET ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, SolarSummaryWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                refreshAsync(context, appWidgetManager, ids)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        refreshAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        refreshAsync(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextMidnightRefresh(context.applicationContext)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelMidnightRefresh(context.applicationContext)
    }

    private fun refreshAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgetInstances(appContext, appWidgetManager, appWidgetIds)
                scheduleNextMidnightRefresh(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_SOLAR_WIDGET = "com.example.action.REFRESH_SOLAR_WIDGET"
        private const val REQUEST_CODE_OPEN_APP = 7701
        private const val REQUEST_CODE_MIDNIGHT_REFRESH = 7702

        /**
         * Triggers an immediate asynchronous update for all pinned Utto Solar widgets.
         * Safe to call from ViewModels, BroadcastReceivers, or Application startup.
         */
        fun requestUpdateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, SolarSummaryWidgetProvider::class.java)
            )
            if (ids.isEmpty()) {
                cancelMidnightRefresh(appContext)
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                updateWidgetInstances(appContext, manager, ids)
                scheduleNextMidnightRefresh(appContext)
            }
        }

        private suspend fun updateWidgetInstances(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val settings = runCatching {
                UttoPreferencesRepository(context).getSettings()
            }.getOrElse { UttoSettings() }

            val views = buildRemoteViews(context, settings)
            appWidgetIds.forEach { widgetId ->
                runCatching { appWidgetManager.updateAppWidget(widgetId, views) }
            }
        }

        private fun buildRemoteViews(context: Context, settings: UttoSettings): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_solar_summary)

            // Tapping anywhere on the widget opens MainActivity cleanly
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN_APP,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            val locationLabel = settings.locationName
                .takeIf { it.isNotBlank() }
                ?: context.getString(R.string.widget_location_fallback)
            views.setTextViewText(R.id.widget_location_name, locationLabel)

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)

            val solar = runCatching {
                NoaaSolarCalculator.calculateDay(
                    date = today,
                    latitude = settings.latitude.coerceIn(-89.9, 89.9),
                    longitude = settings.longitude.coerceIn(-180.0, 180.0),
                    zoneId = zone
                )
            }.getOrNull()

            if (solar == null) {
                val placeholder = context.getString(R.string.widget_time_placeholder)
                val durationFallback = context.getString(R.string.widget_duration_placeholder)
                views.setTextViewText(R.id.widget_sunrise_value, placeholder)
                views.setTextViewText(R.id.widget_solar_noon_value, placeholder)
                views.setTextViewText(R.id.widget_sunset_value, placeholder)
                views.setTextViewText(R.id.widget_day_night_summary, durationFallback)
                views.setContentDescription(
                    R.id.widget_root,
                    context.getString(R.string.widget_solar_description)
                )
                return views
            }

            val sunriseText = when {
                solar.isPolarDay -> context.getString(R.string.widget_polar_day)
                solar.isPolarNight -> context.getString(R.string.widget_polar_night)
                else -> UttoAlarmScheduler.formatTime12h(solar.sunrise)
            }
            val solarNoonText = UttoAlarmScheduler.formatTime12h(solar.solarNoon)
            val sunsetText = when {
                solar.isPolarDay -> context.getString(R.string.widget_polar_day)
                solar.isPolarNight -> context.getString(R.string.widget_polar_night)
                else -> UttoAlarmScheduler.formatTime12h(solar.sunset)
            }

            val dayMinutes = solar.daylightMinutes.coerceIn(0, 1440)
            val nightMinutes = (1440 - dayMinutes).coerceIn(0, 1440)
            val daySummary = "${dayMinutes / 60}h ${dayMinutes % 60}m"
            val nightSummary = "${nightMinutes / 60}h ${nightMinutes % 60}m"
            val durationLine = "Day: $daySummary · Night: $nightSummary"

            views.setTextViewText(R.id.widget_sunrise_value, sunriseText)
            views.setTextViewText(R.id.widget_solar_noon_value, solarNoonText)
            views.setTextViewText(R.id.widget_sunset_value, sunsetText)
            views.setTextViewText(R.id.widget_day_night_summary, durationLine)
            views.setContentDescription(
                R.id.widget_root,
                "Utto solar summary for $locationLabel. Sunrise $sunriseText, Solar Noon $solarNoonText, Sunset $sunsetText. $durationLine. Tap to open Utto."
            )

            return views
        }

        /**
         * Schedules a lightweight battery-friendly refresh shortly after local midnight
         * so daily Sunrise, Solar Noon, Sunset, and Day/Night Duration stay accurate every day.
         */
        private fun scheduleNextMidnightRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val zone = ZoneId.systemDefault()
            val nextMidnightTrigger = ZonedDateTime.now(zone)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(zone)
                .plusSeconds(15)
                .toInstant()
                .toEpochMilli()

            val pendingIntent = buildMidnightRefreshPendingIntent(context)
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC,
                    nextMidnightTrigger,
                    pendingIntent
                )
            }
        }

        private fun cancelMidnightRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            runCatching { alarmManager.cancel(buildMidnightRefreshPendingIntent(context)) }
        }

        private fun buildMidnightRefreshPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, SolarSummaryWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_SOLAR_WIDGET
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_MIDNIGHT_REFRESH,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
