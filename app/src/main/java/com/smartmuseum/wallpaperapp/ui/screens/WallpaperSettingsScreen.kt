package com.smartmuseum.wallpaperapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.ui.MainUiState
import com.smartmuseum.wallpaperapp.ui.components.SetupRow

@Composable
fun WallpaperSettingsScreen(
    uiState: MainUiState,
    toggleUseLocation: () -> Unit,
    onToggleShowTemperature: (Boolean) -> Unit,
    onToggleShowForecast: (Boolean) -> Unit,
    onToggleShowSunTransitions: () -> Unit,
    onToggleTemperatureUnit: () -> Unit,
    onChooseManualLocation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Use Location Row
        SetupRow(
            icon = Icons.Default.Place,
            label = stringResource(R.string.use_location_wallpapers),
            secondaryLabel = stringResource(R.string.location_usage_desc),
            onClick = toggleUseLocation
        ) {
            Checkbox(
                checked = uiState.useLocation,
                onCheckedChange = { toggleUseLocation() },
                colors = CheckboxDefaults.colors(checkmarkColor = MaterialTheme.colorScheme.primary)
            )
        }

        SetupRow(
            icon = Icons.Default.EditLocation,
            label = "Manual Location",
            secondaryLabel = "Override current location with a specific city",
            onClick = onChooseManualLocation
        ) {
            Button(
                onClick = onChooseManualLocation,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White
                )
            ) {
                Text("SET", fontSize = 12.sp)
            }
        }

        SetupRow(
            icon = Icons.Default.DeviceThermostat,
            label = "Show Temperature",
            secondaryLabel = "Display the current temperature and conditions",
            onClick = { onToggleShowTemperature(!uiState.showTemperature) }
        ) {
            Switch(
                checked = uiState.showTemperature,
                onCheckedChange = onToggleShowTemperature
            )
        }

        // Temperature Unit Row
        SetupRow(
            icon = Icons.Default.Thermostat,
            label = "Temperature Unit",
            secondaryLabel = stringResource(R.string.temp_unit_desc),
            onClick = onToggleTemperatureUnit
        ) {
            Button(
                onClick = onToggleTemperatureUnit,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White
                )
            ) {
                Text(
                    stringResource(if (uiState.isCelsius) R.string.unit_f else R.string.unit_c),
                    fontSize = 12.sp
                )
            }
        }

        SetupRow(
            icon = Icons.AutoMirrored.Filled.ListAlt,
            label = "Show Forecast",
            secondaryLabel = "Display the hourly forecast strip",
            onClick = { onToggleShowForecast(!uiState.showForecast) }
        ) {
            Switch(
                checked = uiState.showForecast,
                onCheckedChange = onToggleShowForecast
            )
        }

        SetupRow(
            icon = Icons.Default.Brightness4,
            label = "Precise Sun Transitions",
            secondaryLabel = "Use astronomical and civil twilight for wallpaper queries",
            onClick = { onToggleShowSunTransitions() }
        ) {
            Switch(
                checked = uiState.showSunTransitions,
                onCheckedChange = { onToggleShowSunTransitions() }
            )
        }
    }
}
