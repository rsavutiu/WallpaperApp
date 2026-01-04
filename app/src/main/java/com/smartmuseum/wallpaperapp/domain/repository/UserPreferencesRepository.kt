package com.smartmuseum.wallpaperapp.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val isCelsius: Flow<Boolean>
    suspend fun setCelsius(isCelsius: Boolean)
    
    val lastUpdateTimestamp: Flow<Long>
    suspend fun updateLastUpdateTimestamp()

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

    // Debug forced weather options
    val forcedWeatherCode: Flow<Int?>
    suspend fun setForcedWeatherCode(code: Int?)

    val forcedTemperature: Flow<Double?>
    suspend fun setForcedTemperature(temp: Double?)
}
