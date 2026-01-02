package com.smartmuseum.wallpaperapp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.ui.MainUiState
import com.smartmuseum.wallpaperapp.ui.components.ProviderImageSelector

@Composable
fun WallpaperScreen(
    uiState: MainUiState,
    onImageSelected: (AtmosImage) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ProviderImageSelector(
            uiState = uiState,
            onImageSelected = onImageSelected,
            modifier = Modifier.fillMaxSize()
        )
    }
}
