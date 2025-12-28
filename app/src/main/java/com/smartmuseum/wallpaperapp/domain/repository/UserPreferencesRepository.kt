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

    val useLocation: Flow<Boolean>
    suspend fun setUseLocation(useLocation: Boolean)
}
