package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.FirstLaunchPermissionsDialog
import com.example.ui.components.UttoSplashOverlay
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CloudSecondary
import com.example.ui.theme.DawnGold
import com.example.ui.theme.DawnGoldBright
import com.example.ui.theme.DawnGoldContainer
import com.example.ui.theme.NightDeep
import com.example.ui.theme.NightObsidian
import com.example.ui.theme.UttoTheme
import com.example.viewmodel.UttoViewModel

private const val ROUTE_HOME = "home"
private const val ROUTE_SCHEDULE = "schedule"
private const val ROUTE_SETTINGS = "settings"

private data class UttoNavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val UttoDestinations = listOf(
    UttoNavDestination(
        route = ROUTE_HOME,
        labelRes = R.string.nav_alarm,
        selectedIcon = Icons.Rounded.Alarm,
        unselectedIcon = Icons.Outlined.Alarm,
        testTag = "nav_tab_alarm"
    ),
    UttoNavDestination(
        route = ROUTE_SCHEDULE,
        labelRes = R.string.nav_schedule,
        selectedIcon = Icons.Rounded.WbTwilight,
        unselectedIcon = Icons.Outlined.WbTwilight,
        testTag = "nav_tab_schedule"
    ),
    UttoNavDestination(
        route = ROUTE_SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Rounded.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "nav_tab_settings"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            UttoTheme(darkTheme = true) {
                UttoApp()
            }
        }
    }
}

@Composable
fun UttoApp(
    viewModel: UttoViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSplash by rememberSaveable { mutableStateOf(true) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HOME

    fun navigateTo(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NightObsidian)
    ) {
        val useNavRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail) {
                NavigationRail(
                    containerColor = NightDeep,
                    contentColor = DawnGoldBright
                ) {
                    UttoDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTo(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(dest.labelRes)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(dest.labelRes),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = DawnGoldBright,
                                selectedTextColor = DawnGoldBright,
                                indicatorColor = DawnGoldContainer,
                                unselectedIconColor = CloudSecondary,
                                unselectedTextColor = CloudSecondary
                            ),
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = NightObsidian,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = NightDeep,
                            tonalElevation = 0.dp
                        ) {
                            UttoDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(dest.labelRes),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = DawnGoldBright,
                                        selectedTextColor = DawnGoldBright,
                                        indicatorColor = DawnGoldContainer,
                                        unselectedIconColor = CloudSecondary,
                                        unselectedTextColor = CloudSecondary
                                    ),
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HOME
                    ) {
                        composable(ROUTE_HOME) {
                            HomeScreen(
                                uiState = uiState,
                                onToggleAlarmEnabled = viewModel::toggleAlarmEnabled,
                                onSetAlarmSunriseOffset = viewModel::setAlarmSunriseOffset,
                                onToggleAlarmRepeatDay = viewModel::toggleAlarmRepeatDay,
                                onSelectAllDaysForAlarm = viewModel::selectAllDaysForAlarm,
                                onSaveAlarm = viewModel::saveAlarm,
                                onDeleteAlarm = viewModel::deleteAlarm,
                                onRefreshGpsLocation = viewModel::refreshGpsLocation,
                                onSelectPresetCity = viewModel::selectPresetCity,
                                onSetCustomCoordinates = viewModel::setCustomCoordinates,
                                onLocationPermissionResult = viewModel::refreshPermissionsHealth
                            )
                        }
                        composable(ROUTE_SCHEDULE) {
                            ScheduleScreen(
                                uiState = uiState,
                                onNavigateBack = { navigateTo(ROUTE_HOME) },
                                onSearchLocation = viewModel::searchSolarCalendarLocation,
                                onResetToMyLocation = viewModel::resetSolarCalendarLocation
                            )
                        }
                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                uiState = uiState,
                                onNavigateBack = { navigateTo(ROUTE_HOME) },
                                onSelectSoundPreset = viewModel::selectSoundPreset,
                                onCustomAudioSelected = viewModel::setCustomAudioUri,
                                onStopPreview = viewModel::stopSoundPreview,
                                onVolumeChange = viewModel::setVolumePercent,
                                onGradualFadeChange = viewModel::setGradualFadeIn,
                                onVibrateChange = viewModel::setVibrateEnabled,
                                onSnoozeDurationChange = viewModel::setSnoozeDuration,
                                onRefreshPermissionsHealth = viewModel::refreshPermissionsHealth
                            )
                        }
                    }
                }
            }
        }

        UttoSplashOverlay(
            visible = showSplash,
            onAnimationFinished = { showSplash = false }
        )

        if (uiState.hasLoadedPreferences && !showSplash && !uiState.settings.hasCompletedPermissionsFlow) {
            FirstLaunchPermissionsDialog(
                health = uiState.permissionsHealth,
                onRefreshHealth = viewModel::refreshPermissionsHealth,
                onLocationGranted = viewModel::refreshGpsLocation,
                onCompleted = viewModel::markPermissionsFlowCompleted
            )
        }
    }
}
