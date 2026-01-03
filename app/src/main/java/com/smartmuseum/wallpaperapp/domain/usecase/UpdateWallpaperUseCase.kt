package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.gson.Gson
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Repurposed to fetch and cache Atmos data for the Live Wallpaper.
 * No longer sets the system static wallpaper directly.
 */
class UpdateWallpaperUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
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
                    val downloaded = result.drawable.toBitmap()
                    // Save the CLEAN original image
                    saveBitmapLocally(downloaded, "atmos_raw.png")
                    downloaded
                } else null
            }

            if (originalBitmap != null) {
                // 2. Save metadata and trigger update signal
                saveMetadataLocally(atmosImage)
                userPreferencesRepository.updateLastUpdateTimestamp()
                return@withContext true
            }
            false
        }

    private fun saveBitmapLocally(bitmap: Bitmap, fileName: String) {
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
    }

    private fun saveMetadataLocally(atmosImage: AtmosImage) {
        val json = Gson().toJson(atmosImage)
        context.openFileOutput("atmos_metadata.json", Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }
    }
}
