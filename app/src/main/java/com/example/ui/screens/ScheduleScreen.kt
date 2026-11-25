package com.example.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.alarm.UttoAlarmScheduler
import com.example.data.DailySolarInfo
import com.example.ui.theme.AlertRose
import com.example.ui.theme.CloudSecondary
import com.example.ui.theme.DawnGold
import com.example.ui.theme.DawnGoldBright
import com.example.ui.theme.HorizonCoral
import com.example.ui.theme.NightDeep
import com.example.ui.theme.NightObsidian
import com.example.ui.theme.NightSurface
import com.example.ui.theme.NightSurfaceElevated
import com.example.ui.theme.StarlightMist
import com.example.ui.theme.TwilightViolet
import com.example.viewmodel.UttoUiState
import java.time.format.DateTimeFormatter

@Composable
fun ScheduleScreen(
    uiState: UttoUiState,
    onNavigateBack: () -> Unit,
    onSearchLocation: (String) -> Unit,
    onResetToMyLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submitSearch() {
        val trimmed = searchQuery.trim()
        if (trimmed.isNotEmpty()) {
            keyboardController?.hide()
            onSearchLocation(trimmed)
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top-left Back Arrow + "Solar Calendar" title (no subtitle)
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
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("solar_calendar_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = StarlightMist
                            )
                        }
                    }

                    Text(
                        text = "Solar Calendar",
                        style = MaterialTheme.typography.headlineMedium,
                        color = StarlightMist
                    )
                }
            }

            // Clean Location Chip + Search Bar for any city/place
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = NightSurface,
                            border = BorderStroke(1.dp, DawnGold.copy(alpha = 0.42f)),
                            modifier = Modifier.testTag("solar_calendar_location_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = DawnGoldBright,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = uiState.calendarLocationName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = StarlightMist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (uiState.isUsingSearchedCalendarLocation) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = NightSurfaceElevated,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        searchQuery = ""
                                        onResetToMyLocation()
                                    }
                                    .testTag("solar_calendar_reset_location")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MyLocation,
                                        contentDescription = "Return to my location",
                                        tint = DawnGoldBright,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "My Location",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DawnGoldBright
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search city or place...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CloudSecondary
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = DawnGoldBright
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotEmpty() && !uiState.isSearchingCalendarLocation) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear search",
                                            tint = CloudSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                if (uiState.isSearchingCalendarLocation) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = DawnGold
                                    )
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = DawnGold,
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { submitSearch() }
                                            .testTag("solar_calendar_search_submit")
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Search",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = NightObsidian
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DawnGold,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                            focusedContainerColor = NightSurface,
                            unfocusedContainerColor = NightSurface,
                            focusedTextColor = StarlightMist,
                            unfocusedTextColor = StarlightMist,
                            cursorColor = DawnGoldBright
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("solar_calendar_search_input")
                    )

                    if (!uiState.calendarSearchError.isNullOrBlank()) {
                        Text(
                            text = uiState.calendarSearchError,
                            style = MaterialTheme.typography.bodySmall,
                            color = AlertRose,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            // 14-Day Solar Calendar Forecast Cards
            itemsIndexed(uiState.calendarForecast) { index, dayInfo ->
                SolarCalendarDayCard(
                    dayInfo = dayInfo,
                    dateTitle = when (index) {
                        0 -> "Today • ${dayInfo.date.format(dateFormatter)}"
                        1 -> "Tomorrow • ${dayInfo.date.format(dateFormatter)}"
                        else -> dayInfo.date.format(dateFormatter)
                    },
                    isToday = index == 0
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SolarCalendarDayCard(
    dayInfo: DailySolarInfo,
    dateTitle: String,
    isToday: Boolean
) {
    val dayMinutes = dayInfo.daylightMinutes.coerceIn(0, 1440)
    val nightMinutes = (1440 - dayMinutes).coerceIn(0, 1440)
    val dayText = "${dayMinutes / 60}h ${dayMinutes % 60}m"
    val nightText = "${nightMinutes / 60}h ${nightMinutes % 60}m"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isToday) NightSurface else NightDeep,
        border = BorderStroke(
            1.dp,
            if (isToday) DawnGold.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Date header only (no alarm icons or badges)
            Text(
                text = dateTitle,
                style = MaterialTheme.typography.titleMedium,
                color = if (isToday) DawnGoldBright else StarlightMist
            )

            // Three main solar events in order: Sunrise, Solar Noon, Sunset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SolarEventMetric(
                    icon = Icons.Rounded.WbTwilight,
                    label = "Sunrise",
                    value = UttoAlarmScheduler.formatTime12h(dayInfo.sunrise),
                    tint = HorizonCoral
                )
                SolarEventMetric(
                    icon = Icons.Rounded.WbSunny,
                    label = "Solar Noon",
                    value = UttoAlarmScheduler.formatTime12h(dayInfo.solarNoon),
                    tint = DawnGoldBright
                )
                SolarEventMetric(
                    icon = Icons.Rounded.NightsStay,
                    label = "Sunset",
                    value = UttoAlarmScheduler.formatTime12h(dayInfo.sunset),
                    tint = TwilightViolet
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // Clean Day & Night duration summary
            Text(
                text = "Day: $dayText · Night: $nightText",
                style = MaterialTheme.typography.labelMedium,
                color = CloudSecondary
            )
        }
    }
}

@Composable
private fun SolarEventMetric(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = CloudSecondary
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = StarlightMist
        )
    }
}
