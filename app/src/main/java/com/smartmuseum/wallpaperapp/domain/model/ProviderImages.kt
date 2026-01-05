package com.smartmuseum.wallpaperapp.domain.model

data class ProviderImages(
    val providerName: String,
    val images: List<AtmosImage>,
    val isLoading: Boolean = false,
    val error: String? = null
)
