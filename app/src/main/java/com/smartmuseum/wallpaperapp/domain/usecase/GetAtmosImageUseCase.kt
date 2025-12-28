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
import com.smartmuseum.wallpaperapp.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(lat: Double, lon: Double): Result<AtmosImage> {
        return try {
            val weather = wallpaperRepository.getWeather(lat, lon).getOrThrow()
            val current = weather.current

            val weatherData = WeatherData(
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
                }.take(8)
            )

            val locationName = getCityName(lat, lon)
            val preferredProviderName = userPreferencesRepository.preferredImageProvider.first()

            val provider: ImageProvider = when {
                current.is_day == 0 -> nasaProvider // Still use NASA for night
                else -> when (preferredProviderName) {
                    "Pixabay" -> pixabayProvider
                    "Pexels" -> pexelsProvider
                    "NASA" -> nasaProvider
                    "SourceSplash" -> sourceSplashProvider
                    else -> unsplashProvider
                }
            }

            val query = when {
                current.snowfall > 0 -> context.getString(R.string.query_snowy)
                current.rain > 0 || current.showers > 0 -> context.getString(R.string.query_rainy)
                current.cloud_cover > 70 -> context.getString(R.string.query_overcast)
                else -> context.getString(R.string.query_sunny)
            }

            val atmosImage = provider.fetchImage(query).getOrThrow()
            
            Result.success(
                atmosImage.copy(
                    locationName = locationName,
                    weather = weatherData
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getCityName(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.let { address ->
                address.locality ?: address.subAdminArea ?: address.adminArea
            }
        } catch (e: Exception) {
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
