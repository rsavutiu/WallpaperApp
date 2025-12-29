package com.smartmuseum.wallpaperapp.domain.repository

import com.smartmuseum.wallpaperapp.domain.model.AtmosImage

interface ImageProvider {
    val name: String
    suspend fun fetchImage(query: String, location: String? = null): Result<AtmosImage>
}
