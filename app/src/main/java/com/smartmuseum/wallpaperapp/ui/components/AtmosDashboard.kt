package com.smartmuseum.wallpaperapp.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage

@Composable
fun AtmosDashboard(
    atmosImage: AtmosImage?,
    currentWallpaper: Bitmap?,
    isCelsius: Boolean,
    onToggleUnit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    forceWeatherCode: Int? = null,
    forceTemp: Double? = null,
    forceIntensity: Float? = null
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Background Image
        currentWallpaper?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black))

        // Weather Effects Overlay
        val displayWeatherCode = forceWeatherCode ?: atmosImage?.weather?.weatherCode ?: -1
        val temperature = forceTemp ?: atmosImage?.weather?.currentTemp ?: 20.0
        val precipitation = atmosImage?.weather?.precipitation ?: 0.0
        
        WeatherEffects(
            weatherCode = displayWeatherCode,
            temperature = temperature,
            precipitation = precipitation,
            forceIntensity = forceIntensity
        )

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = 300f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (onToggleUnit != null) 24.dp else 48.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            atmosImage?.let { atmos ->
                atmos.weather?.let { weather ->
                    val currentTemp = if (isCelsius) (forceTemp ?: weather.currentTemp) else ((forceTemp ?: weather.currentTemp) * 9/5) + 32
                    val unitLabel = stringResource(if (isCelsius) R.string.unit_c else R.string.unit_f)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            atmos.locationName?.let { city ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = city,
                                        fontSize = 18.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WeatherIcon(if (forceWeatherCode != null) forceWeatherCode else weather.weatherCode, weather.isDay, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${currentTemp.toInt()}$unitLabel",
                                    fontSize = 56.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = weather.condition,
                                fontSize = 20.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Text(
                                text = stringResource(R.string.humidity_precip_format, weather.humidity, weather.precipitation),
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        
                        onToggleUnit?.let {
                            FilledTonalButton(
                                onClick = it,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(stringResource(if (isCelsius) R.string.unit_f else R.string.unit_c))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(weather.hourlyForecast) { forecast ->
                            val hourlyTemp = if (isCelsius) forecast.temp else (forecast.temp * 9/5) + 32
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = forecast.time.takeLast(5),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                                WeatherIcon(forecast.weatherCode, true, modifier = Modifier.size(24.dp).padding(vertical = 4.dp))
                                Text(
                                    text = "${hourlyTemp.toInt()}°",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    if (atmos.metadata.containsKey("title")) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = atmos.metadata["title"] ?: "",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        atmos.metadata["explanation"]?.let { explanation ->
                            Text(
                                text = if (explanation.length > 200) explanation.take(200) + "..." else explanation,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
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
