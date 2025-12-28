package com.smartmuseum.wallpaperapp.domain.repository

import com.smartmuseum.wallpaperapp.data.remote.WeatherResponse
import com.smartmuseum.wallpaperapp.data.remote.NasaApodResponse
import com.smartmuseum.wallpaperapp.data.remote.UnsplashPhotoResponse
import com.smartmuseum.wallpaperapp.data.remote.PixabayResponse
import com.smartmuseum.wallpaperapp.data.remote.PexelsResponse

interface WallpaperRepository {
    suspend fun getWeather(lat: Double, lon: Double): Result<WeatherResponse>
    suspend fun getNasaApod(): Result<NasaApodResponse>
    suspend fun getUnsplashPhoto(query: String): Result<UnsplashPhotoResponse>
    suspend fun getPixabayPhoto(query: String): Result<PixabayResponse>
    suspend fun getPexelsPhoto(query: String): Result<PexelsResponse>
}
