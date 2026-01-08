package com.smartmuseum.wallpaperapp.ui.components.dashboard

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.model.WeatherData

class AtmosImagePreviewParameterProvider : PreviewParameterProvider<AtmosImage> {
    override val values = sequenceOf(
        AtmosImage(
            id = "debug_0",
            url = "https://www.vecteezy.com/free-photos/beautiful-wallpaper",
            blurHash = "",
            attribution = "Radu (Debug)",
            locationName = "Bucegi Mountains, Romania",
            title = "Mountain Peak",
            explanation = "Dog sitting on mountain peak with slight snow",
            metadata = mapOf(),
            weather = WeatherData(
                currentTemp = 0.1,
                condition = "Snowing",
                weatherCode = 71,
                isDay = true,
                humidity = 80,
                precipitation = 0.5,
                hourlyForecast = listOf(),
                snowfall = 0.5,
                rain = 0.0,
                showers = 0.0,
                cloudCover = 100,
                sunrise = 0,
                sunset = 0
            ),
            calendarEvents = listOf()
        )
    )
}
