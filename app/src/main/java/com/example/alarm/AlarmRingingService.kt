package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.R
import com.example.data.SoundPreset
import com.example.data.UttoPreferencesRepository
import com.example.data.UttoSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service (mediaPlayback) responsible for playing wake-up audio via AudioAttributes.USAGE_ALARM
 * (ringing through Silent / DND), applying optional 30-second gradual fade-in, driving vibration,
 * posting the high-priority full-screen alarm notification, and coordinating Snooze / Dismiss.
 */
class AlarmRingingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var fadeJob: Job? = null
    private var vibrator: Vibrator? = null
    private var currentIsTest: Boolean = false
    private var currentAlarmId: String = com.example.data.UttoAlarm.PRIMARY_ALARM_ID

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> {
                handleDismiss()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                handleSnooze()
                return START_NOT_STICKY
            }
            ACTION_START_RINGING, null -> {
                val alarmId = intent?.getStringExtra(UttoAlarmScheduler.EXTRA_ALARM_ID)
                    ?: com.example.data.UttoAlarm.PRIMARY_ALARM_ID
                val label = intent?.getStringExtra(UttoAlarmScheduler.EXTRA_ALARM_LABEL)
                    ?: "Utto Sunrise Alarm"
                val sunriseText = intent?.getStringExtra(UttoAlarmScheduler.EXTRA_SUNRISE_TIME_TEXT)
                    ?: ""
                val isSnooze = intent?.getBooleanExtra(UttoAlarmScheduler.EXTRA_IS_SNOOZE, false) ?: false
                val isTest = intent?.getBooleanExtra(UttoAlarmScheduler.EXTRA_IS_TEST, false) ?: false
                currentAlarmId = alarmId
                currentIsTest = isTest

                startRingingForeground(label, sunriseText, isSnooze, isTest)
            }
        }
        return START_NOT_STICKY
    }

    private fun startRingingForeground(
        label: String,
        sunriseText: String,
        isSnooze: Boolean,
        isTest: Boolean
    ) {
        ensureNotificationChannel()

        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(UttoAlarmScheduler.EXTRA_ALARM_LABEL, label)
            putExtra(UttoAlarmScheduler.EXTRA_SUNRISE_TIME_TEXT, sunriseText)
            putExtra(UttoAlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
            putExtra(UttoAlarmScheduler.EXTRA_IS_TEST, isTest)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            REQ_FULLSCREEN,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozePendingIntent = PendingIntent.getService(
            this,
            REQ_SNOOZE,
            Intent(this, AlarmRingingService::class.java).apply { action = ACTION_SNOOZE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissPendingIntent = PendingIntent.getService(
            this,
            REQ_DISMISS,
            Intent(this, AlarmRingingService::class.java).apply { action = ACTION_DISMISS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = buildString {
            append(label)
            if (sunriseText.isNotBlank()) {
                append(" • Sunrise ")
                append(sunriseText)
            }
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.alarm_notif_title))
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, getString(R.string.alarm_notif_snooze), snoozePendingIntent)
            .addAction(0, getString(R.string.alarm_notif_dismiss), dismissPendingIntent)
            .build()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)

        // Launch full-screen activity directly as well so interactive tests / unlocked screens open immediately
        runCatching { startActivity(fullScreenIntent) }

        serviceScope.launch {
            try {
                val repo = UttoPreferencesRepository(applicationContext)
                val settings = repo.getSettings()
                startAudioPlayback(settings)
                if (settings.vibrateEnabled) {
                    startVibration()
                }
            } finally {
                AlarmWakeLockHolder.release()
            }
        }
    }

    private fun startAudioPlayback(settings: UttoSettings) {
        stopAudioAndVibration()
        val targetVolume = (settings.volumePercent / 100f).coerceIn(0.1f, 1.0f)
        val initialVolume = if (settings.gradualFadeIn) 0.05f else targetVolume

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Request transient exclusive alarm audio focus so other media pauses immediately
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(audioAttributes)
            .setWillPauseWhenDucked(false)
            .build()
        runCatching { audioManager?.requestAudioFocus(focusReq) }
        audioFocusRequest = focusReq

        val player = MediaPlayer()
        val prepared = runCatching {
            player.setAudioAttributes(audioAttributes)
            player.isLooping = true
            configureDataSource(player, settings)
            player.prepare()
            player.setVolume(initialVolume, initialVolume)
            player.start()
            true
        }.getOrElse {
            // Fallback to bundled Fazar Azan ringtone if custom URI is inaccessible
            runCatching {
                player.reset()
                player.setAudioAttributes(audioAttributes)
                player.isLooping = true
                setRawDataSource(player, R.raw.fazar_azan)
                player.prepare()
                player.setVolume(initialVolume, initialVolume)
                player.start()
                true
            }.getOrDefault(false)
        }

        if (prepared) {
            mediaPlayer = player
            if (settings.gradualFadeIn) {
                fadeJob = serviceScope.launch {
                    val steps = 25 // 5 seconds at 200ms intervals
                    for (step in 1..steps) {
                        delay(200L)
                        val fraction = step.toFloat() / steps.toFloat()
                        val v = initialVolume + (targetVolume - initialVolume) * fraction
                        runCatching { mediaPlayer?.setVolume(v, v) }
                    }
                }
            }
        } else {
            runCatching { player.release() }
        }
    }

    private fun configureDataSource(player: MediaPlayer, settings: UttoSettings) {
        when (settings.soundPreset) {
            SoundPreset.CUSTOM_AUDIO -> {
                val uriStr = settings.customSoundUri
                if (!uriStr.isNullOrBlank()) {
                    player.setDataSource(applicationContext, Uri.parse(uriStr))
                } else {
                    setRawDataSource(player, R.raw.fazar_azan)
                }
            }
            else -> {
                val resId = settings.soundPreset.rawResId ?: R.raw.fazar_azan
                setRawDataSource(player, resId)
            }
        }
    }

    private fun setRawDataSource(player: MediaPlayer, rawResId: Int) {
        resources.openRawResourceFd(rawResId).use { afd ->
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        }
    }

    private fun startVibration() {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (!vib.hasVibrator()) return
        vibrator = vib

        val timings = longArrayOf(0, 450, 350, 450, 1200)
        val amplitudes = intArrayOf(0, 140, 0, 210, 0)
        val effect = if (vib.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, 0)
        } else {
            VibrationEffect.createWaveform(timings, 0)
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()
        @Suppress("DEPRECATION")
        vib.vibrate(effect, attrs)
    }

    private fun handleSnooze() {
        stopAudioAndVibration()
        sendBroadcast(Intent(ACTION_RINGING_STOPPED).setPackage(packageName))
        serviceScope.launch {
            val repo = UttoPreferencesRepository(applicationContext)
            val settings = repo.getSettings()
            if (!currentIsTest) {
                val snoozeMillis = UttoAlarmScheduler.scheduleSnooze(applicationContext, settings)
                repo.setSnoozeTriggerMillis(snoozeMillis)
            }
            stopSelfCleanly()
        }
    }

    private fun handleDismiss() {
        stopAudioAndVibration()
        sendBroadcast(Intent(ACTION_RINGING_STOPPED).setPackage(packageName))
        serviceScope.launch {
            val repo = UttoPreferencesRepository(applicationContext)
            val settings = repo.getSettings()
            if (!currentIsTest) {
                repo.setSnoozeTriggerMillis(null)
                val firedAlarm = settings.allAlarms.firstOrNull { it.id == currentAlarmId }
                if (firedAlarm != null && firedAlarm.repeatDays.isEmpty()) {
                    // One-time alarm: disable only this alarm after dismissal
                    repo.setAlarmEnabledById(firedAlarm.id, false)
                }
                val refreshed = repo.getSettings()
                UttoAlarmScheduler.syncAlarmSchedule(applicationContext, refreshed)
            }
            stopSelfCleanly()
        }
    }

    private fun stopAudioAndVibration() {
        fadeJob?.cancel()
        fadeJob = null
        mediaPlayer?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        mediaPlayer = null
        audioFocusRequest?.let { req ->
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            runCatching { audioManager?.abandonAudioFocusRequest(req) }
        }
        audioFocusRequest = null
        vibrator?.let { runCatching { it.cancel() } }
        vibrator = null
    }

    private fun stopSelfCleanly() {
        AlarmWakeLockHolder.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAudioAndVibration()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.alarm_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            setSound(null, null) // Audio is managed directly by AlarmRingingService MediaPlayer
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "utto_sunrise_alarm_channel_v1"
        const val NOTIFICATION_ID = 9001

        const val ACTION_START_RINGING = "com.example.alarm.ACTION_START_RINGING"
        const val ACTION_DISMISS = "com.example.alarm.ACTION_DISMISS"
        const val ACTION_SNOOZE = "com.example.alarm.ACTION_SNOOZE"
        const val ACTION_RINGING_STOPPED = "com.example.alarm.ACTION_RINGING_STOPPED"

        private const val REQ_FULLSCREEN = 9101
        private const val REQ_SNOOZE = 9102
        private const val REQ_DISMISS = 9103
    }
}
