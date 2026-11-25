package com.example.data

import androidx.annotation.RawRes
import com.example.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID

enum class AlarmMode {
    SUNRISE_RELATIVE,
    MANUAL
}

enum class SoundPreset(
    val id: String,
    val title: String,
    @RawRes val rawResId: Int?
) {
    FAZAR_AZAN(
        id = "fazar_azan",
        title = "Fazar Azan",
        rawResId = R.raw.fazar_azan
    ),
    LOVE_BIRDS(
        id = "love_birds",
        title = "Love Birds",
        rawResId = R.raw.love_birds
    ),
    CATASH(
        id = "catash",
        title = "Catash",
        rawResId = R.raw.catash
    ),
    CUSTOM_AUDIO(
        id = "custom_audio",
        title = "Custom Ringtone",
        rawResId = null
    );

    companion object {
        val bundledPresets: List<SoundPreset> = listOf(FAZAR_AZAN, LOVE_BIRDS, CATASH)

        fun fromId(id: String?): SoundPreset =
            entries.firstOrNull { it.id == id } ?: FAZAR_AZAN
    }
}

/**
 * Represents an individual alarm item (either the primary Sunrise/Sunset alarm or an
 * additional independent alarm).
 *
 * [sunriseOffsetMinutes]:
 *  - positive (e.g. +30) => 30 min BEFORE sunrise
 *  - zero (0) => AT sunrise
 *  - negative (e.g. -15) => 15 min AFTER sunrise
 */
data class UttoAlarm(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "Custom Alarm",
    val isEnabled: Boolean = true,
    val mode: AlarmMode = AlarmMode.MANUAL,
    val sunriseOffsetMinutes: Int = 30,
    val manualHour: Int = 7,
    val manualMinute: Int = 0,
    val repeatDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    ),
    val isPrimary: Boolean = false
) {
    companion object {
        const val PRIMARY_ALARM_ID = "primary_sunrise_alarm"
        val ALL_DAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }
}

data class UttoSettings(
    val isAlarmActive: Boolean = true,
    val alarmMode: AlarmMode = AlarmMode.SUNRISE_RELATIVE,
    val sunriseOffsetMinutes: Int = 30,
    val manualHour: Int = 6,
    val manualMinute: Int = 30,
    val repeatDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    ),
    val extraAlarms: List<UttoAlarm> = emptyList(),
    val latitude: Double = 37.7749,
    val longitude: Double = -122.4194,
    val locationName: String = "San Francisco, CA",
    val isDefaultFallbackLocation: Boolean = true,
    val lastSyncedEpochMillis: Long = System.currentTimeMillis(),
    val soundPreset: SoundPreset = SoundPreset.FAZAR_AZAN,
    val customSoundUri: String? = null,
    val customSoundTitle: String? = null,
    val volumePercent: Int = 80,
    val gradualFadeIn: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val snoozeDurationMinutes: Int = 10,
    val snoozeTriggerEpochMillis: Long? = null,
    val hasCompletedPermissionsFlow: Boolean = false
) {
    val primaryAlarm: UttoAlarm
        get() = UttoAlarm(
            id = UttoAlarm.PRIMARY_ALARM_ID,
            label = "Sunrise Alarm",
            isEnabled = isAlarmActive,
            mode = alarmMode,
            sunriseOffsetMinutes = sunriseOffsetMinutes,
            manualHour = manualHour,
            manualMinute = manualMinute,
            repeatDays = repeatDays,
            isPrimary = true
        )

    val allAlarms: List<UttoAlarm>
        get() = listOf(primaryAlarm) + extraAlarms
}

data class DailySolarInfo(
    val date: LocalDate,
    val civilDawn: LocalTime,
    val sunrise: LocalTime,
    val solarNoon: LocalTime,
    val sunset: LocalTime,
    val civilDusk: LocalTime,
    val daylightMinutes: Int,
    val daylightDeltaMinutes: Int = 0,
    val isPolarDay: Boolean = false,
    val isPolarNight: Boolean = false
)

data class NextAlarmSchedule(
    val triggerDateTime: ZonedDateTime,
    val sunriseForThatDay: LocalTime,
    val isSnoozed: Boolean = false,
    val alarmId: String = UttoAlarm.PRIMARY_ALARM_ID,
    val label: String = ""
)

data class PermissionHealthStatus(
    val hasLocationPermission: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val hasNotificationPermission: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val hasStoragePermission: Boolean = false
) {
    val allHealthy: Boolean
        get() = hasLocationPermission &&
            canScheduleExactAlarms &&
            hasNotificationPermission &&
            isIgnoringBatteryOptimizations

    val readyCount: Int
        get() = listOf(
            hasLocationPermission,
            canScheduleExactAlarms,
            hasNotificationPermission,
            isIgnoringBatteryOptimizations
        ).count { it }
}

data class PresetCity(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

val CuratedOfflineCities = listOf(
    PresetCity("San Francisco, CA", 37.7749, -122.4194),
    PresetCity("New York, NY", 40.7128, -74.0060),
    PresetCity("London, UK", 51.5074, -0.1278),
    PresetCity("Tokyo, Japan", 35.6762, 139.6503),
    PresetCity("Sydney, Australia", -33.8688, 151.2093),
    PresetCity("Bengaluru, India", 12.9716, 77.5946),
    PresetCity("Reykjavik, Iceland", 64.1466, -21.9426),
    PresetCity("Zurich, Switzerland", 47.3769, 8.5417)
)
