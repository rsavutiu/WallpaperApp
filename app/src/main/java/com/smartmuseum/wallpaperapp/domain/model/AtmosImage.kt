package com.smartmuseum.wallpaperapp.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AtmosImage(
    val id: String,
    val url: String,          // High-res version
    val blurHash: String?,    // For smooth loading transitions
    val attribution: String,   // E.g., "NASA/JPL" or "Unsplash / John Doe"
    val locationName: String? = null, // E.g., "New York"
    val title: String? = null,
    val explanation: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val weather: WeatherData? = null,
    val calendarEvents: List<CalendarEvent>? = null,
    val sunData: SunData? = null
)

@Serializable
data class WeatherData(
    val currentTemp: Double,
    val condition: String,
    val weatherCode: Int,
    val isDay: Boolean,
    val humidity: Int,
    val precipitation: Double,
    val hourlyForecast: List<HourlyForecast>,
    val snowfall: Double,
    val rain: Double,
    val showers: Double,
    val cloudCover: Int,
    val sunrise: Long = 0L, // Epoch millis
    val sunset: Long = 0L   // Epoch millis
)

@Serializable
data class SunData(
    val astronomicalDawn: Long,
    val astronomicalDusk: Long,
    val civilDawn: Long,
    val civilDusk: Long,
    val nauticalDawn: Long,
    val nauticalDusk: Long,
    val sunrise: Long,
    val sunset: Long
)

@Serializable
data class HourlyForecast(
    val time: String,
    val temp: Double,
    val precipitationProb: Int,
    val weatherCode: Int
)

@Serializable
data class CalendarEvent(
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean
)
