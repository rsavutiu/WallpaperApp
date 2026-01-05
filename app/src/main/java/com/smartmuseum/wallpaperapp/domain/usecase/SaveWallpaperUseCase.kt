package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import com.google.gson.Gson
import com.smartmuseum.wallpaperapp.AtmosApplication
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SaveWallpaperUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(atmosImage: AtmosImage, bitmap: Bitmap) {
        saveBitmapLocally(bitmap, AtmosApplication.RAW_IMAGE_FILE)
        saveMetadataLocally(atmosImage)
    }

    private fun saveBitmapLocally(bitmap: Bitmap, fileName: String) {
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
    }

    private fun saveMetadataLocally(atmosImage: AtmosImage) {
        val json = Gson().toJson(atmosImage)
        context.openFileOutput(AtmosApplication.METADATA_FILE, Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }
    }
}
