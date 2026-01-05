package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.WeatherData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GetImageQueryUseCase @Inject constructor(
    private val getMoonPhaseUseCase: GetMoonPhaseUseCase,
    @ApplicationContext private val context: Context
) {
    operator fun invoke(weather: WeatherData): String {
        val isDay = weather.isDay

        return if (!isDay) {
            if (weather.cloudCover < 30) {
                getMoonPhaseUseCase()
            } else {
                context.getString(R.string.query_night_sky)
            }
        } else {
            when {
                weather.snowfall > 0 -> context.getString(R.string.query_snowy)
                weather.rain > 0 || weather.showers > 0 -> context.getString(R.string.query_rainy)
                weather.cloudCover > 70 -> context.getString(R.string.query_overcast)
                else -> context.getString(R.string.query_sunny)
            }
        }
    }
}
