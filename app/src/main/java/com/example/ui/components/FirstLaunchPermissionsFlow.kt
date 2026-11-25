package com.example.ui.components

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.PermissionHealthStatus
import com.example.ui.theme.CloudSecondary
import com.example.ui.theme.DawnGold
import com.example.ui.theme.DawnGoldBright
import com.example.ui.theme.NightDeep
import com.example.ui.theme.NightObsidian
import com.example.ui.theme.NightSurface
import com.example.ui.theme.StarlightMist

private enum class OnboardingPermissionStep {
    LOCATION,
    NOTIFICATION,
    EXACT_ALARM,
    BATTERY,
    STORAGE
}

/**
 * First-launch sequential permissions flow prompting concisely for required permissions:
 * Location -> Notification -> Exact Alarm -> Battery Optimization -> Storage,
 * displaying clean labels only with no micro-text or ringtone picker actions.
 */
@Composable
fun FirstLaunchPermissionsDialog(
    health: PermissionHealthStatus,
    onRefreshHealth: () -> Unit,
    onLocationGranted: () -> Unit,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val steps = remember { OnboardingPermissionStep.entries }
    var stepIndex by remember { mutableIntStateOf(0) }
    var showDeniedFallback by remember { mutableStateOf(false) }
    var waitingForSystemSettingsReturn by remember { mutableStateOf(false) }

    fun advanceOrFinish() {
        showDeniedFallback = false
        waitingForSystemSettingsReturn = false
        if (stepIndex + 1 >= steps.size) {
            onCompleted()
        } else {
            stepIndex += 1
        }
    }

    // Auto-skip steps that are already granted
    val currentStep = steps.getOrNull(stepIndex)
    LaunchedEffect(currentStep, health) {
        when (currentStep) {
            OnboardingPermissionStep.LOCATION -> if (health.hasLocationPermission) advanceOrFinish()
            OnboardingPermissionStep.NOTIFICATION -> if (health.hasNotificationPermission) advanceOrFinish()
            OnboardingPermissionStep.EXACT_ALARM -> if (health.canScheduleExactAlarms) advanceOrFinish()
            OnboardingPermissionStep.BATTERY -> if (health.isIgnoringBatteryOptimizations) advanceOrFinish()
            OnboardingPermissionStep.STORAGE -> if (health.hasStoragePermission) advanceOrFinish()
            null -> onCompleted()
        }
    }

    // Re-check permissions when returning from Android system settings screens
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefreshHealth()
        if (waitingForSystemSettingsReturn) {
            waitingForSystemSettingsReturn = false
            advanceOrFinish()
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        onRefreshHealth()
        if (grants.values.any { it }) {
            onLocationGranted()
            advanceOrFinish()
        } else {
            showDeniedFallback = true
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onRefreshHealth()
        if (granted) {
            advanceOrFinish()
        } else {
            showDeniedFallback = true
        }
    }

    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onRefreshHealth()
        if (granted) {
            advanceOrFinish()
        } else {
            showDeniedFallback = true
        }
    }

    if (currentStep == null) return

    val icon: ImageVector
    val title: String

    when (currentStep) {
        OnboardingPermissionStep.LOCATION -> {
            icon = Icons.Rounded.LocationOn
            title = "Location"
        }
        OnboardingPermissionStep.NOTIFICATION -> {
            icon = Icons.Rounded.NotificationsActive
            title = "Notification"
        }
        OnboardingPermissionStep.EXACT_ALARM -> {
            icon = Icons.Rounded.AlarmOn
            title = "Exact Alarm"
        }
        OnboardingPermissionStep.BATTERY -> {
            icon = Icons.Rounded.BatteryChargingFull
            title = "Battery Optimization"
        }
        OnboardingPermissionStep.STORAGE -> {
            icon = Icons.Rounded.FolderOpen
            title = "Storage"
        }
    }

    Dialog(
        onDismissRequest = { onCompleted() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = NightDeep,
            border = BorderStroke(1.dp, DawnGold.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("first_launch_permissions_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Minimal progress dots (no text caption)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.forEachIndexed { idx, _ ->
                        val isCurrent = idx == stepIndex
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isCurrent) 22.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCurrent) DawnGold else CloudSecondary.copy(alpha = 0.35f)
                                )
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = NightSurface,
                    border = BorderStroke(1.dp, DawnGold.copy(alpha = 0.4f)),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = DawnGoldBright,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = StarlightMist,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("onboarding_permission_title")
                )

                if (showDeniedFallback) {
                    OutlinedButton(
                        onClick = {
                            waitingForSystemSettingsReturn = true
                            val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            runCatching { context.startActivity(appSettingsIntent) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DawnGold.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("onboarding_open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = DawnGoldBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("App Settings", color = DawnGoldBright)
                    }
                }

                Button(
                    onClick = {
                        when (currentStep) {
                            OnboardingPermissionStep.LOCATION -> {
                                locationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            }
                            OnboardingPermissionStep.NOTIFICATION -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    advanceOrFinish()
                                }
                            }
                            OnboardingPermissionStep.EXACT_ALARM -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    waitingForSystemSettingsReturn = true
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    runCatching { context.startActivity(intent) }
                                        .onFailure {
                                            waitingForSystemSettingsReturn = false
                                            showDeniedFallback = true
                                        }
                                } else {
                                    advanceOrFinish()
                                }
                            }
                            OnboardingPermissionStep.BATTERY -> {
                                waitingForSystemSettingsReturn = true
                                @SuppressLint("BatteryLife")
                                val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                runCatching { context.startActivity(directIntent) }
                                    .onFailure {
                                        runCatching { context.startActivity(fallbackIntent) }
                                            .onFailure {
                                                waitingForSystemSettingsReturn = false
                                                showDeniedFallback = true
                                            }
                                    }
                            }
                            OnboardingPermissionStep.STORAGE -> {
                                val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    Manifest.permission.READ_MEDIA_AUDIO
                                } else {
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                }
                                storageLauncher.launch(storagePermission)
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DawnGold,
                        contentColor = NightObsidian
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboarding_allow_button")
                ) {
                    Text(text = "Allow", style = MaterialTheme.typography.titleMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { onCompleted() },
                        modifier = Modifier.testTag("onboarding_skip_all_button")
                    ) {
                        Text("Later", color = CloudSecondary)
                    }

                    TextButton(
                        onClick = { advanceOrFinish() },
                        modifier = Modifier.testTag("onboarding_skip_step_button")
                    ) {
                        Text("Skip", color = DawnGoldBright)
                    }
                }
            }
        }
    }
}
