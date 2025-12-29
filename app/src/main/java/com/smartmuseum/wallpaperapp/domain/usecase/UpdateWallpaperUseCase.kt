package com.smartmuseum.wallpaperapp.domain.usecase

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.gson.Gson
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class UpdateWallpaperUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(atmosImage: AtmosImage): Boolean = withContext(Dispatchers.IO) {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(atmosImage.url)
            .allowHardware(false)
            .build()

        val result = loader.execute(request)
        if (result is SuccessResult) {
            val originalBitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (originalBitmap != null) {
                // 1. Save the CLEAN original image
                saveBitmapLocally(originalBitmap, "atmos_raw.png")

                // 2. Extract Palette colors for "burnt-in" info
                val palette = Palette.from(originalBitmap).generate()
                val accentColor = palette.getLightVibrantColor(Color.WHITE)

                // 3. Create a copy for the system wallpaper
                val processedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                
                val isCelsius = userPreferencesRepository.isCelsius.first()
                val isCalendarEnabled = userPreferencesRepository.isCalendarEnabled.first()

                // Draw info on the processed bitmap using palette-derived colors
                drawAtmosInfoOnBitmap(
                    processedBitmap,
                    atmosImage,
                    isCelsius,
                    isCalendarEnabled,
                    accentColor
                )

                return@withContext try {
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    wallpaperManager.setBitmap(processedBitmap)
                    
                    saveBitmapLocally(processedBitmap, "atmos_wallpaper.png")
                    saveMetadataLocally(atmosImage)
                    
                    userPreferencesRepository.updateLastUpdateTimestamp()
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.wallpaper_updated, Toast.LENGTH_SHORT).show()
                    }
                    
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        false
    }

    private fun drawAtmosInfoOnBitmap(
        bitmap: Bitmap,
        atmosImage: AtmosImage,
        isCelsius: Boolean,
        isCalendarEnabled: Boolean,
        accentColor: Int
    ) {
        val canvas = Canvas(bitmap)

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = bitmap.height / 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(15f, 0f, 0f, Color.BLACK)
        }

        val accentPaint = Paint(textPaint).apply {
            color = accentColor
        }

        // 1. Draw Calendar Events (Left Side) - ONLY IF ENABLED
        if (isCalendarEnabled) {
            atmosImage.calendarEvents?.let { events ->
                if (events.isNotEmpty()) {
                    val calendarPaint = Paint(textPaint).apply {
                        textSize = bitmap.height / 50f
                    }
                    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                    var calendarY = bitmap.height * 0.2f
                    val calendarX = bitmap.width * 0.05f

                    canvas.drawText(
                        context.getString(R.string.todays_schedule),
                        calendarX,
                        calendarY,
                        accentPaint.apply { textSize = bitmap.height / 45f })
                    calendarY += calendarPaint.textSize * 2.0f

                    events.take(5).forEach { event ->
                        val timeStr =
                            if (event.isAllDay) "All Day" else timeFormatter.format(Date(event.startTime))
                        val eventText = "$timeStr: ${event.title}"

                        canvas.drawText(
                            if (eventText.length > 30) eventText.take(27) + "..." else eventText,
                            calendarX,
                            calendarY,
                            calendarPaint.apply { typeface = Typeface.DEFAULT }
                        )
                        calendarY += calendarPaint.textSize * 1.5f
                    }
                }
            }
        }

        // 2. Draw Weather & Metadata (Mid to Bottom Area)
        var currentY = bitmap.height * 0.5f
        val rightSideX = bitmap.width * 0.45f

        atmosImage.weather?.let { weather ->
            val tempValue =
                if (isCelsius) weather.currentTemp else (weather.currentTemp * 9 / 5) + 32
            val unit = context.getString(if (isCelsius) R.string.unit_c else R.string.unit_f)
            val tempText = "${tempValue.toInt()}$unit"

            // Main Weather Icon
            val iconRes = getWeatherIconRes(weather.weatherCode, weather.isDay)
            val iconBitmap = drawableToBitmap(iconRes)
            if (iconBitmap != null) {
                val iconSize = (bitmap.height / 15f).toInt()
                val scaledIcon = Bitmap.createScaledBitmap(iconBitmap, iconSize, iconSize, true)
                val tintedIcon = tintBitmap(scaledIcon, accentColor)
                canvas.drawBitmap(tintedIcon, rightSideX, currentY - iconSize, null)
            }

            // Temperature
            textPaint.textSize = bitmap.height / 20f
            canvas.drawText(tempText, rightSideX + (bitmap.height / 10f), currentY, textPaint)

            // Condition
            textPaint.textSize = bitmap.height / 45f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            currentY += textPaint.textSize * 1.5f
            canvas.drawText(weather.condition, rightSideX, currentY, textPaint)

            // Hourly Forecast
            if (weather.hourlyForecast.isNotEmpty()) {
                currentY += textPaint.textSize * 2.0f
                val hourlyPaint = Paint(textPaint).apply {
                    textSize = bitmap.height / 60f
                    typeface = Typeface.DEFAULT
                }

                var forecastX = rightSideX
                weather.hourlyForecast.take(5).forEach { forecast ->
                    val hTemp = if (isCelsius) forecast.temp else (forecast.temp * 9 / 5) + 32
                    val hTime = forecast.time.takeLast(5)

                    // Small icon for each hour
                    val hIconRes = getWeatherIconRes(forecast.weatherCode, true)
                    drawableToBitmap(hIconRes)?.let { hIcon ->
                        val hIconSize = (bitmap.height / 45f).toInt()
                        val sHIcon = Bitmap.createScaledBitmap(hIcon, hIconSize, hIconSize, true)
                        canvas.drawBitmap(sHIcon, forecastX, currentY, null)
                    }

                    canvas.drawText(
                        "${hTemp.toInt()}°",
                        forecastX,
                        currentY + (bitmap.height / 25f),
                        hourlyPaint
                    )
                    canvas.drawText(
                        hTime,
                        forecastX,
                        currentY + (bitmap.height / 18f),
                        hourlyPaint.apply { alpha = 180 })

                    forecastX += bitmap.width / 12f
                }
                currentY += bitmap.height / 10f
            }

            // City Name (Accent Color)
            atmosImage.locationName?.let { city ->
                textPaint.textSize = bitmap.height / 50f
                textPaint.color = accentColor
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                currentY += textPaint.textSize * 1.8f
                canvas.drawText(city, rightSideX, currentY, textPaint)
                textPaint.color = Color.WHITE
            }
        }

        // Image Details (Removed hardcoded keys)
        atmosImage.title?.let { title ->
            textPaint.textSize = bitmap.height / 50f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            currentY += textPaint.textSize * 2.0f
            canvas.drawText(title, rightSideX, currentY, textPaint)
        }

        // Attribution
        val attribution = atmosImage.attribution
        textPaint.textSize = bitmap.height / 65f
        textPaint.alpha = 200
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        currentY += textPaint.textSize * 2.0f
        canvas.drawText(attribution, rightSideX, currentY, textPaint)
    }

    private fun tintBitmap(bitmap: Bitmap, color: Int): Bitmap {
        val paint = Paint()
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        val bitmapResult = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmapResult)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return bitmapResult
    }

    fun getWeatherIconRes(code: Int, isDay: Boolean): Int {
        return when (code) {
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
    }

    private fun drawableToBitmap(resId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, resId) ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun saveBitmapLocally(bitmap: Bitmap, fileName: String) {
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun saveMetadataLocally(atmosImage: AtmosImage) {
        val json = Gson().toJson(atmosImage)
        context.openFileOutput("atmos_metadata.json", Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }
    }
}
