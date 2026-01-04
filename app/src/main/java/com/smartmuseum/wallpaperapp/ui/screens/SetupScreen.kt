package com.smartmuseum.wallpaperapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmuseum.wallpaperapp.BuildConfig
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.ui.MainUiState

@Composable
fun SetupScreen(
    uiState: MainUiState,
    toggleUseLocation: () -> Unit,
    toggleTemperatureUnit: () -> Unit,
    setCalendarEnabled: (Boolean) -> Unit,
    onRunUpdate: () -> Unit,
    onDebugWeatherChange: (Int?) -> Unit,
    updateRefreshPeriod: (Long) -> Unit,
    debugWeatherCode: Int?,
    debugTemp: Double,
    onDebugTempChange: (Double?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val refreshOptions = remember {
        mutableListOf<Long>().apply {
            if (BuildConfig.DEBUG) add(1L) // 1 minute for debug
            addAll(listOf(15L, 30L, 60L, 120L, 240L))
        }
    }
    
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
            onClick = toggleUseLocation
        ) {
            Checkbox(
                checked = uiState.useLocation,
                onCheckedChange = { toggleUseLocation() },
                colors = CheckboxDefaults.colors(checkmarkColor = MaterialTheme.colorScheme.primary)
            )
        }

        // Temperature Unit Row
        SetupRow(
            icon = Icons.Default.Thermostat,
            label = "Temperature Unit",
            onClick = toggleTemperatureUnit
        ) {
            Button(
                onClick = toggleTemperatureUnit,
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

        // Calendar Sync Row
        SetupRow(
            icon = Icons.Default.CalendarMonth,
            label = stringResource(R.string.enable_calendar),
            onClick = { setCalendarEnabled(!uiState.isCalendarEnabled) }
        ) {
            Switch(
                checked = uiState.isCalendarEnabled,
                onCheckedChange = setCalendarEnabled
            )
        }

        SetupRow(
            icon = Icons.Default.Refresh,
            label = stringResource(R.string.refresh_period),
            onClick = { expanded = true }
        ) {
            Box {
                Button(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    )
                ) {
                    val currentPeriod = uiState.refreshPeriodInMinutes
                    Text(
                        text = if (currentPeriod >= 60) "${currentPeriod / 60}h" else "${currentPeriod}m",
                        fontSize = 12.sp
                    )
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
                                    text = when {
                                        minutes == 1L -> "1 minute"
                                        minutes >= 60L -> stringResource(
                                            R.string.hours,
                                            minutes / 60
                                        )

                                        else -> stringResource(R.string.minutes, minutes)
                                    },
                                    style = MaterialTheme.typography.bodyMedium
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

        if (BuildConfig.DEBUG) {
            DebugWeatherSection(
                onDebugWeatherChange = onDebugWeatherChange,
                debugWeatherCode = debugWeatherCode,
                debugTemp = debugTemp,
                onDebugTempChange = onDebugTempChange
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Big Run Button
        Button(
            onClick = onRunUpdate,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("RUN", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun SetupRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = label,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            content()
        }
    }
}

@Composable
private fun DebugWeatherSection(
    onDebugWeatherChange: (Int?) -> Unit,
    debugWeatherCode: Int?,
    debugTemp: Double,
    onDebugTempChange: (Double?) -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Debug Weather Overlay:",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val buttonColors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = Color.White
                )

                Button(
                    onClick = { onDebugWeatherChange(null); onDebugTempChange(null) },
                    colors = buttonColors,
                    contentPadding = PaddingValues(4.dp)
                ) { Text(if (debugWeatherCode == null) "[Auto]" else "Auto") }
                Button(
                    onClick = { onDebugWeatherChange(0) },
                    colors = buttonColors,
                    contentPadding = PaddingValues(4.dp)
                ) { Text(if (debugWeatherCode == 0) "[Clear]" else "Clear") }
                Button(
                    onClick = { onDebugWeatherChange(61) },
                    colors = buttonColors,
                    contentPadding = PaddingValues(4.dp)
                ) { Text(if (debugWeatherCode == 61) "[L. Rain]" else "L. Rain") }
                Button(
                    onClick = { onDebugWeatherChange(65) },
                    colors = buttonColors,
                    contentPadding = PaddingValues(4.dp)
                ) { Text(if (debugWeatherCode == 65) "[H. Rain]" else "H. Rain") }
                Button(
                    onClick = { onDebugWeatherChange(71) },
                    colors = buttonColors,
                    contentPadding = PaddingValues(4.dp)
                ) { Text(if (debugWeatherCode == 71) "[L. Snow]" else "L. Snow") }
                Button(
                    onClick = { onDebugWeatherChange(75) },
                    colors = buttonColors,
                    contentPadding = PaddingValues(4.dp)
                ) { Text(if (debugWeatherCode == 75) "[H. Snow]" else "H. Snow") }
                Button(
                    onClick = { onDebugWeatherChange(45) },
                    colors = buttonColors,
                    contentPadding = PaddingValues(4.dp)
                ) { Text(if (debugWeatherCode == 45) "[Fog]" else "Fog") }
                Button(
                    onClick = { onDebugWeatherChange(95) },
                    colors = buttonColors,
                    contentPadding = PaddingValues(4.dp)
                ) { Text(if (debugWeatherCode == 95) "[Storm]" else "Storm") }
                Button(
                    onClick = { onDebugTempChange(if (debugTemp > 0) -20.0 else 20.0) },
                    colors = buttonColors,
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text("Temp: ${debugTemp.toInt()}°C")
                }
            }
        }
    }
}
