package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AlarmMode
import com.example.data.CuratedOfflineCities
import com.example.data.LocationRepository
import com.example.data.PresetCity
import com.example.data.UttoAlarm
import com.example.ui.components.NumericWheelPicker
import com.example.ui.components.SolarArcHeroCard
import com.example.ui.components.WheelTimePicker
import com.example.ui.theme.AlertRose
import com.example.ui.theme.CloudSecondary
import com.example.ui.theme.DawnGold
import com.example.ui.theme.DawnGoldBright
import com.example.ui.theme.NightDeep
import com.example.ui.theme.NightObsidian
import com.example.ui.theme.NightSurface
import com.example.ui.theme.NightSurfaceElevated
import com.example.ui.theme.ReadyEmerald
import com.example.ui.theme.StarlightMist
import com.example.viewmodel.AlarmCardItem
import com.example.viewmodel.UttoUiState
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: UttoUiState,
    onToggleAlarmEnabled: (String, Boolean) -> Unit,
    onSetAlarmSunriseOffset: (String, Int) -> Unit,
    onToggleAlarmRepeatDay: (String, DayOfWeek) -> Unit,
    onSelectAllDaysForAlarm: (String) -> Unit,
    onSaveAlarm: (UttoAlarm) -> Unit,
    onDeleteAlarm: (String) -> Unit,
    onRefreshGpsLocation: () -> Unit,
    onSelectPresetCity: (PresetCity) -> Unit,
    onSetCustomCoordinates: (Double, Double, String?) -> Unit,
    onLocationPermissionResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLocationSheet by remember { mutableStateOf(false) }
    var editingManualAlarm by remember { mutableStateOf<UttoAlarm?>(null) }
    var showCustomSunriseOffsetSheet by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        onLocationPermissionResult()
        if (grants.values.any { it }) {
            onRefreshGpsLocation()
        }
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
            // 1. Top Header: "Utto" (Sentence case) and Location
            item {
                HomeTopLocationBar(
                    locationName = uiState.settings.locationName,
                    isRefreshing = uiState.isRefreshingLocation,
                    onLocationChipClick = { showLocationSheet = true },
                    onGpsRefreshClick = {
                        if (uiState.permissionsHealth.hasLocationPermission) {
                            onRefreshGpsLocation()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        }
                    }
                )
            }

            // Optional location feedback banner
            if (!uiState.locationFeedback.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DawnGold.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, DawnGold.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.locationFeedback,
                            style = MaterialTheme.typography.bodySmall,
                            color = DawnGoldBright,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // 2. Main Date/Time & Solar Card
            item {
                SolarArcHeroCard(
                    now = uiState.now,
                    todaySolar = uiState.todaySolar
                )
            }

            // 3. Primary Sunrise Alarm Container
            // Clicking a sunrise chip links directly to sunrise; clicking Edit next to "At rise" opens custom minute offset;
            // clicking top "Edit" opens manual time picker and unlinks from sunrise into standalone mode.
            item {
                AlarmItemCard(
                    item = uiState.primaryAlarmItem,
                    showSunriseOffsetRow = true,
                    onToggleEnabled = { enabled ->
                        onToggleAlarmEnabled(uiState.primaryAlarmItem.alarm.id, enabled)
                    },
                    onEditClick = {
                        // Prefill manual time editor with the currently displayed alarm time so editing feels natural
                        val currentDisplay = uiState.primaryAlarmItem.displayTime
                        editingManualAlarm = uiState.primaryAlarmItem.alarm.copy(
                            manualHour = if (uiState.primaryAlarmItem.alarm.mode == AlarmMode.MANUAL) {
                                uiState.primaryAlarmItem.alarm.manualHour
                            } else {
                                currentDisplay.hour
                            },
                            manualMinute = if (uiState.primaryAlarmItem.alarm.mode == AlarmMode.MANUAL) {
                                uiState.primaryAlarmItem.alarm.manualMinute
                            } else {
                                currentDisplay.minute
                            }
                        )
                    },
                    onSelectSunriseOffset = { offset ->
                        onSetAlarmSunriseOffset(uiState.primaryAlarmItem.alarm.id, offset)
                    },
                    onCustomSunriseOffsetClick = {
                        showCustomSunriseOffsetSheet = true
                    },
                    onToggleRepeatDay = { day ->
                        onToggleAlarmRepeatDay(uiState.primaryAlarmItem.alarm.id, day)
                    },
                    onSelectAllDays = {
                        onSelectAllDaysForAlarm(uiState.primaryAlarmItem.alarm.id)
                    },
                    onDeleteClick = null
                )
            }

            // 4. Additional Independent Alarms Section Header + "+ Add Alarm" button (no extra descriptive box below)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Additional Alarms",
                        style = MaterialTheme.typography.titleMedium,
                        color = StarlightMist
                    )

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = DawnGold,
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                editingManualAlarm = UttoAlarm(
                                    id = UUID.randomUUID().toString(),
                                    label = "Alarm ${uiState.extraAlarmItems.size + 1}",
                                    isEnabled = true,
                                    mode = AlarmMode.MANUAL,
                                    manualHour = 7,
                                    manualMinute = 0,
                                    repeatDays = setOf(
                                        DayOfWeek.MONDAY,
                                        DayOfWeek.TUESDAY,
                                        DayOfWeek.WEDNESDAY,
                                        DayOfWeek.THURSDAY,
                                        DayOfWeek.FRIDAY
                                    ),
                                    isPrimary = false
                                )
                            }
                            .testTag("add_alarm_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = NightObsidian,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Add Alarm",
                                style = MaterialTheme.typography.labelLarge,
                                color = NightObsidian
                            )
                        }
                    }
                }
            }

            // Independent additional alarms (no sunrise/offset options on these cards)
            items(uiState.extraAlarmItems, key = { it.alarm.id }) { extraItem ->
                AlarmItemCard(
                    item = extraItem,
                    showSunriseOffsetRow = false,
                    onToggleEnabled = { enabled ->
                        onToggleAlarmEnabled(extraItem.alarm.id, enabled)
                    },
                    onEditClick = { editingManualAlarm = extraItem.alarm },
                    onSelectSunriseOffset = {},
                    onCustomSunriseOffsetClick = {},
                    onToggleRepeatDay = { day ->
                        onToggleAlarmRepeatDay(extraItem.alarm.id, day)
                    },
                    onSelectAllDays = {
                        onSelectAllDaysForAlarm(extraItem.alarm.id)
                    },
                    onDeleteClick = { onDeleteAlarm(extraItem.alarm.id) }
                )
            }

            // Clean Status Footer (single clean status line only)
            item {
                OfflineSyncFooter(syncStatusText = uiState.syncStatusText)
            }

            // Very subtle Footer Branding at 30% opacity
            item {
                Text(
                    text = "Designed and Developed by Romio",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarlightMist,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp)
                        .alpha(0.30f)
                        .testTag("footer_branding_text")
                )
            }
        }
    }

    // Manual Standalone Time Editor Sheet (for both editing Primary Alarm to unlink from sunrise & Add/Edit Independent Alarms)
    editingManualAlarm?.let { targetAlarm ->
        val isExistingExtra = !targetAlarm.isPrimary &&
            uiState.settings.extraAlarms.any { it.id == targetAlarm.id }

        ManualAlarmEditorSheet(
            initialAlarm = targetAlarm,
            canDelete = isExistingExtra,
            onDismiss = { editingManualAlarm = null },
            onSave = { updated ->
                // Saving from Manual Edit always sets standalone AlarmMode.MANUAL (unlinking from sunrise/sunset)
                onSaveAlarm(updated.copy(mode = AlarmMode.MANUAL))
                editingManualAlarm = null
            },
            onDelete = {
                onDeleteAlarm(targetAlarm.id)
                editingManualAlarm = null
            }
        )
    }

    // Custom Sunrise Offset Editor Sheet (opened from Edit button next to "At rise" on Primary Sunrise Alarm)
    if (showCustomSunriseOffsetSheet) {
        CustomSunriseOffsetSheet(
            currentOffsetMinutes = uiState.primaryAlarmItem.alarm.sunriseOffsetMinutes,
            onDismiss = { showCustomSunriseOffsetSheet = false },
            onSaveOffset = { signedMinutes ->
                onSetAlarmSunriseOffset(uiState.primaryAlarmItem.alarm.id, signedMinutes)
                showCustomSunriseOffsetSheet = false
            }
        )
    }

    if (showLocationSheet) {
        LocationPickerBottomSheet(
            currentName = uiState.settings.locationName,
            currentLat = uiState.settings.latitude,
            currentLon = uiState.settings.longitude,
            onDismiss = { showLocationSheet = false },
            onUseGps = {
                showLocationSheet = false
                if (uiState.permissionsHealth.hasLocationPermission) {
                    onRefreshGpsLocation()
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                }
            },
            onSelectCity = { city ->
                onSelectPresetCity(city)
                showLocationSheet = false
            },
            onApplyCoordinates = { lat, lon, name ->
                onSetCustomCoordinates(lat, lon, name)
                showLocationSheet = false
            }
        )
    }
}

