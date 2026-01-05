package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import android.location.Geocoder
import android.util.Log
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
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import com.smartmuseum.wallpaperapp.domain.repository.WallpaperRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

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
            val weather = wallpaperRepository.getWeather(lat, lon).getOrThrow()
            Log.i(TAG, "Weather: $weather")
            val current = weather.current
            val isDay = current.is_day == 1
            val weatherData = WeatherData(
                currentTemp = current.temperature_2m,
                condition = getWeatherCondition(current.weather_code),
                weatherCode = current.weather_code,
                isDay = isDay,
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
            )

            val useLocation: Boolean = userPreferencesRepository.useLocation.first()
            val locationName: String? = getCityName(lat, lon)
            val isCalendarEnabled = userPreferencesRepository.isCalendarEnabled.first()
            val events = if (isCalendarEnabled) {
                calendarRepository.getTodaysEvents()
            } else null

            // Check if we already have an image and shouldn't refresh it
            val rawFile = File(context.filesDir, RAW_IMAGE_FILE)
            val metadataFile = File(context.filesDir, METADATA_FILE)
            if (!forceRefreshImage && rawFile.exists() && metadataFile.exists()) {
                try {
                    Log.i(TAG, "Using cached image")
                    val json = metadataFile.readText()
                    val cachedImage = Gson().fromJson(json, AtmosImage::class.java)
                    if (cachedImage != null) {
                        Log.i(TAG, "Using cached image SUCCESS")

                        val newImage = cachedImage.copy(
                            weather = weatherData,
                            locationName = locationName,
                            calendarEvents = events
                        )
                        updateMetadata(atmosImage = newImage)
                        return Result.success(value = newImage)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error ${e.message}")
                    Log.e(TAG, " Fallback to fetching a new image if cache is corrupted ")
                }
            }
            Log.w(TAG, "Fallback to fetching a new image")
            // If we reach here, we need to fetch a NEW image (either forced or none exists)
            val preferredProviderName: String =
                userPreferencesRepository.preferredImageProvider.first()

            val (primaryProvider, fallbackProvider) = if (!isDay) {
                nasaProvider to unsplashProvider
            } else {
                when (preferredProviderName) {
                    "Pixabay" -> pixabayProvider to unsplashProvider
                    "Pexels" -> pexelsProvider to unsplashProvider
                    "NASA" -> nasaProvider to unsplashProvider
                    "SourceSplash" -> sourceSplashProvider to unsplashProvider
                    else -> unsplashProvider to pixabayProvider
                }
            }

            val query = if (!isDay) {
                if (current.cloud_cover < 30) {
                    getMoonPhaseQuery()
                } else {
                    "night sky stars nebula"
                }
            } else {
                when {
                    current.snowfall > 0 -> context.getString(R.string.query_snowy)
                    current.rain > 0 || current.showers > 0 -> context.getString(R.string.query_rainy)
                    current.cloud_cover > 70 -> context.getString(R.string.query_overcast)
                    else -> context.getString(R.string.query_sunny)
                }
            }

            val locationQuery: String? = if (useLocation && isDay) locationName else null

            val atmosImageResult = withTimeoutOrNull(10_000L) {
                primaryProvider.fetchImage(query = query, location = locationQuery)
            }

            val atmosImage = if (atmosImageResult?.isSuccess == true) {
                atmosImageResult.getOrThrow()
            } else {
                fallbackProvider.fetchImage(query = query, location = locationQuery).getOrThrow()
            }

            Result.success(
                atmosImage.copy(
                    locationName = locationName,
                    weather = weatherData,
                    calendarEvents = events
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun updateMetadata(atmosImage: AtmosImage) {
        val json = Gson().toJson(atmosImage)
        context.openFileOutput(RAW_IMAGE_FILE, Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }
    }

    private fun getMoonPhaseQuery(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val jd = if (month < 3) {
            val y = year - 1
            val m = month + 12
            (365.25 * y).toInt() + (30.6001 * (m + 1)).toInt() + day + 1720995
        } else {
            (365.25 * year).toInt() + (30.6001 * (month + 1)).toInt() + day + 1720995
        }
        val b = (jd - 2451545) / 29.53.toInt()
        val phase = (jd - 2451545) - (b * 29.53)

        return when {
            phase < 1.84 -> "new moon"
            phase < 5.53 -> "waxing crescent moon"
            phase < 9.22 -> "first quarter moon"
            phase < 12.91 -> "waxing gibbous moon"
            phase < 16.61 -> "full moon"
            phase < 20.30 -> "waning gibbous moon"
            phase < 23.99 -> "last quarter moon"
            phase < 27.68 -> "waning crescent moon"
            else -> "new moon"
        }
    }

    private suspend fun getCityName(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.let { address ->
                address.locality ?: address.subAdminArea ?: address.adminArea
            }
        } catch (_: Exception) {
            null
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
