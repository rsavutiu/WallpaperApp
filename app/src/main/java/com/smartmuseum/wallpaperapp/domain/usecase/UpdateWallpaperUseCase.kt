package com.smartmuseum.wallpaperapp.domain.usecase

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.core.content.ContextCompat
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
                // 1. Save the CLEAN original image for the Live App UI and TV Screensaver
                saveBitmapLocally(originalBitmap, "atmos_raw.png")

                // 2. Create a copy for the system wallpaper with "burnt-in" info
                val processedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                
                val isCelsius = userPreferencesRepository.isCelsius.first()
                
                // Draw weather info on the processed bitmap
                atmosImage.weather?.let { weather ->
                    val tempValue = if (isCelsius) weather.currentTemp else (weather.currentTemp * 9/5) + 32
                    val unit = context.getString(if (isCelsius) R.string.unit_c else R.string.unit_f)
                    drawAtmosInfoOnBitmap(processedBitmap, "${tempValue.toInt()}$unit", weather.condition, atmosImage)
                }

                return@withContext try {
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    // Set the burnt-in version as system wallpaper
                    wallpaperManager.setBitmap(processedBitmap)
                    
                    // Also save the burnt-in version as a secondary file
                    saveBitmapLocally(processedBitmap, "atmos_wallpaper.png")
                    saveMetadataLocally(atmosImage)
                    
                    userPreferencesRepository.updateLastUpdateTimestamp()
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.wallpaper_updated, Toast.LENGTH_SHORT).show()
                    }
                    
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
        false
    }

    private fun drawAtmosInfoOnBitmap(bitmap: Bitmap, temp: String, condition: String, atmosImage: AtmosImage) {
        val canvas = Canvas(bitmap)
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = bitmap.height / 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(10f, 0f, 0f, Color.BLACK)
        }

        val margin = bitmap.width * 0.05f
        var currentY = bitmap.height * 0.82f

        // 1. Draw Weather Icon
        atmosImage.weather?.let { weather ->
            val iconRes = getWeatherIconRes(weather.weatherCode, weather.isDay)
            val iconBitmap = drawableToBitmap(iconRes)
            if (iconBitmap != null) {
                val iconSize = (bitmap.height / 15f).toInt()
                val scaledIcon = Bitmap.createScaledBitmap(iconBitmap, iconSize, iconSize, true)
                canvas.drawBitmap(scaledIcon, margin, currentY - iconSize, null)
                currentY += margin * 0.5f 
            }
        }

        // 2. Draw Temp & Condition
        canvas.drawText(temp, margin, currentY, textPaint)
        
        textPaint.textSize = bitmap.height / 45f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        currentY += textPaint.textSize * 1.5f
        canvas.drawText(condition, margin, currentY, textPaint)

        // 3. Draw City Name
        atmosImage.locationName?.let { city ->
            textPaint.textSize = bitmap.height / 50f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            currentY += textPaint.textSize * 1.8f
            canvas.drawText(city, margin, currentY, textPaint)
        }

        // 4. Draw Attribution
        val type = atmosImage.metadata["type"] ?: ""
        val sourceInfo = if (type == context.getString(R.string.metadata_type_nasa)) {
            atmosImage.metadata["title"] ?: context.getString(R.string.attribution_nasa)
        } else {
            atmosImage.attribution
        }
        
        textPaint.textSize = bitmap.height / 65f
        textPaint.alpha = 180
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        currentY += textPaint.textSize * 2.0f
        canvas.drawText(sourceInfo, margin, currentY, textPaint)
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
