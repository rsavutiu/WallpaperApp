package com.smartmuseum.wallpaperapp.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val isCelsius: Flow<Boolean>
    suspend fun setCelsius(isCelsius: Boolean)

    suspend fun getLastKnownLocation(): Pair<Double, Double>
    suspend fun setLastKnownLocation(location: Pair<Double, Double>)

    val preferredImageProvider: Flow<String>
    suspend fun setPreferredImageProvider(provider: String)

    val isCalendarEnabled: Flow<Boolean>
    suspend fun setCalendarEnabled(enabled: Boolean)

    val useLocation: Flow<Boolean>
    suspend fun setUseLocation(useLocation: Boolean)

    val refreshPeriodInMinutes: Flow<Long>
    suspend fun setRefreshPeriod(minutes: Long)

    val isDynamicWallpaperEnabled: Flow<Boolean>
    suspend fun setDynamicWallpaperEnabled(enabled: Boolean)

    // Wallpaper content settings
    val showLocation: Flow<Boolean>
    suspend fun setShowLocation(show: Boolean)

    val showTemperature: Flow<Boolean>
    suspend fun setShowTemperature(show: Boolean)

    val showForecast: Flow<Boolean>
    suspend fun setShowForecast(show: Boolean)

    val showSunTransistions: Flow<Boolean>
    suspend fun setShowSunTransistions(show: Boolean)

    // Debug forced weather options
    val forcedWeatherCode: Flow<Int?>
    suspend fun setForcedWeatherCode(code: Int?)

    val forcedTemperature: Flow<Double?>
    suspend fun setForcedTemperature(temp: Double?)
}
