package com.smartmuseum.wallpaperapp.ui.components.dashboard

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage

@Preview
@Composable
fun AtmosDashboardPreview(
    @PreviewParameter(AtmosImagePreviewParameterProvider::class) atmosImage: AtmosImage
) {
    val context = LocalContext.current
    val localResources: Resources = LocalResources.current
    val bitmap = remember {
        try {
            BitmapFactory.decodeResource(localResources, R.drawable.preview_wallpaper_small)
        } catch (e: Exception) {
            null
        }
    }

    AtmosDashboard(
        atmosImage = atmosImage,
        currentWallpaper = bitmap,
        isCelsius = true,
        isCalendarEnabled = true
    )
}
