package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.data.AlarmMode
import com.example.data.NextAlarmSchedule
import com.example.data.UttoAlarm
import com.example.data.UttoSettings
import com.example.solar.NoaaSolarCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

object UttoAlarmScheduler {

    private const val PREFS_NAME = "utto_scheduler_tracking"
    private const val KEY_SCHEDULED_CODES = "scheduled_request_codes"
    private const val SNOOZE_REQUEST_CODE = 7099
    private const val SHOW_INTENT_REQUEST_CODE = 7002

    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_ALARM_LABEL = "extra_alarm_label"
    const val EXTRA_SUNRISE_TIME_TEXT = "extra_sunrise_time_text"
    const val EXTRA_IS_SNOOZE = "extra_is_snooze"
    const val EXTRA_IS_TEST = "extra_is_test"

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Computes the earliest upcoming alarm across ALL enabled alarms (primary + additional),
     * or returns an active snooze if it fires sooner.
     */
    fun computeNextAlarm(
        settings: UttoSettings,
        now: ZonedDateTime = ZonedDateTime.now()
    ): NextAlarmSchedule? {
        val candidates = computeAllUpcomingAlarms(settings, now)
        return candidates.minByOrNull { it.triggerDateTime.toInstant().toEpochMilli() }
    }

    private fun computeAllUpcomingAlarms(
        settings: UttoSettings,
        now: ZonedDateTime = ZonedDateTime.now()
    ): List<NextAlarmSchedule> {
        val zone = now.zone
        val candidates = mutableListOf<NextAlarmSchedule>()

        val snoozeMillis = settings.snoozeTriggerEpochMillis
        if (snoozeMillis != null && snoozeMillis > now.toInstant().toEpochMilli()) {
            val snoozeZdt = Instant.ofEpochMilli(snoozeMillis).atZone(zone)
            val solar = NoaaSolarCalculator.calculateDay(
                date = snoozeZdt.toLocalDate(),
                latitude = settings.latitude,
                longitude = settings.longitude,
                zoneId = zone,
                includeDelta = false
            )
            candidates.add(
                NextAlarmSchedule(
                    triggerDateTime = snoozeZdt,
                    sunriseForThatDay = solar.sunrise,
                    isSnoozed = true,
                    alarmId = UttoAlarm.PRIMARY_ALARM_ID,
                    label = "Snoozed Alarm"
                )
            )
        }

        for (alarm in settings.allAlarms) {
            if (!alarm.isEnabled) continue
            computeNextForAlarm(alarm, settings, now)?.let { candidates.add(it) }
        }
        return candidates
    }

    /**
     * Computes the exact next occurrence for a single [UttoAlarm] aligned to :00.000 seconds.
     * Recalculates daily NOAA sunrise for each candidate day when in Sunrise-Relative mode.
     */
    fun computeNextForAlarm(
        alarm: UttoAlarm,
        settings: UttoSettings,
        now: ZonedDateTime = ZonedDateTime.now()
    ): NextAlarmSchedule? {
        val zone = now.zone
        val today = now.toLocalDate()
        val repeatDays = alarm.repeatDays

        for (dayOffset in 0L..8L) {
            val candidateDate = today.plusDays(dayOffset)
            if (repeatDays.isNotEmpty() && candidateDate.dayOfWeek !in repeatDays) {
                continue
            }
            val solar = NoaaSolarCalculator.calculateDay(
                date = candidateDate,
                latitude = settings.latitude,
                longitude = settings.longitude,
                zoneId = zone,
                includeDelta = false
            )
            val alarmTime = computeAlarmTimeForAlarm(
                alarm = alarm,
                date = candidateDate,
                latitude = settings.latitude,
                longitude = settings.longitude,
                sunriseForDate = solar.sunrise,
                zoneId = zone
            )
            val candidateZdt = ZonedDateTime.of(candidateDate, alarmTime, zone)
            // Fire strictly in the future without skipping near-term minutes (e.g. 2 seconds away)
            if (candidateZdt.isAfter(now)) {
                return NextAlarmSchedule(
                    triggerDateTime = candidateZdt,
                    sunriseForThatDay = solar.sunrise,
                    isSnoozed = false,
                    alarmId = alarm.id,
                    label = buildAlarmLabel(alarm, false)
                )
            }
        }
        return null
    }

