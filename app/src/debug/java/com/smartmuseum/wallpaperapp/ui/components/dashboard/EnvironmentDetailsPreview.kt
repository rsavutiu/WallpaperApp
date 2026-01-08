package com.smartmuseum.wallpaperapp.ui.components.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage

@Preview
@Composable
fun EnvironmentDetailsPreview(
    @PreviewParameter(AtmosImagePreviewParameterProvider::class) atmosImage: AtmosImage
) {
    EnvironmentalDetails(
        atmos = atmosImage,
        isCelsius = true,
        showLocation = true,
        showTemperature = true,
        showForecast = true,
        onToggleUnit = { },
        forceTemp = null,
        forceWeatherCode = null
    )
}
