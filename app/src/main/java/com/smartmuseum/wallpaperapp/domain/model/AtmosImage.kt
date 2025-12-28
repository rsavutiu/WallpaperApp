package com.smartmuseum.wallpaperapp.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AtmosImage(
    val id: String,
    val url: String,          // High-res version
    val blurHash: String?,    // For smooth loading transitions
    val attribution: String,   // E.g., "NASA/JPL" or "Unsplash / John Doe"
    val locationName: String? = null, // E.g., "New York"
    val metadata: Map<String, String> = emptyMap(),
    val weather: WeatherData? = null
)

@Serializable
data class WeatherData(
    val currentTemp: Double,
    val condition: String,
    val weatherCode: Int,
    val isDay: Boolean,
    val humidity: Int,
    val precipitation: Double,
    val hourlyForecast: List<HourlyForecast>
)

@Serializable
data class HourlyForecast(
    val time: String,
    val temp: Double,
    val precipitationProb: Int,
    val weatherCode: Int
)
