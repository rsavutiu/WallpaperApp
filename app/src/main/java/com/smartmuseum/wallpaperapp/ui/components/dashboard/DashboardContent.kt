package com.smartmuseum.wallpaperapp.ui.components.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage

@Composable
fun DashboardContent(
    atmosImage: AtmosImage?,
    isCelsius: Boolean,
    isCalendarEnabled: Boolean,
    showLocation: Boolean,
    showTemperature: Boolean,
    showForecast: Boolean,
    onToggleUnit: (() -> Unit)?,
    forceTemp: Double?,
    forceWeatherCode: Int?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        verticalArrangement = Arrangement.Center
    ) {
        // Agenda Section
        if (isCalendarEnabled && !atmosImage?.calendarEvents.isNullOrEmpty()) {
            AgendaList(events = atmosImage?.calendarEvents)
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Environmental Info Section
        atmosImage?.let { atmos ->
            EnvironmentalDetails(
                atmos = atmos,
                isCelsius = isCelsius,
                showLocation = showLocation,
                showTemperature = showTemperature,
                showForecast = showForecast,
                onToggleUnit = onToggleUnit,
                forceTemp = forceTemp,
                forceWeatherCode = forceWeatherCode
            )
        }
    }
}