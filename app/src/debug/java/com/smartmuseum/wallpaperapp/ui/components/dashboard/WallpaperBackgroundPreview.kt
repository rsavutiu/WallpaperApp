package com.smartmuseum.wallpaperapp.ui.components.dashboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.smartmuseum.wallpaperapp.R

@Preview
@Composable
private fun WallpaperBackgroundPreview() {
    val context = LocalContext.current
    val bitmap = remember {
        try {
            BitmapFactory.decodeResource(context.resources, R.drawable.preview_wallpaper_small)
        } catch (e: Exception) {
            null
        }
    }
    WallpaperBackground(bitmap = bitmap)
}
