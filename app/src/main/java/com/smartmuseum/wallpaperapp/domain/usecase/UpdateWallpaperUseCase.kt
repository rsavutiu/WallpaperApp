package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.gson.Gson
import com.smartmuseum.wallpaperapp.AtmosApplication.Companion.METADATA_FILE
import com.smartmuseum.wallpaperapp.AtmosApplication.Companion.RAW_IMAGE_FILE
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * SOLID Refactored Use Case: Responsible for caching image assets and metadata.
 * Delegates storage concerns to private specialized methods and uses Hilt for DI.
 */
class UpdateWallpaperUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /**
     * Executes the caching operation.
     * @param atmosImage The metadata associated with the scenery.
     * @param useCache If true, re-uses existing local storage instead of performing network I/O.
     */
    suspend operator fun invoke(atmosImage: AtmosImage, useCache: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            val originalBitmap = if (useCache) {
                tryLoadLocalBitmap()
            } else {
                downloadAndSaveBitmap(atmosImage.url)
            }

            if (originalBitmap != null) {
                persistState(atmosImage)
                return@withContext true
            }
            false
        }

    private fun tryLoadLocalBitmap(): Bitmap? {
        val file = File(context.filesDir, RAW_IMAGE_FILE)
        return if (file.exists()) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    private suspend fun downloadAndSaveBitmap(url: String): Bitmap? {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()

        val result = loader.execute(request)
        return if (result is SuccessResult) {
            val bitmap = result.drawable.toBitmap()
            saveBitmapLocally(bitmap)
            bitmap
        } else null
    }

    private fun saveBitmapLocally(bitmap: Bitmap) {
        try {
            context.openFileOutput(RAW_IMAGE_FILE, Context.MODE_PRIVATE).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistState(atmosImage: AtmosImage) {
        try {
            val json = Gson().toJson(atmosImage)
            context.openFileOutput(METADATA_FILE, Context.MODE_PRIVATE).use {
                it.write(json.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
