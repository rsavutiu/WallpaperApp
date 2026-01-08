package com.smartmuseum.wallpaperapp.di

import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.model.WeatherData
import com.smartmuseum.wallpaperapp.domain.repository.SampleDataProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

class DebugSampleDataProvider : SampleDataProvider {
    override fun getSampleAtmosImage(): AtmosImage {
        return AtmosImage(
            id = "debug_0",
            url = "",
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
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DebugModule {

    @Provides
    @Singleton
    fun provideSampleDataProvider(): SampleDataProvider {
        return DebugSampleDataProvider()
    }
}