    fun computeAlarmTimeForAlarm(
        alarm: UttoAlarm,
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        sunriseForDate: LocalTime? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): LocalTime {
        return when (alarm.mode) {
            AlarmMode.MANUAL -> LocalTime.of(alarm.manualHour, alarm.manualMinute, 0, 0)
            AlarmMode.SUNRISE_RELATIVE -> {
                val sunrise = sunriseForDate ?: NoaaSolarCalculator.calculateDay(
                    date = date,
                    latitude = latitude,
                    longitude = longitude,
                    zoneId = zoneId,
                    includeDelta = false
                ).sunrise
                sunrise.minusMinutes(alarm.sunriseOffsetMinutes.toLong()).withSecond(0).withNano(0)
            }
        }
    }

    fun computeAlarmTimeForDate(
        settings: UttoSettings,
        date: LocalDate,
        sunriseForDate: LocalTime? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): LocalTime = computeAlarmTimeForAlarm(
        alarm = settings.primaryAlarm,
        date = date,
        latitude = settings.latitude,
        longitude = settings.longitude,
        sunriseForDate = sunriseForDate,
        zoneId = zoneId
    )

    /**
     * Synchronizes ALL enabled alarms with Android's AlarmManager:
     * - Cancels any stale / disabled alarm PendingIntents.
     * - Schedules the earliest upcoming alarm with AlarmManager.setAlarmClock() (highest Doze priority,
     *   zero batching delay, displays system alarm icon).
     * - Schedules any additional enabled alarms with setExactAndAllowWhileIdle() using distinct
     *   per-alarm request codes so back-to-back alarms are never missed.
     */
    fun syncAlarmSchedule(context: Context, settings: UttoSettings): NextAlarmSchedule? {
        val now = ZonedDateTime.now()
        val upcoming = computeAllUpcomingAlarms(settings, now)
            .sortedBy { it.triggerDateTime.toInstant().toEpochMilli() }

        val previouslyScheduled = loadTrackedRequestCodes(context)
        val newlyScheduled = mutableSetOf<Int>()

        upcoming.forEachIndexed { index, schedule ->
            val reqCode = if (schedule.isSnoozed) {
                SNOOZE_REQUEST_CODE
            } else {
                requestCodeForAlarmId(schedule.alarmId)
            }
            newlyScheduled.add(reqCode)

            val sunriseFormatted = formatTime12h(schedule.sunriseForThatDay)
            scheduleExactTrigger(
                context = context,
                requestCode = reqCode,
                triggerAtMillis = schedule.triggerDateTime.toInstant().toEpochMilli(),
                alarmId = schedule.alarmId,
                label = schedule.label.ifBlank { buildAlarmLabel(settings, schedule.isSnoozed) },
                sunriseText = sunriseFormatted,
                isSnooze = schedule.isSnoozed,
                useAlarmClock = index == 0
            )
        }

        // Cancel any previously tracked request codes no longer active
        val toCancel = previouslyScheduled - newlyScheduled
        for (staleCode in toCancel) {
            cancelByRequestCode(context, staleCode)
        }
        saveTrackedRequestCodes(context, newlyScheduled)

        return upcoming.firstOrNull()
    }

    fun scheduleSnooze(context: Context, settings: UttoSettings): Long {
        val triggerMillis = System.currentTimeMillis() + settings.snoozeDurationMinutes * 60_000L
        val zdt = Instant.ofEpochMilli(triggerMillis).atZone(ZoneId.systemDefault())
        val solar = NoaaSolarCalculator.calculateDay(
            date = zdt.toLocalDate(),
            latitude = settings.latitude,
            longitude = settings.longitude,
            zoneId = zdt.zone,
            includeDelta = false
        )
        scheduleExactTrigger(
            context = context,
            requestCode = SNOOZE_REQUEST_CODE,
            triggerAtMillis = triggerMillis,
            alarmId = UttoAlarm.PRIMARY_ALARM_ID,
            label = "Snoozed (${settings.snoozeDurationMinutes}m)",
            sunriseText = formatTime12h(solar.sunrise),
            isSnooze = true,
            useAlarmClock = true
        )
        val updated = loadTrackedRequestCodes(context) + SNOOZE_REQUEST_CODE
        saveTrackedRequestCodes(context, updated)
        return triggerMillis
    }