@Composable
private fun HomeTopLocationBar(
    locationName: String,
    isRefreshing: Boolean,
    onLocationChipClick: () -> Unit,
    onGpsRefreshClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Utto",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = StarlightMist
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = NightSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onLocationChipClick)
                    .testTag("location_selector_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = "Select location",
                        tint = DawnGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = locationName,
                        style = MaterialTheme.typography.labelLarge,
                        color = StarlightMist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = NightSurfaceElevated,
                border = BorderStroke(1.dp, DawnGold.copy(alpha = 0.35f)),
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(
                    onClick = onGpsRefreshClick,
                    enabled = !isRefreshing,
                    modifier = Modifier.testTag("gps_refresh_button")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = DawnGold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.GpsFixed,
                            contentDescription = "Refresh GPS coordinates",
                            tint = DawnGoldBright,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmItemCard(
    item: AlarmCardItem,
    showSunriseOffsetRow: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onSelectSunriseOffset: (Int) -> Unit,
    onCustomSunriseOffsetClick: () -> Unit,
    onToggleRepeatDay: (DayOfWeek) -> Unit,
    onSelectAllDays: () -> Unit,
    onDeleteClick: (() -> Unit)?
) {
    val alarm = item.alarm
    val timeOnlyFormatter = remember { DateTimeFormatter.ofPattern("h:mm") }
    val amPmFormatter = remember { DateTimeFormatter.ofPattern("a") }

    val quickOffsets = remember {
        listOf(
            30 to "30m",
            40 to "40m",
            45 to "45m",
            0 to "At rise"
        )
    }
    val presetOffsetValues = remember { setOf(30, 40, 45, 0) }
    val isCustomSunriseOffsetActive = alarm.mode == AlarmMode.SUNRISE_RELATIVE &&
        alarm.sunriseOffsetMinutes !in presetOffsetValues

    val orderedDays = remember {
        listOf(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY
        )
    }

    val allDaysSelected = alarm.repeatDays.size == 7

    val cardTitle = when {
        alarm.isPrimary && alarm.mode == AlarmMode.SUNRISE_RELATIVE -> "Sunrise Alarm"
        alarm.isPrimary && alarm.mode == AlarmMode.MANUAL -> "Utto Alarm"
        else -> alarm.label
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_card_${alarm.id}"),
        shape = RoundedCornerShape(24.dp),
        color = NightSurface,
        border = BorderStroke(
            1.dp,
            if (alarm.isEnabled) DawnGold.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Row: Label + Edit button + (optional Delete) + Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (alarm.mode == AlarmMode.SUNRISE_RELATIVE) {
                            Icons.Rounded.WbTwilight
                        } else {
                            Icons.Rounded.Schedule
                        },
                        contentDescription = null,
                        tint = if (alarm.isEnabled) DawnGoldBright else CloudSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = cardTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (alarm.isEnabled) StarlightMist else CloudSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = NightDeep,
                        border = BorderStroke(1.dp, DawnGold.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onEditClick)
                            .testTag("edit_alarm_button_${alarm.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit alarm",
                                tint = DawnGoldBright,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelLarge,
                                color = DawnGoldBright
                            )
                        }
                    }

                    if (onDeleteClick != null) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("delete_alarm_button_${alarm.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete alarm",
                                tint = AlertRose.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.testTag("alarm_switch_${alarm.id}"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NightObsidian,
                            checkedTrackColor = DawnGold,
                            uncheckedThumbColor = CloudSecondary,
                            uncheckedTrackColor = NightDeep
                        )
                    )
                }
            }

            // Alarm Time + Countdown Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = item.displayTime.format(timeOnlyFormatter),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 44.sp,
                            lineHeight = 48.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (alarm.isEnabled) StarlightMist else CloudSecondary.copy(alpha = 0.55f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = item.displayTime.format(amPmFormatter),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (alarm.isEnabled) DawnGoldBright else CloudSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (alarm.isEnabled) DawnGold.copy(alpha = 0.16f) else NightDeep,
                    border = BorderStroke(
                        1.dp,
                        if (alarm.isEnabled) DawnGold.copy(alpha = 0.4f) else Color.Transparent
                    )
                ) {
                    Text(
                        text = item.countdownText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (alarm.isEnabled) DawnGoldBright else CloudSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Show sunrise relationship subtitle on primary alarm (and standalone label when unlinked)
            if (showSunriseOffsetRow) {
                Text(
                    text = item.relationText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (alarm.isEnabled) CloudSecondary else CloudSecondary.copy(alpha = 0.6f)
                )

                // Sunrise-Relative Quick Chips + Edit Custom Offset button next to "At rise"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickOffsets.forEach { (mins, chipLabel) ->
                        val selected = alarm.mode == AlarmMode.SUNRISE_RELATIVE &&
                            alarm.sunriseOffsetMinutes == mins
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) DawnGold else NightDeep,
                            border = BorderStroke(
                                1.dp,
                                if (selected) DawnGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectSunriseOffset(mins) }
                                .testTag("alarm_${alarm.id}_offset_$mins")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = chipLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) NightObsidian else StarlightMist,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Custom Sunrise Offset Edit Button right next to "At rise"
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCustomSunriseOffsetActive) DawnGold else NightDeep,
                        border = BorderStroke(
                            1.dp,
                            if (isCustomSunriseOffsetActive) DawnGold else DawnGold.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onCustomSunriseOffsetClick)
                            .testTag("custom_sunrise_offset_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Custom sunrise offset",
                                tint = if (isCustomSunriseOffsetActive) NightObsidian else DawnGoldBright,
                                modifier = Modifier.size(16.dp)
                            )
                            if (isCustomSunriseOffsetActive) {
                                val absMins = abs(alarm.sunriseOffsetMinutes)
                                val customText = if (absMins == 60) "1h" else "${absMins}m"
                                Text(
                                    text = customText,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NightObsidian,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Repeat Days Header + small "All Days" option
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatRepeatSummary(alarm.repeatDays),
                    style = MaterialTheme.typography.labelMedium,
                    color = CloudSecondary
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (allDaysSelected) DawnGold.copy(alpha = 0.22f) else NightDeep,
                    border = BorderStroke(
                        1.dp,
                        if (allDaysSelected) DawnGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onSelectAllDays)
                        .testTag("all_days_button_${alarm.id}")
                ) {
                    Text(
                        text = "All Days",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (allDaysSelected) DawnGoldBright else CloudSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Repeat Day Pills (Sun–Sat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                orderedDays.forEach { day ->
                    val active = day in alarm.repeatDays
                    val letter = day.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                    Surface(
                        shape = CircleShape,
                        color = if (active) DawnGold else NightDeep,
                        border = BorderStroke(
                            1.dp,
                            if (active) DawnGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onToggleRepeatDay(day) }
                            .testTag("repeat_day_${alarm.id}_${day.name.lowercase()}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (active) NightObsidian else CloudSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatRepeatSummary(days: Set<DayOfWeek>): String {
    val ordered = listOf(
        DayOfWeek.SUNDAY,
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY
    )
    return when {
        days.isEmpty() -> stringResource(R.string.one_time_alarm)
        days.size == 7 -> stringResource(R.string.every_day)
        days == setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        ) -> stringResource(R.string.weekdays)
        days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> stringResource(R.string.weekends)
        else -> ordered.filter { it in days }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
}

/**
 * Direct manual time editor used both when clicking "Edit" on the Primary alarm (unlinking it from sunrise)
 * and when adding/editing independent Additional Alarms (zero sunrise/sunset/offset options).
 * Prevents swipe-down sheet dismissal conflict while scrolling the wheel time picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualAlarmEditorSheet(
    initialAlarm: UttoAlarm,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (UttoAlarm) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    var label by remember(initialAlarm.id) { mutableStateOf(initialAlarm.label) }
    var manualHour by remember(initialAlarm.id) { mutableIntStateOf(initialAlarm.manualHour) }
    var manualMinute by remember(initialAlarm.id) { mutableIntStateOf(initialAlarm.manualMinute) }
    var repeatDays by remember(initialAlarm.id) { mutableStateOf(initialAlarm.repeatDays) }

    val orderedDays = remember {
        listOf(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = NightDeep
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (canDelete) "Edit Alarm" else if (initialAlarm.isPrimary) "Set Custom Time" else "Add Alarm",
                        style = MaterialTheme.typography.headlineMedium,
                        color = StarlightMist
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("editor_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = CloudSecondary
                        )
                    }
                }
            }

            if (!initialAlarm.isPrimary) {
                item {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Alarm Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_alarm_name_input")
                    )
                }
            }

            item {
                WheelTimePicker(
                    hour24 = manualHour,
                    minute = manualMinute,
                    isActiveMode = true,
                    onTimeChanged = { h24, min ->
                        manualHour = h24
                        manualMinute = min
                    }
                )
            }

            // Repeat Days + All Days
            item {
                val allSelected = repeatDays.size == 7
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Repeat Days",
                            style = MaterialTheme.typography.titleSmall,
                            color = StarlightMist
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (allSelected) DawnGold.copy(alpha = 0.22f) else NightSurface,
                            border = BorderStroke(
                                1.dp,
                                if (allSelected) DawnGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    repeatDays = if (allSelected) emptySet() else UttoAlarm.ALL_DAYS
                                }
                        ) {
                            Text(
                                text = "All Days",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (allSelected) DawnGoldBright else CloudSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        orderedDays.forEach { day ->
                            val active = day in repeatDays
                            val letter = day.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                            Surface(
                                shape = CircleShape,
                                color = if (active) DawnGold else NightSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (active) DawnGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        repeatDays = if (active) repeatDays - day else repeatDays + day
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = letter,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = if (active) NightObsidian else CloudSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Save & optional Delete Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            onSave(
                                initialAlarm.copy(
                                    label = label.trim().ifBlank { "Utto Alarm" },
                                    mode = AlarmMode.MANUAL,
                                    manualHour = manualHour,
                                    manualMinute = manualMinute,
                                    repeatDays = repeatDays
                                )
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DawnGold,
                            contentColor = NightObsidian
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("editor_save_alarm_button")
                    ) {
                        Text("Save Alarm", style = MaterialTheme.typography.titleMedium)
                    }

                    if (canDelete) {
                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, AlertRose.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Delete Alarm", color = AlertRose)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simplified "Custom" offset bottom sheet (opened from the edit button next to "At rise").
 * Keeps quick presets (15 min, 25 min, 90 min) and caps the scrollable offset wheel at 1 hour (60 min),
 * formatting 60 minutes as "1 hour", with full swipe-to-dismiss conflict protection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomSunriseOffsetSheet(
    currentOffsetMinutes: Int,
    onDismiss: () -> Unit,
    onSaveOffset: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    var selectedMinutes by remember {
        mutableIntStateOf(abs(currentOffsetMinutes).coerceIn(1, 90))
    }

    val quickCustomPresets = remember { listOf(15, 25, 90) }

    fun formatOffsetButtonText(mins: Int): String =
        if (mins == 60) "1 hour" else "$mins min"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = NightDeep
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Custom",
                    style = MaterialTheme.typography.headlineSmall,
                    color = StarlightMist
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("custom_offset_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = CloudSecondary
                    )
                }
            }

            // Quick preset chips: 15 min, 25 min, 90 min
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                quickCustomPresets.forEach { preset ->
                    val selected = selectedMinutes == preset
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) DawnGold else NightSurface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) DawnGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedMinutes = preset }
                            .testTag("custom_offset_preset_$preset")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$preset min",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) NightObsidian else StarlightMist
                            )
                        }
                    }
                }
            }

            // Scrollable offset wheel capped at 1 hour (1..60 min), formatting 60 as "1 hour"
            NumericWheelPicker(
                range = 1..60,
                selectedValue = selectedMinutes.coerceIn(1, 60),
                unitSuffix = "min",
                labelFormatter = { value -> if (value == 60) "1 hour" else "$value min" },
                onValueSelected = { selectedMinutes = it }
            )

            Button(
                onClick = { onSaveOffset(selectedMinutes) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DawnGold,
                    contentColor = NightObsidian
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_custom_sunrise_offset_button")
            ) {
                Text(
                    text = "Set ${formatOffsetButtonText(selectedMinutes)}",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun OfflineSyncFooter(
    syncStatusText: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.OfflinePin,
            contentDescription = null,
            tint = ReadyEmerald,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = syncStatusText,
            style = MaterialTheme.typography.labelSmall,
            color = CloudSecondary,
            modifier = Modifier.testTag("offline_sync_footer")
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationPickerBottomSheet(
    currentName: String,
    currentLat: Double,
    currentLon: Double,
    onDismiss: () -> Unit,
    onUseGps: () -> Unit,
    onSelectCity: (PresetCity) -> Unit,
    onApplyCoordinates: (Double, Double, String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var latInput by remember { mutableStateOf(String.format(Locale.US, "%.4f", currentLat)) }
    var lonInput by remember { mutableStateOf(String.format(Locale.US, "%.4f", currentLon)) }
    var nameInput by remember { mutableStateOf(currentName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NightDeep
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Text(
                    text = "Solar Location",
                    style = MaterialTheme.typography.headlineMedium,
                    color = StarlightMist
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Coordinates are cached on-device so sunrise calculations work 100% offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CloudSecondary
                )
            }

            item {
                Button(
                    onClick = onUseGps,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DawnGold,
                        contentColor = NightObsidian
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("sheet_use_gps_button")
                ) {
                    Icon(Icons.Rounded.GpsFixed, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Detect Current GPS Location", style = MaterialTheme.typography.titleMedium)
                }
            }

            item {
                Text(
                    text = "Quick Offline Cities",
                    style = MaterialTheme.typography.titleSmall,
                    color = DawnGoldBright,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            items(CuratedOfflineCities.size) { idx ->
                val city = CuratedOfflineCities[idx]
                val isSelected = city.name == currentName
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) DawnGold.copy(alpha = 0.16f) else NightSurface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) DawnGold.copy(alpha = 0.5f) else Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectCity(city) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = city.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = StarlightMist
                            )
                            Text(
                                text = LocationRepository.formatCoordinates(city.latitude, city.longitude),
                                style = MaterialTheme.typography.labelSmall,
                                color = CloudSecondary
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Selected",
                                tint = DawnGold
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Custom Coordinates",
                    style = MaterialTheme.typography.titleSmall,
                    color = DawnGoldBright
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Location Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = latInput,
                        onValueChange = { latInput = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lonInput,
                        onValueChange = { lonInput = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val lat = latInput.toDoubleOrNull()
                        val lon = lonInput.toDoubleOrNull()
                        if (lat != null && lon != null) {
                            onApplyCoordinates(lat, lon, nameInput.trim().ifBlank { null })
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Save Custom Coordinates")
                }
            }
        }
    }
}
