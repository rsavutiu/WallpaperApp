package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import com.google.gson.Gson
import com.smartmuseum.wallpaperapp.AtmosApplication.Companion.METADATA_FILE
import com.smartmuseum.wallpaperapp.AtmosApplication.Companion.RAW_IMAGE_FILE
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.data.repository.NasaImageProvider
import com.smartmuseum.wallpaperapp.data.repository.PexelsImageProvider
import com.smartmuseum.wallpaperapp.data.repository.PixabayImageProvider
import com.smartmuseum.wallpaperapp.data.repository.SourceSplashImageProvider
import com.smartmuseum.wallpaperapp.data.repository.UnsplashImageProvider
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.model.HourlyForecast
import com.smartmuseum.wallpaperapp.domain.model.WeatherData
import com.smartmuseum.wallpaperapp.domain.repository.CalendarRepository
import com.smartmuseum.wallpaperapp.domain.repository.ImageProvider
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import com.smartmuseum.wallpaperapp.domain.repository.WallpaperRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

/**
 * Use case responsible for retrieving the environmental state (weather, location, scenery).
 * Adheres to SOLID by delegating image fetching and caching to specific components.
 */
class GetAtmosImageUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val unsplashProvider: UnsplashImageProvider,
    private val nasaProvider: NasaImageProvider,
    private val pixabayProvider: PixabayImageProvider,
    private val pexelsProvider: PexelsImageProvider,
    private val sourceSplashProvider: SourceSplashImageProvider,
    private val calendarRepository: CalendarRepository,
    @param:ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        lat: Double,
        lon: Double,
        forceRefreshImage: Boolean = false
    ): Result<AtmosImage> {
        return try {
            val weatherData = fetchWeatherData(lat, lon).getOrThrow()

            val isCalendarEnabled = userPreferencesRepository.isCalendarEnabled.first()
            val events = if (isCalendarEnabled) {
                calendarRepository.getTodaysEvents()
            } else null

            // Check for persistent cache to avoid unnecessary network calls
            getCachedImage(forceRefreshImage)?.let { cached ->
                val newImage = cached.copy(
                    weather = weatherData,
                    calendarEvents = events
                )
                saveMetadata(newImage)
                return Result.success(newImage)
            }

            // Fetch new scenery if no cache exists or refresh is forced
            val atmosImage = fetchNewScenery(weatherData.isDay).getOrThrow()

            Result.success(
                atmosImage.copy(
                    weather = weatherData,
                    calendarEvents = events
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchWeatherData(lat: Double, lon: Double): Result<WeatherData> {
        return try {
            val weather = wallpaperRepository.getWeather(lat, lon).getOrThrow()
            val current = weather.current
            Result.success(
                WeatherData(
                currentTemp = current.temperature_2m,
                condition = getWeatherCondition(current.weather_code),
                weatherCode = current.weather_code,
                    isDay = current.is_day == 1,
                humidity = current.relative_humidity_2m,
                precipitation = current.precipitation,
                hourlyForecast = weather.hourly.time.mapIndexed { index, time ->
                    HourlyForecast(
                        time = time,
                        temp = weather.hourly.temperature_2m[index],
                        precipitationProb = weather.hourly.precipitation_probability[index],
                        weatherCode = weather.hourly.weather_code[index]
                    )
                }.take(12)
                ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getCachedImage(forceRefresh: Boolean): AtmosImage? {
        if (forceRefresh) return null

        val rawFile = File(context.filesDir, RAW_IMAGE_FILE)
        val metadataFile = File(context.filesDir, METADATA_FILE)

        if (rawFile.exists() && metadataFile.exists()) {
            return try {
                val json = metadataFile.readText()
                Gson().fromJson(json, AtmosImage::class.java)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private suspend fun fetchNewScenery(isDay: Boolean): Result<AtmosImage> {
        val preferredProviderName = userPreferencesRepository.preferredImageProvider.first()
        val provider = getProvider(preferredProviderName, isDay)

        val query =
            if (!isDay) "night sky stars nebula" else context.getString(R.string.query_sunny)

        return withTimeoutOrNull(10_000L) {
            provider.fetchImage(query = query, location = null)
        } ?: Result.failure(Exception("Scenery fetch timeout"))
    }

    private fun getProvider(name: String, isDay: Boolean): ImageProvider {
        if (!isDay) return nasaProvider
        return when (name) {
            "Pixabay" -> pixabayProvider
            "Pexels" -> pexelsProvider
            "NASA" -> nasaProvider
            "SourceSplash" -> sourceSplashProvider
            else -> unsplashProvider
        }
    }

    private fun saveMetadata(atmosImage: AtmosImage) {
        val json = Gson().toJson(atmosImage)
        context.openFileOutput(METADATA_FILE, Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }
    }

    private fun getWeatherCondition(code: Int): String {
        return when (code) {
            0 -> context.getString(R.string.condition_clear)
            1, 2, 3 -> context.getString(R.string.condition_cloudy)
            45, 48 -> context.getString(R.string.condition_fog)
            51, 53, 55 -> context.getString(R.string.condition_drizzle)
            56, 57 -> context.getString(R.string.condition_freezing_drizzle)
            61, 63, 65 -> context.getString(R.string.condition_rain)
            66, 67 -> context.getString(R.string.condition_freezing_rain)
            71, 73, 75 -> context.getString(R.string.condition_snow)
            77 -> context.getString(R.string.condition_snow_grains)
            80, 81, 82 -> context.getString(R.string.condition_rain_showers)
            85, 86 -> context.getString(R.string.condition_snow_showers)
            95 -> context.getString(R.string.condition_thunderstorm)
            96, 99 -> context.getString(R.string.condition_thunderstorm_hail)
            else -> context.getString(R.string.condition_unknown)
        }
    }

    companion object {
        const val TAG = "GetAtmosImageUseCase"
    }
}