    fun cancelAlarm(context: Context) {
        val tracked = loadTrackedRequestCodes(context) +
            setOf(requestCodeForAlarmId(UttoAlarm.PRIMARY_ALARM_ID), SNOOZE_REQUEST_CODE)
        for (code in tracked) {
            cancelByRequestCode(context, code)
        }
        saveTrackedRequestCodes(context, emptySet())
    }

    private fun cancelByRequestCode(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildTriggerPendingIntent(context, requestCode, "", "", "", false)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun scheduleExactTrigger(
        context: Context,
        requestCode: Int,
        triggerAtMillis: Long,
        alarmId: String,
        label: String,
        sunriseText: String,
        isSnooze: Boolean,
        useAlarmClock: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerPendingIntent = buildTriggerPendingIntent(
            context = context,
            requestCode = requestCode,
            alarmId = alarmId,
            label = label,
            sunriseText = sunriseText,
            isSnooze = isSnooze
        )
        val showIntent = PendingIntent.getActivity(
            context,
            SHOW_INTENT_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (canScheduleExactAlarms(context)) {
            val scheduled = runCatching {
                if (useAlarmClock) {
                    val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
                    alarmManager.setAlarmClock(clockInfo, triggerPendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        triggerPendingIntent
                    )
                }
                true
            }.getOrDefault(false)

            if (!scheduled) {
                runCatching {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        triggerPendingIntent
                    )
                }.onFailure {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        triggerPendingIntent
                    )
                }
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                triggerPendingIntent
            )
        }
    }

    private fun buildTriggerPendingIntent(
        context: Context,
        requestCode: Int,
        alarmId: String,
        label: String,
        sunriseText: String,
        isSnooze: Boolean
    ): PendingIntent {
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            action = AlarmTriggerReceiver.ACTION_TRIGGER_ALARM
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, label)
            putExtra(EXTRA_SUNRISE_TIME_TEXT, sunriseText)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCodeForAlarmId(alarmId: String): Int {
        return 7100 + (alarmId.hashCode() and 0x7FFF)
    }

    private fun loadTrackedRequestCodes(context: Context): Set<Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SCHEDULED_CODES, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    private fun saveTrackedRequestCodes(context: Context, codes: Set<Int>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_SCHEDULED_CODES, codes.map { it.toString() }.toSet())
            .apply()
    }

    fun buildAlarmLabel(settings: UttoSettings, isSnooze: Boolean): String =
        buildAlarmLabel(settings.primaryAlarm, isSnooze)

    fun buildAlarmLabel(alarm: UttoAlarm, isSnooze: Boolean): String {
        if (isSnooze) return "Snoozed Alarm"
        return when (alarm.mode) {
            AlarmMode.SUNRISE_RELATIVE -> formatSunriseOffsetLabel(alarm.sunriseOffsetMinutes)
            AlarmMode.MANUAL -> if (alarm.isPrimary) "Utto Alarm" else alarm.label.ifBlank { "Utto Alarm" }
        }
    }

    fun formatSunriseOffsetLabel(offsetMinutes: Int): String {
        val absMins = abs(offsetMinutes)
        return when {
            offsetMinutes == 0 -> "At sunrise"
            offsetMinutes > 0 && absMins == 60 -> "1 hour before sunrise"
            offsetMinutes > 0 -> "$absMins min before sunrise"
            absMins == 60 -> "1 hour after sunrise"
            else -> "$absMins min after sunrise"
        }
    }

    fun formatTime12h(time: LocalTime): String {
        val hour12 = when {
            time.hour == 0 -> 12
            time.hour > 12 -> time.hour - 12
            else -> time.hour
        }
        val amPm = if (time.hour < 12) "AM" else "PM"
        return String.format("%d:%02d %s", hour12, time.minute, amPm)
    }
}
