package com.smartmuseum.wallpaperapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smartmuseum.wallpaperapp.BuildConfig
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.ui.MainUiState
import com.smartmuseum.wallpaperapp.ui.components.SetupRow

@Composable
fun TvSettingsScreen(
    uiState: MainUiState,
    setCalendarEnabled: (Boolean) -> Unit,
    setDynamicWallpaperEnabled: (Boolean) -> Unit,
    updateRefreshPeriod: (Long) -> Unit,
    onToggleShowLocation: (Boolean) -> Unit,
    onToggleShowTemperature: (Boolean) -> Unit,
    onToggleShowForecast: (Boolean) -> Unit,
    onToggleShowSunTransitions: () -> Unit,
    onToggleTemperatureUnit: () -> Unit,
    onChooseManualLocation: () -> Unit,
    toggleUseLocation: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val refreshOptions = remember {
        mutableListOf<Long>().apply {
            if (BuildConfig.DEBUG) add(1L) // 1 minute for debug
            addAll(listOf(15L, 30L, 60L, 120L, 240L))
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.9f)
    ) {
        BackHandler(onBack = onDismiss)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 56.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }

            // --- Combined Settings ---

            SetupRow(
                icon = Icons.Default.EditLocation,
                label = "Manual Location",
                secondaryLabel = "Override automatic location with a specific city",
                onClick = onChooseManualLocation
            ) {
                Button(onClick = onChooseManualLocation) { Text("SET") }
            }

            SetupRow(
                icon = Icons.Default.Place,
                label = "Use GPS Location",
                secondaryLabel = "Use device\'s GPS for automatic location",
                onClick = toggleUseLocation
            ) {
                Switch(checked = uiState.useLocation, onCheckedChange = { toggleUseLocation() })
            }

            SetupRow(
                icon = Icons.Default.Visibility,
                label = "Show Location Name",
                secondaryLabel = "Display the city name on the wallpaper",
                onClick = { onToggleShowLocation(!uiState.showLocation) }
            ) {
                Switch(checked = uiState.showLocation, onCheckedChange = onToggleShowLocation)
            }

            SetupRow(
                icon = Icons.Default.DeviceThermostat,
                label = "Show Temperature",
                secondaryLabel = "Display the current temperature and conditions",
                onClick = { onToggleShowTemperature(!uiState.showTemperature) }
            ) {
                Switch(checked = uiState.showTemperature, onCheckedChange = onToggleShowTemperature)
            }

            SetupRow(
                icon = Icons.Default.Thermostat,
                label = "Temperature Unit",
                secondaryLabel = stringResource(R.string.temp_unit_desc),
                onClick = onToggleTemperatureUnit
            ) {
                Button(
                    onClick = onToggleTemperatureUnit,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(if (uiState.isCelsius) R.string.unit_f else R.string.unit_c))
                }
            }

            SetupRow(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                label = "Show Forecast",
                secondaryLabel = "Display the hourly forecast strip",
                onClick = { onToggleShowForecast(!uiState.showForecast) }
            ) {
                Switch(checked = uiState.showForecast, onCheckedChange = onToggleShowForecast)
            }

            SetupRow(
                icon = Icons.Default.CalendarMonth,
                label = stringResource(R.string.enable_calendar),
                secondaryLabel = stringResource(R.string.calendar_sync_desc),
                onClick = { setCalendarEnabled(!uiState.isCalendarEnabled) }
            ) {
                Switch(checked = uiState.isCalendarEnabled, onCheckedChange = setCalendarEnabled)
            }

            SetupRow(
                icon = Icons.Default.AutoAwesome,
                label = stringResource(R.string.dynamic_wallpaper),
                secondaryLabel = stringResource(R.string.dynamic_wallpaper_desc),
                onClick = { setDynamicWallpaperEnabled(!uiState.isDynamicWallpaperEnabled) }
            ) {
                Switch(
                    checked = uiState.isDynamicWallpaperEnabled,
                    onCheckedChange = setDynamicWallpaperEnabled
                )
            }

            SetupRow(
                icon = Icons.Default.Brightness4,
                label = "Precise Sun Transitions",
                secondaryLabel = "Use twilight periods for wallpaper queries",
                onClick = { onToggleShowSunTransitions() }
            ) {
                Switch(
                    checked = uiState.showSunTransitions,
                    onCheckedChange = { onToggleShowSunTransitions() })
            }

            SetupRow(
                icon = Icons.Default.Refresh,
                label = stringResource(R.string.refresh_period),
                secondaryLabel = stringResource(R.string.refresh_period_desc),
                onClick = { expanded = true }
            ) {
                Box {
                    Button(onClick = { expanded = true }) {
                        val currentPeriod = uiState.refreshPeriodInMinutes
                        Text(if (currentPeriod >= 60) "${currentPeriod / 60}h" else "${currentPeriod}m")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        refreshOptions.forEach { minutes ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when {
                                            minutes == 1L -> "1 minute"
                                            minutes >= 60L -> stringResource(
                                                R.string.hours,
                                                minutes / 60
                                            )

                                            else -> stringResource(R.string.minutes, minutes)
                                        }
                                    )
                                },
                                onClick = {
                                    updateRefreshPeriod(minutes)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
