package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import com.smartmuseum.wallpaperapp.BuildConfig
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.data.Log
import com.smartmuseum.wallpaperapp.domain.model.WeatherData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

class GetImageQueryUseCase @Inject constructor(
    private val getMoonPhaseUseCase: GetMoonPhaseUseCase,
    private val getSunDataUseCase: GetSunDataUseCase,
    @ApplicationContext private val context: Context
) {
    operator fun invoke(weather: WeatherData, lat: Double, lon: Double): String {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH)

        val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH) ?: ""

        val (season, isPolar) = getSeason(lat, month)

        val tempDescriptor = when {
            weather.currentTemp > 35 -> "Torrid"
            weather.currentTemp > 30 -> "Hot"
            weather.currentTemp > 20 -> "Warm"
            weather.currentTemp > 10 -> "Cool"
            weather.currentTemp > 0 -> "Cold"
            weather.currentTemp > -10 -> "Very Cold"
            else -> "Freezing"
        }

        // Precise sun data integration
        val sunData = getSunDataUseCase(lat, lon)

        val timeOfDay = when {
            // Astronomical Dawn (Stars start to fade)
            now in sunData.astronomicalDawn..<sunData.nauticalDawn -> "Astronomical Dawn"
            // Nautical Dawn (Horizon becomes visible)
            now in sunData.nauticalDawn..<sunData.civilDawn -> "Nautical Dawn"
            // Civil Dawn (Golden hour starts)
            now in sunData.civilDawn..<sunData.sunrise -> "Civil Dawn Golden Hour"
            // Sunrise (Sun hits the horizon)
            now in sunData.sunrise..(sunData.sunrise + 1800000) -> "Sunrise"

            // Sunset (Sun drops to horizon)
            now in (sunData.sunset - 2700000)..sunData.sunset -> "Sunset"
            // Civil Dusk (Golden/Blue hour transition)
            now in sunData.sunset..<sunData.civilDusk -> "Civil Twilight Blue Hour"
            // Nautical Dusk (Sky becomes dark blue)
            now in sunData.civilDusk..<sunData.nauticalDusk -> "Nautical Twilight"
            // Astronomical Dusk (Last light fades)
            now in sunData.nauticalDusk..<sunData.astronomicalDusk -> "Astronomical Twilight"

            // Daytime: Between sunrise window and sunset window
            now in (sunData.sunrise + 1800001)..(sunData.sunset - 2700001) -> {
                if (now < sunData.sunrise + 12600000) "Morning" else "Day"
            }
            else -> "Night"
        }

        val baseQuery = when {
            timeOfDay == "Night" -> {
                if (weather.cloudCover < 30) {
                    getMoonPhaseUseCase()
                } else {
                    context.getString(R.string.query_night_sky)
                }
            }
            timeOfDay.contains("Dawn") || timeOfDay.contains("Twilight") || timeOfDay.contains("Dusk") -> {
                "Landscape $timeOfDay"
            }
            timeOfDay == "Sunrise" || timeOfDay == "Sunset" -> {
                "Landscape $timeOfDay"
            }
            else -> {
                // Daytime logic
                when {
                    weather.snowfall > 0 -> context.getString(R.string.query_snowy)
                    weather.rain > 0 || weather.showers > 0 -> context.getString(R.string.query_rainy)
                    weather.cloudCover > 70 -> context.getString(R.string.query_overcast)
                    else -> context.getString(R.string.query_sunny)
                }
            }
        }

        // Build the final query
        val queryParts = mutableListOf<String>()
        if (isPolar) queryParts.add("Polar")
        queryParts.add(baseQuery)

        // Add context for non-heavy weather
        if (weather.snowfall == 0.0 && weather.rain == 0.0 && weather.showers == 0.0) {
            queryParts.add(season)
            queryParts.add(monthName)
        }

        // Add temperature for daytime and transitional queries
        if (timeOfDay != "Night") {
            queryParts.add(tempDescriptor)
        }

        val ret = queryParts.joinToString(" ").replace("  ", " ").trim()

        if (BuildConfig.DEBUG) {
            Log.i(
                TAG,
                "Query Generated: $ret (Lat: $lat, Phase: $timeOfDay, Temp: ${weather.currentTemp})"
            )
        }
        return ret
    }

    private fun getSeason(lat: Double, month: Int): Pair<String, Boolean> {
        val isPolar = lat > 66.5 || lat < -66.5
        val isTropical = lat in -23.5..23.5

        val season = when {
            isTropical -> {
                if (lat >= 0) { // Northern Tropics
                    if (month in 4..8) "Wet Season" else "Dry Season"
                } else { // Southern Tropics
                    if (month in 9..11 || month in 0..2) "Wet Season" else "Dry Season"
                }
            }

            lat >= 0 -> { // Northern Hemisphere (Temperate)
                when (month) {
                    Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> "Winter"
                    Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> "Spring"
                    Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "Summer"
                    else -> "Autumn"
                }
            }

            else -> { // Southern Hemisphere (Temperate)
                when (month) {
                    Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "Winter"
                    Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER -> "Spring"
                    Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> "Summer"
                    else -> "Autumn"
                }
            }
        }
        return season to isPolar
    }

    companion object {
        private const val TAG = "GetImageQueryUseCase"
    }
}
