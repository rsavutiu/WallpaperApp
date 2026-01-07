package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import android.util.Log
import com.smartmuseum.wallpaperapp.BuildConfig
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.WeatherData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

class GetImageQueryUseCase @Inject constructor(
    private val getMoonPhaseUseCase: GetMoonPhaseUseCase,
    @ApplicationContext private val context: Context
) {
    operator fun invoke(weather: WeatherData): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val month = calendar.get(Calendar.MONTH)

        val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH) ?: ""
        val season = when (month) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> "Winter"
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> "Spring"
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "Summer"
            Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER -> "Autumn"
            else -> ""
        }

        val tempDescriptor = when {
            weather.currentTemp > 30 -> "Hot"
            weather.currentTemp > 20 -> "Warm"
            weather.currentTemp > 10 -> "Cool"
            weather.currentTemp > 0 -> "Cold"
            else -> "Freezing"
        }

        // Determine Time of Day descriptor
        val timeOfDay = when (hour) {
            in 5..6 -> "Sunrise"
            in 7..10 -> "Morning"
            in 11..16 -> "Day"
            in 17..18 -> "Sunset"
            in 19..20 -> "Twilight"
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

            timeOfDay == "Sunrise" || timeOfDay == "Sunset" -> {
                "Landscape $timeOfDay"
            }

            timeOfDay == "Twilight" -> {
                "Landscape Twilight Blue Hour"
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
        queryParts.add(baseQuery)

        // Add season and month for context (except for rainy/snowy/overcast where it might clutter)
        if (weather.snowfall == 0.0 && weather.rain == 0.0 && weather.showers == 0.0) {
            queryParts.add(season)
            queryParts.add(monthName)
        }

        // Add temperature for daytime queries
        if (timeOfDay != "Night") {
            queryParts.add(tempDescriptor)
        }

        val ret = queryParts.joinToString(" ").replace("  ", " ").trim()

        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Query Generated: $ret (Hour: $hour, Temp: ${weather.currentTemp})")
        }
        return ret
    }

    companion object {
        private const val TAG = "GetImageQueryUseCase"
    }
}
