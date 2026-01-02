package com.smartmuseum.wallpaperapp.domain.usecase

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class UpdateWallpaperUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    /**
     * Updates the system wallpaper.
     * @param atmosImage Metadata for the wallpaper.
     * @param useCache If true, attempts to use the locally saved 'atmos_raw.png' instead of downloading.
     */
    suspend operator fun invoke(atmosImage: AtmosImage, useCache: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
        val loader = ImageLoader(context)

            // 1. Get the original bitmap (from cache or network)
            val bitmapToProcess = if (useCache) {
                val rawFile = File(context.filesDir, "atmos_raw.png")
                if (rawFile.exists()) {
                    try {
                        BitmapFactory.decodeFile(rawFile.absolutePath)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            } else null

            val originalBitmap = bitmapToProcess ?: run {
                val request = ImageRequest.Builder(context)
                    .data(atmosImage.url)
                    .allowHardware(false)
                    .build()

                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val downloaded = (result.drawable as? BitmapDrawable)?.bitmap
                    if (downloaded != null) {
                        // Save the CLEAN original image if we just downloaded it
                        saveBitmapLocally(downloaded, "atmos_raw.png")
                    }
                    downloaded
                } else null
            }

            if (originalBitmap != null) {
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
                        Toast.makeText(context, R.string.wallpaper_updated, Toast.LENGTH_SHORT)
                            .show()
                    }

                    true
                } catch (_: Exception) {
                    false
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

        val scrimPaint = Paint().apply {
            color = Color.BLACK
            alpha = 140 // Increased opacity for better legibility as requested
            style = Paint.Style.FILL
        }

        // 1. Draw Weather & Location Info (Top Area)
        var currentY = bitmap.height * 0.15f
        val leftMarginX = bitmap.width * 0.10f

        // Draw Location Name first
        atmosImage.locationName?.let { city ->
            textPaint.textSize = bitmap.height / 50f
            textPaint.color = accentColor
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            // Draw scrim for location
            val textWidth = textPaint.measureText(city)
            val rect = RectF(
                leftMarginX - 15f,
                currentY - textPaint.textSize - 5f,
                leftMarginX + textWidth + 15f,
                currentY + 10f
            )
            canvas.drawRoundRect(rect, 12f, 12f, scrimPaint)

            canvas.drawText(city, leftMarginX, currentY, textPaint)
            currentY += textPaint.textSize * 1.8f + 60
            textPaint.color = Color.WHITE
        }

        // Draw Weather Info
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
                val scaledIcon = iconBitmap.scale(iconSize, iconSize)
                val tintedIcon = tintBitmap(scaledIcon, accentColor)

                // Draw scrim for icon
                canvas.drawRoundRect(
                    RectF(
                        leftMarginX - 10f,
                        currentY - iconSize - 10f,
                        leftMarginX + iconSize + 10f,
                        currentY + 10f
                    ), 12f, 12f, scrimPaint
                )

                canvas.drawBitmap(tintedIcon, leftMarginX, currentY - iconSize, null)
            }

            // Temperature
            textPaint.textSize = bitmap.height / 20f
            val tempWidth = textPaint.measureText(tempText)
            val tempX = leftMarginX + (bitmap.height / 10f)
            canvas.drawRoundRect(
                RectF(
                    tempX - 15f,
                    currentY - textPaint.textSize - 5f,
                    tempX + tempWidth + 15f,
                    currentY + 10f
                ), 12f, 12f, scrimPaint
            )
            canvas.drawText(tempText, tempX, currentY, textPaint)

            // Condition
            textPaint.textSize = bitmap.height / 45f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            currentY += textPaint.textSize * 1.5f + 20
            val condWidth = textPaint.measureText(weather.condition)
            canvas.drawRoundRect(
                RectF(
                    leftMarginX - 15f,
                    currentY - textPaint.textSize - 5f,
                    leftMarginX + condWidth + 15f,
                    currentY + 10f
                ), 12f, 12f, scrimPaint
            )
            canvas.drawText(weather.condition, leftMarginX, currentY, textPaint)

            // Hourly Forecast
            if (weather.hourlyForecast.isNotEmpty()) {
                val hourlyData = weather.hourlyForecast.take(FORECAST_HOURS)

                val hourlyPaint = Paint(textPaint).apply {
                    textSize = bitmap.height / 60f
                    typeface = Typeface.DEFAULT
                    alpha = 255
                    setShadowLayer(10f, 0f, 0f, Color.BLACK)
                    textAlign = Paint.Align.CENTER
                }

                val hIconSize = (bitmap.height / 30f).toInt()
                val lineSpacing = hourlyPaint.textSize * 0.5f

                val columnCount = hourlyData.size
                val totalForecastWidth = bitmap.width * 0.85f
                val columnWidth = totalForecastWidth / columnCount
                val startX = leftMarginX

                val textBounds = android.graphics.Rect()
                hourlyPaint.getTextBounds("00:00", 0, 5, textBounds)
                val timeHeight = textBounds.height().toFloat()

                hourlyPaint.getTextBounds("00°", 0, 3, textBounds)
                val tempHeight = textBounds.height().toFloat()

                currentY += hourlyPaint.textSize * 2.5f
                val timeBaselineY = currentY + timeHeight
                val iconTopY = timeBaselineY + lineSpacing
                val tempBaselineY = iconTopY + hIconSize + lineSpacing + tempHeight

                hourlyData.forEachIndexed { index, forecast ->
                    val centerX = startX + (index * columnWidth) + (columnWidth / 2f)

                    // Draw individual scrim for this forecast item as requested
                    val itemRect = RectF(
                        centerX - (columnWidth * 0.48f),
                        currentY - 15f,
                        centerX + (columnWidth * 0.48f),
                        tempBaselineY + 20f
                    )
                    canvas.drawRoundRect(itemRect, 12f, 12f, scrimPaint)

                    // A. Draw Time
                    val hTime = forecast.time.takeLast(5)
                    hourlyPaint.alpha = 180
                    canvas.drawText(hTime, centerX, timeBaselineY, hourlyPaint)

                    // B. Draw Icon
                    val hIconRes = getWeatherIconRes(forecast.weatherCode, true)
                    drawableToBitmap(hIconRes)?.let { hIcon ->
                        val scaledIcon = hIcon.scale(hIconSize, hIconSize)
                        canvas.drawBitmap(
                            scaledIcon,
                            centerX - (hIconSize / 2f),
                            iconTopY,
                            null
                        )
                    }

                    // C. Draw Temperature
                    val hTemp = if (isCelsius) forecast.temp else (forecast.temp * 9 / 5) + 32
                    val hTempStr = "${hTemp.toInt()}°"
                    hourlyPaint.alpha = 255
                    canvas.drawText(hTempStr, centerX, tempBaselineY, hourlyPaint)
                }

                currentY = tempBaselineY + hourlyPaint.textSize * 2f
            }
        }

        // 2. Draw Calendar Events
        if (isCalendarEnabled) {
            atmosImage.calendarEvents?.let { events ->
                if (events.isNotEmpty()) {
                    val calendarPaint = Paint(textPaint).apply {
                        textSize = bitmap.height / 50f
                        textAlign = Paint.Align.LEFT
                    }
                    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                    var calendarY = bitmap.height * 0.05f
                    val calendarX = bitmap.width * 0.05f

                    val scheduleText = context.getString(R.string.todays_schedule)
                    val sWidth = accentPaint.apply { textSize = bitmap.height / 45f }
                        .measureText(scheduleText)
                    canvas.drawRoundRect(
                        RectF(
                            calendarX - 10f,
                            calendarY - (bitmap.height / 45f) - 5f,
                            calendarX + sWidth + 10f,
                            calendarY + 15f
                        ), 12f, 12f, scrimPaint
                    )
                    canvas.drawText(scheduleText, calendarX, calendarY, accentPaint)
                    calendarY += calendarPaint.textSize * 2.0f

                    events.take(5).forEach { event ->
                        val timeStr =
                            if (event.isAllDay) "All Day" else timeFormatter.format(Date(event.startTime))
                        val eventText = "$timeStr: ${event.title}"
                        val displayEventText =
                            if (eventText.length > 30) eventText.take(27) + "..." else eventText

                        val eWidth = calendarPaint.apply { typeface = Typeface.DEFAULT }
                            .measureText(displayEventText)
                        canvas.drawRoundRect(
                            RectF(
                                calendarX - 10f,
                                calendarY - calendarPaint.textSize - 5f,
                                calendarX + eWidth + 10f,
                                calendarY + 10f
                            ), 12f, 12f, scrimPaint
                        )

                        canvas.drawText(displayEventText, calendarX, calendarY, calendarPaint)
                        calendarY += calendarPaint.textSize * 1.5f
                    }
                }
            }
        }
    }

    private fun tintBitmap(bitmap: Bitmap, color: Int): Bitmap {
        val paint = Paint()
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        val bitmapResult = createBitmap(bitmap.width, bitmap.height)
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
        val bitmap = createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1)
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

    companion object {
        private const val FORECAST_HOURS = 8
    }
}
