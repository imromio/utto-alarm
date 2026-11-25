package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.util.UUID

private val Context.uttoDataStore: DataStore<Preferences> by preferencesDataStore(name = "utto_alarm_prefs")

class UttoPreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_ALARM_ACTIVE = booleanPreferencesKey("is_alarm_active")
        val ALARM_MODE = stringPreferencesKey("alarm_mode")
        val SUNRISE_OFFSET_MINUTES = intPreferencesKey("sunrise_offset_minutes")
        val MANUAL_HOUR = intPreferencesKey("manual_hour")
        val MANUAL_MINUTE = intPreferencesKey("manual_minute")
        val REPEAT_DAYS = stringSetPreferencesKey("repeat_days")
        val REPEAT_DAYS_INITIALIZED = booleanPreferencesKey("repeat_days_initialized")
        val EXTRA_ALARMS_JSON = stringPreferencesKey("extra_alarms_json")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val LOCATION_NAME = stringPreferencesKey("location_name")
        val IS_DEFAULT_LOCATION = booleanPreferencesKey("is_default_location")
        val LAST_SYNCED_MILLIS = longPreferencesKey("last_synced_millis")
        val SOUND_PRESET = stringPreferencesKey("sound_preset")
        val CUSTOM_SOUND_URI = stringPreferencesKey("custom_sound_uri")
        val CUSTOM_SOUND_TITLE = stringPreferencesKey("custom_sound_title")
        val VOLUME_PERCENT = intPreferencesKey("volume_percent")
        val GRADUAL_FADE_IN = booleanPreferencesKey("gradual_fade_in")
        val VIBRATE_ENABLED = booleanPreferencesKey("vibrate_enabled")
        val SNOOZE_DURATION_MINUTES = intPreferencesKey("snooze_duration_minutes")
        val SNOOZE_TRIGGER_MILLIS = longPreferencesKey("snooze_trigger_millis")
        val HAS_COMPLETED_PERMISSIONS_FLOW = booleanPreferencesKey("has_completed_permissions_flow")
    }

    val settingsFlow: Flow<UttoSettings> = context.uttoDataStore.data.map { prefs ->
        val repeatDaysInitialized = prefs[Keys.REPEAT_DAYS_INITIALIZED] ?: false
        val repeatDays = if (!repeatDaysInitialized) {
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            )
        } else {
            prefs[Keys.REPEAT_DAYS]
                ?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: emptySet()
        }

        val mode = prefs[Keys.ALARM_MODE]?.let {
            runCatching { AlarmMode.valueOf(it) }.getOrNull()
        } ?: AlarmMode.SUNRISE_RELATIVE

        val snoozeMillis = prefs[Keys.SNOOZE_TRIGGER_MILLIS]?.takeIf { it > 0L }
        val extraAlarms = decodeExtraAlarms(prefs[Keys.EXTRA_ALARMS_JSON])

        UttoSettings(
            isAlarmActive = prefs[Keys.IS_ALARM_ACTIVE] ?: true,
            alarmMode = mode,
            sunriseOffsetMinutes = prefs[Keys.SUNRISE_OFFSET_MINUTES] ?: 30,
            manualHour = prefs[Keys.MANUAL_HOUR] ?: 6,
            manualMinute = prefs[Keys.MANUAL_MINUTE] ?: 30,
            repeatDays = repeatDays,
            extraAlarms = extraAlarms,
            latitude = prefs[Keys.LATITUDE] ?: 37.7749,
            longitude = prefs[Keys.LONGITUDE] ?: -122.4194,
            locationName = prefs[Keys.LOCATION_NAME] ?: "San Francisco, CA",
            isDefaultFallbackLocation = prefs[Keys.IS_DEFAULT_LOCATION] ?: true,
            lastSyncedEpochMillis = prefs[Keys.LAST_SYNCED_MILLIS] ?: System.currentTimeMillis(),
            soundPreset = SoundPreset.fromId(prefs[Keys.SOUND_PRESET]),
            customSoundUri = prefs[Keys.CUSTOM_SOUND_URI],
            customSoundTitle = prefs[Keys.CUSTOM_SOUND_TITLE],
            volumePercent = (prefs[Keys.VOLUME_PERCENT] ?: 80).coerceIn(10, 100),
            gradualFadeIn = prefs[Keys.GRADUAL_FADE_IN] ?: true,
            vibrateEnabled = prefs[Keys.VIBRATE_ENABLED] ?: true,
            snoozeDurationMinutes = (prefs[Keys.SNOOZE_DURATION_MINUTES] ?: 10).coerceIn(1, 60),
            snoozeTriggerEpochMillis = snoozeMillis,
            hasCompletedPermissionsFlow = prefs[Keys.HAS_COMPLETED_PERMISSIONS_FLOW] ?: false
        )
    }

    suspend fun getSettings(): UttoSettings = settingsFlow.first()

    suspend fun setAlarmActive(active: Boolean) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.IS_ALARM_ACTIVE] = active
            if (!active) {
                prefs.remove(Keys.SNOOZE_TRIGGER_MILLIS)
            }
            prefs[Keys.LAST_SYNCED_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun setSunriseOffset(offsetMinutes: Int) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.ALARM_MODE] = AlarmMode.SUNRISE_RELATIVE.name
            prefs[Keys.SUNRISE_OFFSET_MINUTES] = offsetMinutes.coerceIn(-180, 180)
            prefs.remove(Keys.SNOOZE_TRIGGER_MILLIS)
            prefs[Keys.LAST_SYNCED_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun setManualTime(hour24: Int, minute: Int) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.ALARM_MODE] = AlarmMode.MANUAL.name
            prefs[Keys.MANUAL_HOUR] = hour24.coerceIn(0, 23)
            prefs[Keys.MANUAL_MINUTE] = minute.coerceIn(0, 59)
            prefs.remove(Keys.SNOOZE_TRIGGER_MILLIS)
            prefs[Keys.LAST_SYNCED_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun setAlarmMode(mode: AlarmMode) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.ALARM_MODE] = mode.name
            prefs.remove(Keys.SNOOZE_TRIGGER_MILLIS)
            prefs[Keys.LAST_SYNCED_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun toggleRepeatDay(day: DayOfWeek) {
        context.uttoDataStore.edit { prefs ->
            val current = readPrimaryRepeatDays(prefs)
            val updated = if (day in current) current - day else current + day
            prefs[Keys.REPEAT_DAYS_INITIALIZED] = true
            prefs[Keys.REPEAT_DAYS] = updated.map { it.name }.toSet()
            prefs[Keys.LAST_SYNCED_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun setPrimaryRepeatDays(days: Set<DayOfWeek>) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.REPEAT_DAYS_INITIALIZED] = true
            prefs[Keys.REPEAT_DAYS] = days.map { it.name }.toSet()
            prefs[Keys.LAST_SYNCED_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun savePrimaryAlarm(alarm: UttoAlarm) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.IS_ALARM_ACTIVE] = alarm.isEnabled
            prefs[Keys.ALARM_MODE] = alarm.mode.name
            prefs[Keys.SUNRISE_OFFSET_MINUTES] = alarm.sunriseOffsetMinutes.coerceIn(-180, 180)
            prefs[Keys.MANUAL_HOUR] = alarm.manualHour.coerceIn(0, 23)
            prefs[Keys.MANUAL_MINUTE] = alarm.manualMinute.coerceIn(0, 59)
            prefs[Keys.REPEAT_DAYS_INITIALIZED] = true
            prefs[Keys.REPEAT_DAYS] = alarm.repeatDays.map { it.name }.toSet()
            prefs.remove(Keys.SNOOZE_TRIGGER_MILLIS)
            prefs[Keys.LAST_SYNCED_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun upsertExtraAlarm(alarm: UttoAlarm) {
        context.uttoDataStore.edit { prefs ->
            val current = decodeExtraAlarms(prefs[Keys.EXTRA_ALARMS_JSON]).toMutableList()
            val normalized = alarm.copy(
                id = alarm.id.ifBlank { UUID.randomUUID().toString() },
                isPrimary = false,
                sunriseOffsetMinutes = alarm.sunriseOffsetMinutes.coerceIn(-180, 180),
                manualHour = alarm.manualHour.coerceIn(0, 23),
                manualMinute = alarm.manualMinute.coerceIn(0, 59)
            )
            val existingIdx = current.indexOfFirst { it.id == normalized.id }
            if (existingIdx >= 0) {
                current[existingIdx] = normalized
            } else {
                current.add(normalized)
            }
            prefs[Keys.EXTRA_ALARMS_JSON] = encodeExtraAlarms(current)
            prefs[Keys.LAST_SYNCED_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun deleteExtraAlarm(alarmId: String) {
        context.uttoDataStore.edit { prefs ->
            val current = decodeExtraAlarms(prefs[Keys.EXTRA_ALARMS_JSON])
                .filterNot { it.id == alarmId }
            prefs[Keys.EXTRA_ALARMS_JSON] = encodeExtraAlarms(current)
            prefs[Keys.LAST_SYNCED_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun setAlarmEnabledById(alarmId: String, enabled: Boolean) {
        if (alarmId == UttoAlarm.PRIMARY_ALARM_ID) {
            setAlarmActive(enabled)
        } else {
            context.uttoDataStore.edit { prefs ->
                val updated = decodeExtraAlarms(prefs[Keys.EXTRA_ALARMS_JSON]).map {
                    if (it.id == alarmId) it.copy(isEnabled = enabled) else it
                }
                prefs[Keys.EXTRA_ALARMS_JSON] = encodeExtraAlarms(updated)
                prefs[Keys.LAST_SYNCED_MILLIS] = System.currentTimeMillis()
            }
        }
    }

    suspend fun updateLocation(
        latitude: Double,
        longitude: Double,
        locationName: String,
        isDefaultFallback: Boolean = false
    ) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.LATITUDE] = latitude
            prefs[Keys.LONGITUDE] = longitude
            prefs[Keys.LOCATION_NAME] = locationName
            prefs[Keys.IS_DEFAULT_LOCATION] = isDefaultFallback
            prefs[Keys.LAST_SYNCED_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun setSoundPreset(preset: SoundPreset) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.SOUND_PRESET] = preset.id
        }
    }

    suspend fun setCustomSound(uri: String, title: String) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.SOUND_PRESET] = SoundPreset.CUSTOM_AUDIO.id
            prefs[Keys.CUSTOM_SOUND_URI] = uri
            prefs[Keys.CUSTOM_SOUND_TITLE] = title
        }
    }

    suspend fun setVolumePercent(percent: Int) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.VOLUME_PERCENT] = percent.coerceIn(10, 100)
        }
    }

    suspend fun setGradualFadeIn(enabled: Boolean) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.GRADUAL_FADE_IN] = enabled
        }
    }

    suspend fun setVibrateEnabled(enabled: Boolean) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.VIBRATE_ENABLED] = enabled
        }
    }

    suspend fun setSnoozeDurationMinutes(minutes: Int) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.SNOOZE_DURATION_MINUTES] = minutes.coerceIn(1, 60)
        }
    }

    suspend fun setSnoozeTriggerMillis(epochMillis: Long?) {
        context.uttoDataStore.edit { prefs ->
            if (epochMillis == null) {
                prefs.remove(Keys.SNOOZE_TRIGGER_MILLIS)
            } else {
                prefs[Keys.SNOOZE_TRIGGER_MILLIS] = epochMillis
            }
        }
    }

    suspend fun markSyncedNow() {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNCED_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun setPermissionsFlowCompleted(completed: Boolean = true) {
        context.uttoDataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_PERMISSIONS_FLOW] = completed
        }
    }

    private fun readPrimaryRepeatDays(prefs: Preferences): Set<DayOfWeek> {
        val initialized = prefs[Keys.REPEAT_DAYS_INITIALIZED] ?: false
        return if (!initialized) {
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            )
        } else {
            prefs[Keys.REPEAT_DAYS]
                ?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: emptySet()
        }
    }

    private fun encodeExtraAlarms(alarms: List<UttoAlarm>): String {
        val array = JSONArray()
        for (alarm in alarms) {
            val daysArray = JSONArray()
            alarm.repeatDays.forEach { daysArray.put(it.name) }
            val obj = JSONObject().apply {
                put("id", alarm.id)
                put("label", alarm.label)
                put("enabled", alarm.isEnabled)
                put("mode", alarm.mode.name)
                put("offset", alarm.sunriseOffsetMinutes)
                put("hour", alarm.manualHour)
                put("minute", alarm.manualMinute)
                put("days", daysArray)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun decodeExtraAlarms(raw: String?): List<UttoAlarm> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val daysJson = obj.optJSONArray("days")
                    val days = buildSet {
                        if (daysJson != null) {
                            for (j in 0 until daysJson.length()) {
                                runCatching { DayOfWeek.valueOf(daysJson.getString(j)) }
                                    .getOrNull()
                                    ?.let { add(it) }
                            }
                        }
                    }
                    add(
                        UttoAlarm(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            label = obj.optString("label", "Alarm"),
                            isEnabled = obj.optBoolean("enabled", true),
                            mode = runCatching { AlarmMode.valueOf(obj.optString("mode")) }
                                .getOrDefault(AlarmMode.MANUAL),
                            sunriseOffsetMinutes = obj.optInt("offset", 30).coerceIn(-180, 180),
                            manualHour = obj.optInt("hour", 7).coerceIn(0, 23),
                            manualMinute = obj.optInt("minute", 0).coerceIn(0, 59),
                            repeatDays = days,
                            isPrimary = false
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
