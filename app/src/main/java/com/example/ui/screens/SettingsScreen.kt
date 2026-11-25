package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.R
import com.example.data.SoundPreset
import com.example.ui.components.NumericWheelPicker
import com.example.ui.theme.CloudSecondary
import com.example.ui.theme.DawnGold
import com.example.ui.theme.DawnGoldBright
import com.example.ui.theme.HorizonCoral
import com.example.ui.theme.NightDeep
import com.example.ui.theme.NightObsidian
import com.example.ui.theme.NightSurface
import com.example.ui.theme.NightSurfaceElevated
import com.example.ui.theme.ReadyEmerald
import com.example.ui.theme.ReadyEmeraldContainer
import com.example.ui.theme.StarlightMist
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.viewmodel.UttoUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: UttoUiState,
    onNavigateBack: () -> Unit,
    onSelectSoundPreset: (SoundPreset) -> Unit,
    onCustomAudioSelected: (Uri) -> Unit,
    onStopPreview: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onGradualFadeChange: (Boolean) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onSnoozeDurationChange: (Int) -> Unit,
    onRefreshPermissionsHealth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCustomSnoozeSheet by remember { mutableStateOf(false) }
    var ringtoneDropdownExpanded by remember { mutableStateOf(false) }

    // Stop any ringtone auto-preview when leaving this screen
    DisposableEffect(Unit) {
        onDispose { onStopPreview() }
    }

    // Re-check all permissions & battery optimization status whenever returning from Android Settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefreshPermissionsHealth()
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onCustomAudioSelected(uri)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermissionsHealth()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshPermissionsHealth()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header with Back Arrow next to "Sound & Reliability" (no subtitle)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = NightSurfaceElevated,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                        modifier = Modifier.size(48.dp)
                    ) {
                        IconButton(
                            onClick = {
                                onStopPreview()
                                onNavigateBack()
                            },
                            modifier = Modifier.testTag("settings_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = StarlightMist
                            )
                        }
                    }

                    Text(
                        text = "Sound & Reliability",
                        style = MaterialTheme.typography.headlineMedium,
                        color = StarlightMist
                    )
                }
            }

            // Ringtone Card (Dropdown + Auto-Preview + Custom Ringtone + Volume + 5s Fade-in + Vibration)
            item {
                val settings = uiState.settings
                val selectedRingtoneLabel = if (settings.soundPreset == SoundPreset.CUSTOM_AUDIO) {
                    settings.customSoundTitle ?: settings.soundPreset.title
                } else {
                    settings.soundPreset.title
                }

                val dropdownOptions = remember(settings.customSoundUri, settings.customSoundTitle) {
                    buildList {
                        addAll(SoundPreset.bundledPresets)
                        if (!settings.customSoundUri.isNullOrBlank()) {
                            add(SoundPreset.CUSTOM_AUDIO)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = NightSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_sound_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = StarlightMist
                        )

                        // Dropdown displaying only currently selected ringtone; selecting auto-plays preview
                        ExposedDropdownMenuBox(
                            expanded = ringtoneDropdownExpanded,
                            onExpandedChange = { ringtoneDropdownExpanded = !ringtoneDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = NightDeep,
                                border = BorderStroke(
                                    1.dp,
                                    if (ringtoneDropdownExpanded) DawnGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                                ),
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("ringtone_dropdown_trigger")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = DawnGoldBright,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = selectedRingtoneLabel,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = StarlightMist,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = if (ringtoneDropdownExpanded) {
                                            Icons.Rounded.KeyboardArrowUp
                                        } else {
                                            Icons.Rounded.KeyboardArrowDown
                                        },
                                        contentDescription = "Select ringtone",
                                        tint = CloudSecondary
                                    )
                                }
                            }

                            ExposedDropdownMenu(
                                expanded = ringtoneDropdownExpanded,
                                onDismissRequest = { ringtoneDropdownExpanded = false },
                                modifier = Modifier.background(NightSurfaceElevated)
                            ) {
                                dropdownOptions.forEach { preset ->
                                    val isSelected = settings.soundPreset == preset
                                    val itemLabel = if (preset == SoundPreset.CUSTOM_AUDIO) {
                                        settings.customSoundTitle ?: preset.title
                                    } else {
                                        preset.title
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = itemLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (isSelected) DawnGoldBright else StarlightMist,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        trailingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = "Selected",
                                                    tint = DawnGoldBright,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        onClick = {
                                            ringtoneDropdownExpanded = false
                                            onSelectSoundPreset(preset)
                                        },
                                        modifier = Modifier.testTag("ringtone_option_${preset.id}")
                                    )
                                }
                            }
                        }

                        // Choose Custom Ringtone button (no file formats listed)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = NightDeep,
                            border = BorderStroke(1.dp, DawnGold.copy(alpha = 0.42f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { audioPickerLauncher.launch(arrayOf("audio/*")) }
                                .testTag("pick_custom_audio_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AudioFile,
                                    contentDescription = null,
                                    tint = DawnGoldBright,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.settings_pick_custom_audio),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = DawnGoldBright
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Volume Slider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VolumeUp,
                                        contentDescription = null,
                                        tint = DawnGoldBright,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_volume),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = StarlightMist
                                    )
                                }
                                Text(
                                    text = "${settings.volumePercent}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = DawnGoldBright
                                )
                            }
                            Slider(
                                value = settings.volumePercent.toFloat(),
                                onValueChange = { onVolumeChange(it.roundToInt()) },
                                valueRange = 10f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = DawnGoldBright,
                                    activeTrackColor = DawnGold,
                                    inactiveTrackColor = NightDeep
                                ),
                                modifier = Modifier.testTag("alarm_volume_slider")
                            )
                        }

                        // 5-second fade-in Toggle (no descriptive micro-text)
                        SettingToggleRow(
                            icon = Icons.Rounded.GraphicEq,
                            title = stringResource(R.string.settings_gradual_fade),
                            checked = settings.gradualFadeIn,
                            onCheckedChange = onGradualFadeChange,
                            testTag = "gradual_fade_switch"
                        )

                        // Vibration Toggle (no descriptive micro-text)
                        SettingToggleRow(
                            icon = Icons.Rounded.Vibration,
                            title = stringResource(R.string.settings_vibration),
                            checked = settings.vibrateEnabled,
                            onCheckedChange = onVibrateChange,
                            testTag = "vibration_switch"
                        )
                    }
                }
            }

            // Snooze Duration Card
            item {
                val currentSnooze = uiState.settings.snoozeDurationMinutes
                val presets = remember { listOf(5, 10, 15) }
                val isCustom = currentSnooze !in presets

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = NightSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Snooze,
                                    contentDescription = null,
                                    tint = HorizonCoral,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.settings_snooze_section),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = StarlightMist
                                )
                            }
                            Text(
                                text = "$currentSnooze min",
                                style = MaterialTheme.typography.labelMedium,
                                color = DawnGoldBright
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { mins ->
                                val selected = currentSnooze == mins
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) DawnGold else NightDeep,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) DawnGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onSnoozeDurationChange(mins) }
                                        .testTag("snooze_preset_$mins")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${mins}m",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) NightObsidian else StarlightMist
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isCustom) DawnGold else NightDeep,
                                border = BorderStroke(
                                    1.dp,
                                    if (isCustom) DawnGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { showCustomSnoozeSheet = true }
                                    .testTag("snooze_custom_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (isCustom) "${currentSnooze}m ★" else "Custom",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isCustom) NightObsidian else StarlightMist
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // System Permissions & Reliability Health Check
            item {
                val health = uiState.permissionsHealth
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = NightSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_health_section),
                                style = MaterialTheme.typography.titleMedium,
                                color = StarlightMist
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (health.allHealthy) ReadyEmeraldContainer else WarningAmberContainer
                            ) {
                                Text(
                                    text = "${health.readyCount}/4 READY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (health.allHealthy) ReadyEmerald else WarningAmber,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HealthCheckRow(
                            icon = Icons.Rounded.LocationOn,
                            title = "Location",
                            isHealthy = health.hasLocationPermission,
                            healthyLabel = stringResource(R.string.status_granted),
                            actionTag = "health_fix_location",
                            onFixClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        HealthCheckRow(
                            icon = Icons.Rounded.AlarmOn,
                            title = "Exact Alarm",
                            isHealthy = health.canScheduleExactAlarms,
                            healthyLabel = stringResource(R.string.status_ready),
                            actionTag = "health_fix_exact_alarm",
                            onFixClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    runCatching { context.startActivity(intent) }
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        HealthCheckRow(
                            icon = Icons.Rounded.NotificationsActive,
                            title = "Notification",
                            isHealthy = health.hasNotificationPermission,
                            healthyLabel = stringResource(R.string.status_granted),
                            actionTag = "health_fix_notifications",
                            onFixClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    runCatching { context.startActivity(intent) }
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        HealthCheckRow(
                            icon = Icons.Rounded.BatteryChargingFull,
                            title = "Battery Optimization",
                            isHealthy = health.isIgnoringBatteryOptimizations,
                            healthyLabel = stringResource(R.string.status_unrestricted),
                            actionTag = "health_fix_battery",
                            onFixClick = {
                                @SuppressLint("BatteryLife")
                                val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                runCatching { context.startActivity(directIntent) }
                                    .onFailure { runCatching { context.startActivity(fallbackIntent) } }
                            }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showCustomSnoozeSheet) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden }
        )
        var draftMinutes by remember { mutableIntStateOf(uiState.settings.snoozeDurationMinutes) }

        ModalBottomSheet(
            onDismissRequest = { showCustomSnoozeSheet = false },
            sheetState = sheetState,
            dragHandle = null,
            containerColor = NightDeep
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Custom Snooze Duration",
                        style = MaterialTheme.typography.headlineSmall,
                        color = StarlightMist
                    )
                    IconButton(onClick = { showCustomSnoozeSheet = false }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = CloudSecondary
                        )
                    }
                }

                NumericWheelPicker(
                    range = 1..60,
                    selectedValue = draftMinutes,
                    unitSuffix = "min",
                    labelFormatter = { v -> if (v == 60) "1 hour" else "$v min" },
                    onValueSelected = { draftMinutes = it }
                )

                val snoozeLabel = if (draftMinutes == 60) "1 hour" else "$draftMinutes min"
                Button(
                    onClick = {
                        onSnoozeDurationChange(draftMinutes)
                        showCustomSnoozeSheet = false
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DawnGold,
                        contentColor = NightObsidian
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Set Snooze to $snoozeLabel", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HealthCheckRow(
    icon: ImageVector,
    title: String,
    isHealthy: Boolean,
    healthyLabel: String,
    actionTag: String,
    onFixClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isHealthy) ReadyEmerald else WarningAmber,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = StarlightMist,
            modifier = Modifier.weight(1f)
        )
        if (isHealthy) {
            Surface(
                shape = RoundedCornerShape(50),
                color = ReadyEmeraldContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = ReadyEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = healthyLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = ReadyEmerald
                    )
                }
            }
        } else {
            Button(
                onClick = onFixClick,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarningAmberContainer,
                    contentColor = WarningAmber
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag(actionTag)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.status_action_needed),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DawnGoldBright,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = StarlightMist,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = NightObsidian,
                checkedTrackColor = DawnGold,
                uncheckedThumbColor = CloudSecondary,
                uncheckedTrackColor = NightDeep
            )
        )
    }
}
