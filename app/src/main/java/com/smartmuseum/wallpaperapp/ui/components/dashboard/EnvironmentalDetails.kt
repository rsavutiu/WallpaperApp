package com.smartmuseum.wallpaperapp.ui.components.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage

@Composable
fun EnvironmentalDetails(
    atmos: AtmosImage,
    isCelsius: Boolean,
    showLocation: Boolean = true,
    showTemperature: Boolean = true,
    showForecast: Boolean = true,
    onToggleUnit: (() -> Unit)?,
    forceTemp: Double?,
    forceWeatherCode: Int?
) {
    Column {
        atmos.weather?.let { weather ->
            // Location Header
            if (showLocation) {
                LocationHeader(atmos.locationName)
            }

            if (showTemperature) {
                // Primary Weather Row
                WeatherPrimaryRow(
                    weather = weather,
                    isCelsius = isCelsius,
                    onToggleUnit = onToggleUnit,
                    forceTemp = forceTemp,
                    forceWeatherCode = forceWeatherCode
                )

                // Atmospheric Stats
                Text(
                    text = weather.condition,
                    fontSize = 20.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    text = stringResource(
                        R.string.humidity_precip_format,
                        weather.humidity,
                        weather.precipitation
                    ),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Forecast Strip
            if (showForecast) {
                ForecastStrip(forecasts = weather.hourlyForecast, isCelsius = isCelsius)
            }

            // Image Provider Insights
            ImageInsights(title = atmos.title, explanation = atmos.explanation)
        }
    }
}
