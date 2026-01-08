package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.HourlyForecast
import com.smartmuseum.wallpaperapp.domain.model.WeatherData
import com.smartmuseum.wallpaperapp.domain.repository.WallpaperRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GetWeatherUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    private val getSunDataUseCase: GetSunDataUseCase,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(lat: Double, lon: Double): Result<WeatherData> {
        return try {
            val weather = wallpaperRepository.getWeather(lat, lon).getOrThrow()
            val current = weather.current
            val isDay = current.is_day == 1

            // Fetch precise sun data including twilights
            val sunData = getSunDataUseCase(lat, lon)

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
                }.take(12),
                snowfall = current.snowfall,
                rain = current.rain,
                showers = current.showers,
                cloudCover = current.cloud_cover,
                sunrise = sunData.sunrise,
                sunset = sunData.sunset
            )
            Result.success(weatherData)
        } catch (e: Exception) {
            Result.failure(e)
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
