package com.smartmuseum.wallpaperapp.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.model.CalendarEvent
import com.smartmuseum.wallpaperapp.domain.model.WeatherData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AtmosDashboard(
    atmosImage: AtmosImage?,
    currentWallpaper: Bitmap?,
    isCelsius: Boolean,
    isCalendarEnabled: Boolean,
    onToggleUnit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    forceWeatherCode: Int? = null,
    forceTemp: Double? = null,
    forceIntensity: Float? = null
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Background Image Layer
        WallpaperBackground(currentWallpaper)

        // Weather Effects Layer (AGSL/Shaders)
        val displayWeatherCode = forceWeatherCode ?: atmosImage?.weather?.weatherCode ?: -1
        val temperature = forceTemp ?: atmosImage?.weather?.currentTemp ?: 20.0
        val precipitation = atmosImage?.weather?.precipitation ?: 0.0
        
        WeatherEffects(
            weatherCode = displayWeatherCode,
            temperature = temperature,
            precipitation = precipitation,
            forceIntensity = forceIntensity
        )

        // Gradient Scrim for Legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = 300f
                    )
                )
        )

        // Responsive Dashboard Content
        DashboardContent(
            atmosImage = atmosImage,
            isCelsius = isCelsius,
            isCalendarEnabled = isCalendarEnabled,
            onToggleUnit = onToggleUnit,
            forceTemp = forceTemp,
            forceWeatherCode = forceWeatherCode
        )
    }
}

@Composable
private fun WallpaperBackground(bitmap: Bitmap?) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black))
    }
}

@Composable
private fun DashboardContent(
    atmosImage: AtmosImage?,
    isCelsius: Boolean,
    isCalendarEnabled: Boolean,
    onToggleUnit: (() -> Unit)?,
    forceTemp: Double?,
    forceWeatherCode: Int?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (onToggleUnit != null) 24.dp else 48.dp)
    ) {
        // Agenda Section (Left/Top-focused)
        Box(modifier = Modifier
            .weight(0.4f)
            .fillMaxWidth()) {
            if (isCalendarEnabled) {
                AgendaList(events = atmosImage?.calendarEvents)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Environmental Info Section (Bottom-focused)
        Box(modifier = Modifier
            .weight(0.6f)
            .fillMaxWidth()) {
            atmosImage?.let { atmos ->
                EnvironmentalDetails(
                    atmos = atmos,
                    isCelsius = isCelsius,
                    onToggleUnit = onToggleUnit,
                    forceTemp = forceTemp,
                    forceWeatherCode = forceWeatherCode
                )
            }
        }
    }
}

@Composable
private fun AgendaList(events: List<CalendarEvent>?) {
    if (events.isNullOrEmpty()) return

    Column {
        Text(
            text = stringResource(R.string.todays_schedule),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events.take(5)) { event ->
                CalendarEventItem(event)
            }
        }
    }
}

@Composable
private fun EnvironmentalDetails(
    atmos: AtmosImage,
    isCelsius: Boolean,
    onToggleUnit: (() -> Unit)?,
    forceTemp: Double?,
    forceWeatherCode: Int?
) {
    Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
        atmos.weather?.let { weather ->
            // Location Header
            LocationHeader(atmos.locationName)

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

            // Forecast Strip
            ForecastStrip(forecasts = weather.hourlyForecast, isCelsius = isCelsius)

            // Image Provider Insights (Removed hardcoded metadata keys)
            ImageInsights(title = atmos.title, explanation = atmos.explanation)
        }
    }
}

@Composable
private fun LocationHeader(locationName: String?) {
    if (locationName == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = locationName,
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun WeatherPrimaryRow(
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

@Composable
private fun ForecastStrip(
    forecasts: List<com.smartmuseum.wallpaperapp.domain.model.HourlyForecast>,
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

@Composable
private fun ImageInsights(title: String?, explanation: String?) {
    if (title.isNullOrBlank()) return

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = title,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    explanation?.let { text ->
        Text(
            text = if (text.length > 120) text.take(120) + "..." else text,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
fun CalendarEventItem(event: CalendarEvent) {
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val startTime = timeFormatter.format(Date(event.startTime))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = event.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Event,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (event.isAllDay) "All Day" else startTime,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun WeatherIcon(code: Int, isDay: Boolean, modifier: Modifier = Modifier) {
    val iconRes = when (code) {
        0 -> if (isDay) R.drawable.ic_sunny else R.drawable.ic_clear_night
        1, 2, 3 -> if (isDay) R.drawable.outline_partly_cloudy_day_24 else R.drawable.outline_partly_cloudy_night_24
        45, 48 -> R.drawable.outline_foggy_24
        51, 53, 55 -> R.drawable.outline_rainy_light_24
        56, 57 -> R.drawable.outline_weather_mix_24
        61, 63 -> R.drawable.outline_rainy_24
        65 -> R.drawable.outline_rainy_heavy_24
        66, 67 -> R.drawable.outline_rainy_snow_24
        71, 73 -> R.drawable.outline_weather_snowy_24
        75 -> R.drawable.outline_snowing_heavy_24
        77 -> R.drawable.outline_snowing_24
        80, 81 -> R.drawable.outline_rainy_24
        82 -> R.drawable.outline_rainy_heavy_24
        85 -> if (isDay) R.drawable.outline_sunny_snowing_24 else R.drawable.outline_snowing_24
        86 -> R.drawable.outline_snowing_heavy_24
        95 -> R.drawable.outline_thunderstorm_24
        96, 99 -> R.drawable.outline_weather_hail_24
        else -> if (isDay) R.drawable.outline_partly_cloudy_day_24 else R.drawable.outline_partly_cloudy_night_24
    }
    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
    )
}
