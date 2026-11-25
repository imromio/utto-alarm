package com.example.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.alarm.UttoAlarmScheduler
import com.example.data.AlarmMode
import com.example.data.DailySolarInfo
import com.example.data.LocationRepository
import com.example.data.NextAlarmSchedule
import com.example.data.PermissionHealthStatus
import com.example.data.PresetCity
import com.example.data.SearchedPlace
import com.example.data.SoundPreset
import com.example.data.UttoAlarm
import com.example.data.UttoPreferencesRepository
import com.example.data.UttoSettings
import com.example.solar.NoaaSolarCalculator
import com.example.widget.SolarSummaryWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

data class AlarmCardItem(
    val alarm: UttoAlarm,
    val displayTime: LocalTime,
    val sunriseForDay: LocalTime,
    val countdownText: String,
    val relationText: String,
    val isSnoozed: Boolean = false
)

data class UttoUiState(
    val settings: UttoSettings = UttoSettings(),
    val now: ZonedDateTime = ZonedDateTime.now(),
    val todaySolar: DailySolarInfo = NoaaSolarCalculator.calculateDay(
        date = LocalDate.now(),
        latitude = 37.7749,
        longitude = -122.4194
    ),
    val multiDaySolar: List<DailySolarInfo> = emptyList(),
    val primaryAlarmItem: AlarmCardItem = AlarmCardItem(
        alarm = UttoSettings().primaryAlarm,
        displayTime = LocalTime.of(6, 0),
        sunriseForDay = LocalTime.of(6, 30),
        countdownText = "",
        relationText = "30 min before sunrise"
    ),
    val extraAlarmItems: List<AlarmCardItem> = emptyList(),
    val nextAlarm: NextAlarmSchedule? = null,
    val countdownText: String = "",
    val syncStatusText: String = "Schedule scanned just now · Offline ready",
    val isRefreshingLocation: Boolean = false,
    val locationFeedback: String? = null,
    val permissionsHealth: PermissionHealthStatus = PermissionHealthStatus(),
    val isPreviewPlaying: Boolean = false,
    val calendarLocationName: String = "San Francisco, CA",
    val calendarForecast: List<DailySolarInfo> = emptyList(),
    val isUsingSearchedCalendarLocation: Boolean = false,
    val isSearchingCalendarLocation: Boolean = false,
    val calendarSearchError: String? = null,
    val hasLoadedPreferences: Boolean = false
)

class UttoViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val prefsRepo = UttoPreferencesRepository(appContext)
    private val locationRepo = LocationRepository(appContext)

    private val clockTicker = MutableStateFlow(ZonedDateTime.now())
    private val isRefreshingLocation = MutableStateFlow(false)
    private val locationFeedback = MutableStateFlow<String?>(null)
    private val permissionsHealth = MutableStateFlow(readPermissionsHealth())
    private val isPreviewPlaying = MutableStateFlow(false)
    private val searchedCalendarPlace = MutableStateFlow<SearchedPlace?>(null)
    private val isSearchingCalendarLocation = MutableStateFlow(false)
    private val calendarSearchError = MutableStateFlow<String?>(null)

    private var previewPlayer: MediaPlayer? = null
    private var previewAutoStopJob: Job? = null

    private data class TransientState(
        val now: ZonedDateTime,
        val isRefreshingLocation: Boolean,
        val locationFeedback: String?,
        val permissionsHealth: PermissionHealthStatus,
        val isPreviewPlaying: Boolean
    )

    private data class CalendarSearchState(
        val searchedPlace: SearchedPlace?,
        val isSearching: Boolean,
        val error: String?
    )

    private val transientFlow = combine(
        clockTicker,
        isRefreshingLocation,
        locationFeedback,
        permissionsHealth,
        isPreviewPlaying
    ) { now, refreshing, feedback, health, preview ->
        TransientState(now, refreshing, feedback, health, preview)
    }

    private val calendarSearchFlow = combine(
        searchedCalendarPlace,
        isSearchingCalendarLocation,
        calendarSearchError
    ) { place, searching, error ->
        CalendarSearchState(place, searching, error)
    }

    val uiState: StateFlow<UttoUiState> = combine(
        prefsRepo.settingsFlow,
        transientFlow,
        calendarSearchFlow
    ) { settings, transient, calSearch ->
        val zone = transient.now.zone
        val today = transient.now.toLocalDate()
        val multiDay = NoaaSolarCalculator.calculateMultiDaySchedule(
            startDate = today,
            days = 14,
            latitude = settings.latitude,
            longitude = settings.longitude,
            zoneId = zone
        )
        val todaySolar = multiDay.firstOrNull() ?: NoaaSolarCalculator.calculateDay(
            date = today,
            latitude = settings.latitude,
            longitude = settings.longitude,
            zoneId = zone
        )
        val nextAlarm = UttoAlarmScheduler.computeNextAlarm(settings, transient.now)
        val countdown = buildCountdownText(nextAlarm != null, nextAlarm, transient.now)
        val syncText = buildSyncStatusText(settings.lastSyncedEpochMillis, transient.now.toInstant().toEpochMilli())

        val primaryItem = buildAlarmCardItem(
            alarm = settings.primaryAlarm,
            settings = settings,
            todaySolar = todaySolar,
            now = transient.now,
            activeSnooze = nextAlarm?.takeIf { it.isSnoozed }
        )
        val extraItems = settings.extraAlarms.map { alarm ->
            buildAlarmCardItem(
                alarm = alarm,
                settings = settings,
                todaySolar = todaySolar,
                now = transient.now,
                activeSnooze = null
            )
        }

        val searched = calSearch.searchedPlace
        val calendarForecast = if (searched != null) {
            NoaaSolarCalculator.calculateMultiDaySchedule(
                startDate = ZonedDateTime.now(searched.zoneId).toLocalDate(),
                days = 14,
                latitude = searched.latitude,
                longitude = searched.longitude,
                zoneId = searched.zoneId
            )
        } else {
            multiDay
        }

        UttoUiState(
            settings = settings,
            now = transient.now,
            todaySolar = todaySolar,
            multiDaySolar = multiDay,
            primaryAlarmItem = primaryItem,
            extraAlarmItems = extraItems,
            nextAlarm = nextAlarm,
            countdownText = countdown,
            syncStatusText = syncText,
            isRefreshingLocation = transient.isRefreshingLocation,
            locationFeedback = transient.locationFeedback,
            permissionsHealth = transient.permissionsHealth,
            isPreviewPlaying = transient.isPreviewPlaying,
            calendarLocationName = searched?.name ?: settings.locationName,
            calendarForecast = calendarForecast,
            isUsingSearchedCalendarLocation = searched != null,
            isSearchingCalendarLocation = calSearch.isSearching,
            calendarSearchError = calSearch.error,
            hasLoadedPreferences = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = UttoUiState()
    )

    init {
        startLiveClockTicker()
        viewModelScope.launch {
            val initial = prefsRepo.getSettings()
            UttoAlarmScheduler.syncAlarmSchedule(appContext, initial)
            SolarSummaryWidgetProvider.requestUpdateAll(appContext)
        }
    }

    private fun startLiveClockTicker() {
        viewModelScope.launch {
            while (true) {
                clockTicker.value = ZonedDateTime.now()
                delay(1_000L)
            }
        }
    }

    private fun buildAlarmCardItem(
        alarm: UttoAlarm,
        settings: UttoSettings,
        todaySolar: DailySolarInfo,
        now: ZonedDateTime,
        activeSnooze: NextAlarmSchedule?
    ): AlarmCardItem {
        val nextForAlarm = if (activeSnooze != null && alarm.isPrimary) {
            activeSnooze
        } else {
            UttoAlarmScheduler.computeNextForAlarm(alarm, settings, now)
        }
        val sunriseForDay = nextForAlarm?.sunriseForThatDay ?: todaySolar.sunrise
        val displayTime = nextForAlarm?.triggerDateTime?.toLocalTime()
            ?: UttoAlarmScheduler.computeAlarmTimeForAlarm(
                alarm = alarm,
                date = todaySolar.date,
                latitude = settings.latitude,
                longitude = settings.longitude,
                sunriseForDate = todaySolar.sunrise,
                zoneId = now.zone
            )
        val countdown = if (!alarm.isEnabled) {
            "Paused"
        } else {
            buildCountdownText(true, nextForAlarm, now)
        }
        val relation = when {
            activeSnooze != null && alarm.isPrimary -> "Snoozed • Toggle off to cancel"
            alarm.mode == AlarmMode.SUNRISE_RELATIVE -> {
                val offsetLabel = UttoAlarmScheduler.formatSunriseOffsetLabel(alarm.sunriseOffsetMinutes)
                "$offsetLabel (${UttoAlarmScheduler.formatTime12h(sunriseForDay)})"
            }
            else -> "Custom time"
        }
        return AlarmCardItem(
            alarm = alarm,
            displayTime = displayTime,
            sunriseForDay = sunriseForDay,
            countdownText = countdown,
            relationText = relation,
            isSnoozed = activeSnooze != null && alarm.isPrimary
        )
    }

    fun refreshPermissionsHealth() {
        permissionsHealth.value = readPermissionsHealth()
    }

    fun markPermissionsFlowCompleted() {
        viewModelScope.launch {
            prefsRepo.setPermissionsFlowCompleted(true)
        }
    }

    private fun readPermissionsHealth(): PermissionHealthStatus {
        val hasLocation = locationRepo.hasLocationPermission()
        val exactAlarms = UttoAlarmScheduler.canScheduleExactAlarms(appContext)
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoringBattery = powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) ?: true
        val storagePerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val hasStorage = ContextCompat.checkSelfPermission(
            appContext,
            storagePerm
        ) == PackageManager.PERMISSION_GRANTED

        return PermissionHealthStatus(
            hasLocationPermission = hasLocation,
            canScheduleExactAlarms = exactAlarms,
            hasNotificationPermission = notifications,
            isIgnoringBatteryOptimizations = ignoringBattery,
            hasStoragePermission = hasStorage
        )
    }

    fun toggleAlarmEnabled(alarmId: String, enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAlarmEnabledById(alarmId, enabled)
            val updated = prefsRepo.getSettings()
            UttoAlarmScheduler.syncAlarmSchedule(appContext, updated)
        }
    }

    fun setAlarmSunriseOffset(alarmId: String, offsetMinutes: Int) {
        viewModelScope.launch {
            if (alarmId == UttoAlarm.PRIMARY_ALARM_ID) {
                prefsRepo.setSunriseOffset(offsetMinutes)
            } else {
                val current = prefsRepo.getSettings().extraAlarms.firstOrNull { it.id == alarmId }
                if (current != null) {
                    prefsRepo.upsertExtraAlarm(
                        current.copy(
                            mode = AlarmMode.SUNRISE_RELATIVE,
                            sunriseOffsetMinutes = offsetMinutes
                        )
                    )
                }
            }
            val updated = prefsRepo.getSettings()
            UttoAlarmScheduler.syncAlarmSchedule(appContext, updated)
        }
    }

    fun toggleAlarmRepeatDay(alarmId: String, day: DayOfWeek) {
        viewModelScope.launch {
            if (alarmId == UttoAlarm.PRIMARY_ALARM_ID) {
                prefsRepo.toggleRepeatDay(day)
            } else {
                val current = prefsRepo.getSettings().extraAlarms.firstOrNull { it.id == alarmId }
                if (current != null) {
                    val updatedDays = if (day in current.repeatDays) {
                        current.repeatDays - day
                    } else {
                        current.repeatDays + day
                    }
                    prefsRepo.upsertExtraAlarm(current.copy(repeatDays = updatedDays))
                }
            }
            val updated = prefsRepo.getSettings()
            UttoAlarmScheduler.syncAlarmSchedule(appContext, updated)
        }
    }

    fun selectAllDaysForAlarm(alarmId: String) {
        viewModelScope.launch {
            val settings = prefsRepo.getSettings()
            if (alarmId == UttoAlarm.PRIMARY_ALARM_ID) {
                val targetDays = if (settings.repeatDays.size == 7) emptySet() else UttoAlarm.ALL_DAYS
                prefsRepo.setPrimaryRepeatDays(targetDays)
            } else {
                val current = settings.extraAlarms.firstOrNull { it.id == alarmId }
                if (current != null) {
                    val targetDays = if (current.repeatDays.size == 7) emptySet() else UttoAlarm.ALL_DAYS
                    prefsRepo.upsertExtraAlarm(current.copy(repeatDays = targetDays))
                }
            }
            val updated = prefsRepo.getSettings()
            UttoAlarmScheduler.syncAlarmSchedule(appContext, updated)
        }
    }

    fun saveAlarm(alarm: UttoAlarm) {
        viewModelScope.launch {
            if (alarm.isPrimary || alarm.id == UttoAlarm.PRIMARY_ALARM_ID) {
                prefsRepo.savePrimaryAlarm(alarm)
            } else {
                // Additional alarms are always completely independent of sunrise/sunset
                prefsRepo.upsertExtraAlarm(alarm.copy(mode = AlarmMode.MANUAL, isPrimary = false))
            }
            val updated = prefsRepo.getSettings()
            UttoAlarmScheduler.syncAlarmSchedule(appContext, updated)
        }
    }

    fun deleteAlarm(alarmId: String) {
        if (alarmId == UttoAlarm.PRIMARY_ALARM_ID) return
        viewModelScope.launch {
            prefsRepo.deleteExtraAlarm(alarmId)
            val updated = prefsRepo.getSettings()
            UttoAlarmScheduler.syncAlarmSchedule(appContext, updated)
        }
    }

    fun refreshGpsLocation() {
        if (isRefreshingLocation.value) return
        viewModelScope.launch {
            isRefreshingLocation.value = true
            locationFeedback.value = null
            val result = locationRepo.fetchCurrentLocation()
            result.onSuccess { loc ->
                prefsRepo.updateLocation(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    locationName = loc.displayName,
                    isDefaultFallback = false
                )
                val updated = prefsRepo.getSettings()
                UttoAlarmScheduler.syncAlarmSchedule(appContext, updated)
                SolarSummaryWidgetProvider.requestUpdateAll(appContext)
                locationFeedback.value = "Updated to ${loc.displayName}"
            }.onFailure { err ->
                locationFeedback.value = err.message ?: "Could not fetch GPS coordinates"
            }
            isRefreshingLocation.value = false
            refreshPermissionsHealth()
        }
    }

    fun selectPresetCity(city: PresetCity) {
        viewModelScope.launch {
            prefsRepo.updateLocation(
                latitude = city.latitude,
                longitude = city.longitude,
                locationName = city.name,
                isDefaultFallback = false
            )
            val updated = prefsRepo.getSettings()
            UttoAlarmScheduler.syncAlarmSchedule(appContext, updated)
            SolarSummaryWidgetProvider.requestUpdateAll(appContext)
            locationFeedback.value = "Location set to ${city.name}"
        }
    }

    fun setCustomCoordinates(latitude: Double, longitude: Double, customName: String?) {
        viewModelScope.launch {
            val safeLat = latitude.coerceIn(-89.9, 89.9)
            val safeLon = longitude.coerceIn(-180.0, 180.0)
            val label = customName?.takeIf { it.isNotBlank() }
                ?: locationRepo.reverseGeocode(safeLat, safeLon)
                ?: LocationRepository.formatCoordinates(safeLat, safeLon)

            prefsRepo.updateLocation(
                latitude = safeLat,
                longitude = safeLon,
                locationName = label,
                isDefaultFallback = false
            )
            val updated = prefsRepo.getSettings()
            UttoAlarmScheduler.syncAlarmSchedule(appContext, updated)
            SolarSummaryWidgetProvider.requestUpdateAll(appContext)
            locationFeedback.value = "Offline solar grid synced for $label"
        }
    }

    fun searchSolarCalendarLocation(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            calendarSearchError.value = "Enter a city or place name"
            return
        }
        viewModelScope.launch {
            isSearchingCalendarLocation.value = true
            calendarSearchError.value = null
            locationRepo.searchPlace(trimmed)
                .onSuccess { place ->
                    searchedCalendarPlace.value = place
                    calendarSearchError.value = null
                }
                .onFailure { err ->
                    calendarSearchError.value = err.message ?: "Location not found"
                }
            isSearchingCalendarLocation.value = false
        }
    }

    fun resetSolarCalendarLocation() {
        searchedCalendarPlace.value = null
        calendarSearchError.value = null
    }

    fun selectSoundPreset(preset: SoundPreset) {
        viewModelScope.launch {
            prefsRepo.setSoundPreset(preset)
            val updated = prefsRepo.getSettings()
            playRingtonePreview(preset, updated.customSoundUri, updated.volumePercent)
        }
    }

    fun setCustomAudioUri(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val title = resolveFileName(uri) ?: "Custom Ringtone"
        viewModelScope.launch {
            prefsRepo.setCustomSound(uri.toString(), title)
            val updated = prefsRepo.getSettings()
            playRingtonePreview(SoundPreset.CUSTOM_AUDIO, uri.toString(), updated.volumePercent)
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIdx)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    fun setVolumePercent(percent: Int) {
        viewModelScope.launch {
            prefsRepo.setVolumePercent(percent)
            val vol = (percent / 100f).coerceIn(0.1f, 1.0f)
            runCatching { previewPlayer?.setVolume(vol, vol) }
        }
    }

    fun setGradualFadeIn(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setGradualFadeIn(enabled)
        }
    }

    fun setVibrateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setVibrateEnabled(enabled)
        }
    }

    fun setSnoozeDuration(minutes: Int) {
        viewModelScope.launch {
            prefsRepo.setSnoozeDurationMinutes(minutes)
        }
    }

    private fun playRingtonePreview(
        preset: SoundPreset,
        customUri: String?,
        volumePercent: Int
    ) {
        stopSoundPreview()
        viewModelScope.launch {
            val vol = (volumePercent / 100f).coerceIn(0.1f, 1.0f)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val player = withContext(Dispatchers.IO) {
                val mp = MediaPlayer()
                val ok = runCatching {
                    mp.setAudioAttributes(attrs)
                    when (preset) {
                        SoundPreset.CUSTOM_AUDIO -> {
                            if (!customUri.isNullOrBlank()) {
                                mp.setDataSource(appContext, Uri.parse(customUri))
                            } else {
                                appContext.resources.openRawResourceFd(R.raw.fazar_azan).use { afd ->
                                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                }
                            }
                        }
                        else -> {
                            val resId = preset.rawResId ?: R.raw.fazar_azan
                            appContext.resources.openRawResourceFd(resId).use { afd ->
                                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            }
                        }
                    }
                    mp.prepare()
                    mp.setVolume(vol, vol)
                    mp.start()
                    true
                }.getOrElse {
                    runCatching { mp.release() }
                    false
                }
                if (ok) mp else null
            }

            if (player != null) {
                previewPlayer = player
                isPreviewPlaying.value = true
                player.setOnCompletionListener {
                    stopSoundPreview()
                }
                previewAutoStopJob = viewModelScope.launch {
                    delay(12_000L)
                    stopSoundPreview()
                }
            }
        }
    }

    fun stopSoundPreview() {
        previewAutoStopJob?.cancel()
        previewAutoStopJob = null
        previewPlayer?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        previewPlayer = null
        isPreviewPlaying.value = false
    }

    override fun onCleared() {
        stopSoundPreview()
        super.onCleared()
    }

    private fun buildCountdownText(
        isActive: Boolean,
        nextAlarm: NextAlarmSchedule?,
        now: ZonedDateTime
    ): String {
        if (!isActive || nextAlarm == null) return "Paused"
        val duration = Duration.between(now, nextAlarm.triggerDateTime)
        if (duration.isNegative || duration.isZero) return "Ringing now"
        val totalMinutes = duration.toMinutes()
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val mins = totalMinutes % 60
        val secs = duration.seconds % 60
        val prefix = if (nextAlarm.isSnoozed) "Snoozed • Rings in" else "Rings in"
        return when {
            days > 0 -> "$prefix ${days}d ${hours}h ${mins}m"
            hours > 0 -> "$prefix ${hours}h ${mins}m"
            mins > 0 -> "$prefix ${mins}m ${secs}s"
            else -> "$prefix ${secs}s"
        }
    }

    private fun buildSyncStatusText(lastSyncedMillis: Long, nowMillis: Long): String {
        val elapsedSec = ((nowMillis - lastSyncedMillis) / 1000L).coerceAtLeast(0L)
        val ago = when {
            elapsedSec < 60 -> "just now"
            elapsedSec < 3600 -> {
                val mins = elapsedSec / 60
                if (mins == 1L) "1 minute ago" else "$mins minutes ago"
            }
            elapsedSec < 86400 -> {
                val hrs = elapsedSec / 3600
                if (hrs == 1L) "1 hour ago" else "$hrs hours ago"
            }
            else -> {
                val days = elapsedSec / 86400
                if (days == 1L) "1 day ago" else "$days days ago"
            }
        }
        return "Schedule scanned $ago · Offline ready"
    }
}
