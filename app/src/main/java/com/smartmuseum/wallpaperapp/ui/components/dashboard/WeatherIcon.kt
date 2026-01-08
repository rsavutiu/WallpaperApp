package com.smartmuseum.wallpaperapp.ui.components.dashboard

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.smartmuseum.wallpaperapp.R

@Composable
fun WeatherIcon(code: Int, isDay: Boolean, modifier: Modifier = Modifier) {
    val safeDefault = R.drawable.outline_partly_cloudy_day_24

    val iconRes = try {
        when (code) {
            0 -> if (isDay) R.drawable.ic_sunny else R.drawable.ic_clear_night
            1, 2, 3 -> if (isDay) R.drawable.outline_partly_cloudy_day_24 else R.drawable.outline_partly_cloudy_night_24
            45, 48 -> R.drawable.outline_foggy_24
            51, 53, 55 -> R.drawable.outline_rainy_light_24
            56, 57 -> R.drawable.outline_weather_mix_24
            61, 63 -> R.drawable.outline_rainy_24
            65 -> R.drawable.outline_rainy_heavy_24
            66, 67 -> R.drawable.outline_rainy_snow_24
            71, 73 -> R.drawable.outline_weather_snowy_24
            75 -> R.drawable.outline_snowing_heavy_24
            77 -> R.drawable.outline_snowing_24
            80, 81 -> R.drawable.outline_rainy_24
            82 -> R.drawable.outline_rainy_heavy_24
            85 -> if (isDay) R.drawable.outline_sunny_snowing_24 else R.drawable.outline_snowing_24
            86 -> R.drawable.outline_snowing_heavy_24
            95 -> R.drawable.outline_thunderstorm_24
            96, 99 -> R.drawable.outline_weather_hail_24
            else -> if (isDay) R.drawable.outline_partly_cloudy_day_24 else R.drawable.outline_partly_cloudy_night_24
        }
    } catch (_: Exception) {
        safeDefault
    }

    Icon(
        painter = painterResource(id = if (iconRes != 0) iconRes else safeDefault),
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
    )
}

@Preview
@Composable
private fun WeatherIconPreview() {
    WeatherIcon(code = 0, isDay = true)
}
