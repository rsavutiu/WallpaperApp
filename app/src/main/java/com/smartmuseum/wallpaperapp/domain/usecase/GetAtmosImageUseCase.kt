package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import android.location.Geocoder
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
    suspend operator fun invoke(lat: Double, lon: Double): Result<AtmosImage> {
        return try {
            val weather = wallpaperRepository.getWeather(lat, lon).getOrThrow()
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
            val preferredProviderName: String =
                userPreferencesRepository.preferredImageProvider.first()
            val isCalendarEnabled = userPreferencesRepository.isCalendarEnabled.first()

            // Always use NASA at night
            val provider: ImageProvider = if (!isDay) nasaProvider else
                when (preferredProviderName) {
                    "Pixabay" -> pixabayProvider
                    "Pexels" -> pexelsProvider
                    "NASA" -> nasaProvider
                    "SourceSplash" -> sourceSplashProvider
                    else -> unsplashProvider
                }

            val query = if (!isDay) {
                // If it's night and not too cloudy, search for the current moon phase
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

            // If user enabled "Use Location for Wallpapers", we use the city name to narrow down the search
            val locationQuery: String? = if (useLocation && isDay) locationName else null

            val atmosImageResult = provider.fetchImage(query = query, location = locationQuery)
            val atmosImage = atmosImageResult.getOrThrow()

            val events = if (isCalendarEnabled) {
                calendarRepository.getTodaysEvents()
            } else null

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

    private fun getMoonPhaseQuery(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        // Simple moon phase approximation
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
}
