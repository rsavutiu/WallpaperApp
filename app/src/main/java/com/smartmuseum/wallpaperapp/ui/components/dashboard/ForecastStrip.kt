package com.smartmuseum.wallpaperapp.ui.components.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmuseum.wallpaperapp.domain.model.HourlyForecast

@Composable
fun ForecastStrip(
    forecasts: List<HourlyForecast>,
    isCelsius: Boolean
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(forecasts) { forecast ->
            val hourlyTemp = if (isCelsius) forecast.temp else (forecast.temp * 9 / 5) + 32
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = forecast.time.takeLast(5),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                WeatherIcon(
                    forecast.weatherCode,
                    true,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(vertical = 2.dp)
                )
                Text(
                    text = "${hourlyTemp.toInt()}°",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Preview
@Composable
fun ForecastStripPreview() {
    ForecastStrip(
        forecasts = listOf(
            HourlyForecast(
                time = "12:00",
                temp = -1.0,
                precipitationProb = 20,
                weatherCode = 5101
            )
        ),
        isCelsius = true
    )
}