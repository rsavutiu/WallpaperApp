package com.smartmuseum.wallpaperapp.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        val REFRESH_PERIOD = longPreferencesKey("refresh_period")
        val IS_CELSIUS = booleanPreferencesKey("is_celsius")
        val LAST_UPDATE = longPreferencesKey("last_update")
        val LONGITUDE = doublePreferencesKey("longitude")
        val LATITUDE = doublePreferencesKey("latitude")
        val PREFERRED_PROVIDER = stringPreferencesKey("preferred_provider")
        val IS_CALENDAR_ENABLED = booleanPreferencesKey("is_calendar_enabled")
        val USE_LOCATION = booleanPreferencesKey("use_location")
        val FORCED_WEATHER_CODE = intPreferencesKey("forced_weather_code")
        val FORCED_TEMPERATURE = doublePreferencesKey("forced_temperature")
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

    override val isCalendarEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_CALENDAR_ENABLED] ?: false
    }

    override suspend fun setCalendarEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_CALENDAR_ENABLED] = enabled
        }
    }

    override val useLocation: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USE_LOCATION] ?: false
    }

    override suspend fun setUseLocation(useLocation: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_LOCATION] = useLocation
        }
    }

    override val refreshPeriodInMinutes: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.REFRESH_PERIOD] ?: 60L
    }

    override suspend fun setRefreshPeriod(minutes: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REFRESH_PERIOD] = minutes
        }
    }

    override val forcedWeatherCode: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FORCED_WEATHER_CODE]
    }

    override suspend fun setForcedWeatherCode(code: Int?) {
        context.dataStore.edit { preferences ->
            if (code == null) preferences.remove(PreferencesKeys.FORCED_WEATHER_CODE)
            else preferences[PreferencesKeys.FORCED_WEATHER_CODE] = code
        }
    }

    override val forcedTemperature: Flow<Double?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FORCED_TEMPERATURE]
    }

    override suspend fun setForcedTemperature(temp: Double?) {
        context.dataStore.edit { preferences ->
            if (temp == null) preferences.remove(PreferencesKeys.FORCED_TEMPERATURE)
            else preferences[PreferencesKeys.FORCED_TEMPERATURE] = temp
        }
    }
}
