package com.smartmuseum.wallpaperapp.ui.components.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.WeatherData

@Composable
fun WeatherPrimaryRow(
    weather: WeatherData,
    isCelsius: Boolean,
    onToggleUnit: (() -> Unit)?,
    forceTemp: Double?,
    forceWeatherCode: Int?
) {
    val temp = forceTemp ?: weather.currentTemp
    val displayTemp = if (isCelsius) temp else (temp * 9 / 5) + 32
    val unitLabel = stringResource(if (isCelsius) R.string.unit_c else R.string.unit_f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(
                code = forceWeatherCode ?: weather.weatherCode,
                isDay = weather.isDay,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${displayTemp.toInt()}$unitLabel",
                fontSize = 56.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        onToggleUnit?.let {
            FilledTonalButton(
                onClick = it,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White
                ),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    stringResource(if (isCelsius) R.string.unit_f else R.string.unit_c),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Preview
@Composable
private fun WeatherPrimaryRowPreview() {
    WeatherPrimaryRow(
        weather = WeatherData(
            currentTemp = 24.0,
            condition = "Clear",
            weatherCode = 0,
            isDay = true,
            humidity = 40,
            precipitation = 0.0,
            hourlyForecast = emptyList(),
            snowfall = 0.0,
            rain = 0.0,
            showers = 0.0,
            cloudCover = 0
        ),
        isCelsius = true,
        onToggleUnit = {},
        forceTemp = null,
        forceWeatherCode = null
    )
}
