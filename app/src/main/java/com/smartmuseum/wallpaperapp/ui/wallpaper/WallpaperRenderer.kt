package com.smartmuseum.wallpaperapp.ui.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.ContextCompat
import com.smartmuseum.wallpaperapp.BuildConfig
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.model.HourlyForecast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WallpaperRenderer(private val context: Context) {

    private val shaderPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        isAntiAlias = true
        isFilterBitmap = true
    }
    private var startTime = System.currentTimeMillis()
    private var frameCounter = 0L


    fun drawFrame(
        canvas: Canvas,
        atmosImage: AtmosImage?,
        backgroundBitmap: Bitmap?,
        weatherShader: RuntimeShader?,
        currentXOffset: Float,
        gyroOffsetX: Float,
        gyroOffsetY: Float,
        accentColor: Int,
        isCelsius: Boolean,
        isCalendarEnabled: Boolean,
        forcedTemperature: Double?,
        forcedWeatherCode: Int?,
        lastUpdateTimestamp: Long
    ) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        frameCounter++

        // 0. Clear canvas with a solid color to prevent garbage pixels at edges
        canvas.drawColor(Color.parseColor("#0F172A"))

        // 1. Draw Background with both Swipe and Gyro Parallax
        backgroundBitmap?.let { bmp ->
            val margin = 120f // Increased margin to prevent edge gaps
            val gyroScale = 2.0f

            // Calculate src rect for a larger destination to ensure we have enough bitmap data for parallax
            val src = calculateCenterCropRect(
                bmp.width,
                bmp.height,
                (width + margin * 2).toInt(),
                (height + margin * 2).toInt(),
                currentXOffset
            )

            // Invert gyro offsets for natural parallax and clamp to ensure background always covers the screen
            val maxShift = margin - 10f
            val shiftX = (-gyroOffsetX * gyroScale).coerceIn(-maxShift, maxShift)
            val shiftY = (-gyroOffsetY * gyroScale).coerceIn(-maxShift, maxShift)

            val dst = RectF(
                -margin + shiftX,
                -margin + shiftY,
                width + margin + shiftX,
                height + margin + shiftY
            )
            canvas.drawBitmap(bmp, src, dst.toRect(), null)
        }

        // 2. AGSL Weather Effects
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            weatherShader?.let { shader ->
                val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                shader.setFloatUniform("iResolution", width, height)
                shader.setFloatUniform("iTime", elapsed * 4.0f)
                // Sync shader offset with background movement
                // Note: Shader uses iOffset in uv calculation which shifts content
                shader.setFloatUniform("iOffset", gyroOffsetX * 0.8f, gyroOffsetY * 0.8f)
                shaderPaint.shader = shader
                canvas.drawRect(0f, 0f, width, height, shaderPaint)
            }
        }

        // 3. Scrim
        val scrimPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height * 0.4f,
                intArrayOf(
                    Color.argb(220, 0, 0, 0),
                    Color.argb(120, 0, 0, 0),
                    Color.TRANSPARENT
                ),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width, height, scrimPaint)

        // 4. Content
        drawDashboardContent(
            canvas,
            width,
            height,
            atmosImage,
            currentXOffset,
            gyroOffsetX,
            accentColor,
            isCelsius,
            isCalendarEnabled,
            forcedTemperature,
            forcedWeatherCode,
            lastUpdateTimestamp
        )
    }

    private fun drawDashboardContent(
        canvas: Canvas,
        width: Float,
        height: Float,
        atmosImage: AtmosImage?,
        currentXOffset: Float,
        gyroOffsetX: Float,
        accentColor: Int,
        isCelsius: Boolean,
        isCalendarEnabled: Boolean,
        forcedTemperature: Double?,
        forcedWeatherCode: Int?,
        lastUpdateTimestamp: Long
    ) {
        val margin = width * 0.08f
        var currentY = height * 0.08f

        val textPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(15f, 0f, 0f, Color.BLACK)
        }

        // Swipe and Gyro shift for UI
        val contentParallaxShift =
            ((currentXOffset - 0.5f) * (width * 0.05f)) - (gyroOffsetX * 1.5f)
        val shiftedMargin = margin - contentParallaxShift

        atmosImage?.locationName?.let { loc ->
            textPaint.textSize = height * 0.025f
            textPaint.color = accentColor
            canvas.drawText(loc.uppercase(), shiftedMargin, currentY, textPaint)
            currentY += height * 0.035f
        }

        atmosImage?.weather?.let { weather ->
            val temp = forcedTemperature ?: weather.currentTemp
            val displayWeatherCode = forcedWeatherCode ?: weather.weatherCode
            val tempValue = if (isCelsius) temp else (temp * 9 / 5) + 32
            val unit = if (isCelsius) "°C" else "°F"
            val tempText = "${tempValue.toInt()}$unit"

            textPaint.textSize = height * 0.08f
            textPaint.color = Color.WHITE
            canvas.drawText(
                tempText,
                shiftedMargin + (height * 0.06f),
                currentY + (height * 0.06f),
                textPaint
            )

            val iconRes = getWeatherIconRes(displayWeatherCode, weather.isDay)
            drawTintedIcon(
                canvas,
                iconRes,
                shiftedMargin,
                currentY,
                height * 0.05f,
                Color.WHITE
            )

            currentY += height * 0.09f

            textPaint.textSize = height * 0.03f
            textPaint.typeface = Typeface.DEFAULT
            canvas.drawText(weather.condition, shiftedMargin, currentY, textPaint)
            currentY += height * 0.025f

            textPaint.textSize = height * 0.018f
            textPaint.color = Color.LTGRAY
            val stats = "Humidity: ${weather.humidity}% | Precip: ${
                String.format(
                    "%.1f",
                    weather.precipitation
                )
            }mm"
            canvas.drawText(stats, shiftedMargin, currentY, textPaint)
            currentY += height * 0.05f

            val forecastParallaxShift =
                ((currentXOffset - 0.5f) * (width * 0.3f)) - (gyroOffsetX * 0.4f)
            drawHourlyStrip(
                canvas,
                weather.hourlyForecast,
                shiftedMargin - forecastParallaxShift,
                currentY,
                width - margin * 2,
                height,
                isCelsius
            )
            currentY += height * 0.08f
        }

        if (isCalendarEnabled) {
            atmosImage?.calendarEvents?.let { events ->
                if (events.isNotEmpty()) {
                    textPaint.textSize = height * 0.022f
                    textPaint.color = accentColor
                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("TODAY'S SCHEDULE", shiftedMargin, currentY, textPaint)
                    currentY += height * 0.03f

                    textPaint.typeface = Typeface.DEFAULT
                    textPaint.color = Color.WHITE
                    textPaint.textSize = height * 0.018f
                    events.take(3).forEach { event ->
                        val time = SimpleDateFormat(
                            "HH:mm",
                            Locale.getDefault()
                        ).format(Date(event.startTime))
                        val title =
                            if (event.title.length > 30) event.title.take(27) + "..." else event.title
                        canvas.drawText("$time - $title", shiftedMargin, currentY, textPaint)
                        currentY += height * 0.022f
                    }
                }
            }
        }

        // Attribution and Debug Info
        atmosImage?.let { img ->
            textPaint.textSize = height * 0.015f
            textPaint.color = Color.GRAY
            textPaint.textAlign = Paint.Align.CENTER

            //attribution at bottom
            canvas.drawText(
                img.attribution,
                width / 2f - contentParallaxShift,
                height - 80f,
                textPaint
            )

            // DEBUG ONLY: Show last refresh time
            if (BuildConfig.DEBUG) {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val timeStr = "Last Refresh: ${sdf.format(Date(lastUpdateTimestamp))}"
                textPaint.color = Color.RED
                canvas.drawText(
                    timeStr,
                    width / 2f - contentParallaxShift,
                    height - 200f,
                    textPaint
                )
            }
        }
    }

    private fun drawHourlyStrip(
        canvas: Canvas,
        forecasts: List<HourlyForecast>,
        x: Float,
        y: Float,
        availableWidth: Float,
        screenHeight: Float,
        isCelsius: Boolean
    ) {
        if (forecasts.isEmpty()) return
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = screenHeight * 0.014f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val data = forecasts.take(6)
        val step = availableWidth / (data.size - 1).coerceAtLeast(1)
        data.forEachIndexed { index, forecast ->
            val curX = x + index * step
            val time = forecast.time.takeLast(5)
            canvas.drawText(time, curX, y, paint)
            val iconRes = getWeatherIconRes(forecast.weatherCode, true)
            drawTintedIcon(
                canvas,
                iconRes,
                curX - (screenHeight * 0.012f),
                y + 10f,
                screenHeight * 0.025f,
                Color.WHITE
            )
            val temp = if (isCelsius) forecast.temp else (forecast.temp * 9 / 5) + 32
            canvas.drawText("${temp.toInt()}°", curX, y + (screenHeight * 0.05f), paint)
        }
    }

    private fun drawTintedIcon(
        canvas: Canvas,
        resId: Int,
        x: Float,
        y: Float,
        size: Float,
        color: Int
    ) {
        val drawable =
            ContextCompat.getDrawable(context, resId) ?: return
        drawable.mutate().colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        drawable.setBounds(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt())
        drawable.draw(canvas)
    }

    private fun calculateCenterCropRect(
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        xOffset: Float = 0.5f
    ): Rect {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return Rect(0, 0, 0, 0)
        val srcAspect = srcW.toFloat() / srcH
        val dstAspect = dstW.toFloat() / dstH

        return if (srcAspect > dstAspect) {
            // Image is wider than screen. We use xOffset to scroll the source window.
            val visibleWidthInSrc = (srcH * dstAspect).toInt()
            val totalScrollableWidth = srcW - visibleWidthInSrc
            val left = (totalScrollableWidth * xOffset).toInt()
            Rect(left, 0, left + visibleWidthInSrc, srcH)
        } else {
            // Image is taller than screen.
            val visibleHeightInSrc = (srcW / dstAspect).toInt()
            val topOffset = (srcH - visibleHeightInSrc) / 2
            Rect(0, topOffset, srcW, topOffset + visibleHeightInSrc)
        }
    }

    private fun getWeatherIconRes(code: Int, isDay: Boolean): Int {
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
}

// Extension to safely convert RectF to Rect
private fun RectF.toRect(): Rect = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
