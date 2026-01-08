package com.smartmuseum.wallpaperapp.ui.components.dashboard

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.ui.components.WeatherEffects

@Composable
fun AtmosDashboard(
    modifier: Modifier = Modifier,
    atmosImage: AtmosImage?,
    currentWallpaper: Bitmap?,
    isCelsius: Boolean,
    isCalendarEnabled: Boolean,
    showLocation: Boolean = true,
    showTemperature: Boolean = true,
    showForecast: Boolean = true,
    onToggleUnit: (() -> Unit)? = null,
    forceWeatherCode: Int? = null,
    forceTemp: Double? = null,
    forceIntensity: Float? = null
) {
    Log.d(
        "AtmosDashboard",
        "Dashboard Recomposing: Image=${atmosImage != null}, Bitmap=${currentWallpaper != null}"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Background Image Layer
        WallpaperBackground(currentWallpaper)

        // Weather Effects Layer (AGSL/Shaders)
        val weatherData = atmosImage?.weather
        val displayWeatherCode = forceWeatherCode ?: weatherData?.weatherCode ?: -1
        val temperature = forceTemp ?: weatherData?.currentTemp ?: 20.0
        val precipitation = weatherData?.precipitation ?: 0.0

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
            showLocation = showLocation,
            showTemperature = showTemperature,
            showForecast = showForecast,
            onToggleUnit = onToggleUnit,
            forceTemp = forceTemp,
            forceWeatherCode = forceWeatherCode
        )
    }
}
