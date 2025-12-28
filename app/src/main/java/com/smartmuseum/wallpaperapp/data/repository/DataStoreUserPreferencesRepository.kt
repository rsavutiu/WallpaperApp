package com.smartmuseum.wallpaperapp.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : UserPreferencesRepository {

    private object PreferencesKeys {
        val IS_CELSIUS = booleanPreferencesKey("is_celsius")
        val LAST_UPDATE = longPreferencesKey("last_update")
        val LONGITUDE = doublePreferencesKey("longitude")
        val LATITUDE = doublePreferencesKey("latitude")
        val PREFERRED_PROVIDER = stringPreferencesKey("preferred_provider")
    }

    override val isCelsius: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_CELSIUS] ?: true
    }

    override suspend fun setCelsius(isCelsius: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_CELSIUS] = isCelsius
        }
    }

    override val lastUpdateTimestamp: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_UPDATE] ?: 0L
    }

    override suspend fun updateLastUpdateTimestamp() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_UPDATE] = System.currentTimeMillis()
        }
    }

    override suspend fun getLastKnownLocation(): Pair<Double, Double> {
        val preferences = context.dataStore.data.first()
        return Pair(
            preferences[PreferencesKeys.LATITUDE] ?: 51.5074,
            preferences[PreferencesKeys.LONGITUDE] ?: -0.1278
        )
    }

    override suspend fun setLastKnownLocation(location: Pair<Double, Double>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LATITUDE] = location.first
            preferences[PreferencesKeys.LONGITUDE] = location.second
        }
    }

    override val preferredImageProvider: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PREFERRED_PROVIDER] ?: "Unsplash"
    }

    override suspend fun setPreferredImageProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PREFERRED_PROVIDER] = provider
        }
    }
}
