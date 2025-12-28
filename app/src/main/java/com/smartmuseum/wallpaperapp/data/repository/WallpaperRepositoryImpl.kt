package com.smartmuseum.wallpaperapp.data.repository

import com.smartmuseum.wallpaperapp.BuildConfig
import com.smartmuseum.wallpaperapp.data.remote.*
import com.smartmuseum.wallpaperapp.domain.repository.WallpaperRepository
import javax.inject.Inject

class WallpaperRepositoryImpl @Inject constructor(
    private val weatherApi: WeatherApi,
    private val unsplashApi: UnsplashApi,
    private val nasaApi: NasaApi,
    private val pixabayApi: PixabayApi,
    private val pexelsApi: PexelsApi
) : WallpaperRepository {

    override suspend fun getWeather(lat: Double, lon: Double): Result<WeatherResponse> {
        return try {
            Result.success(weatherApi.getWeather(lat, lon))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getNasaApod(): Result<NasaApodResponse> {
        return try {
            Result.success(nasaApi.getApod(BuildConfig.NASA_API_KEY))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUnsplashPhoto(query: String): Result<UnsplashPhotoResponse> {
        return try {
            Result.success(unsplashApi.getRandomPhoto(query, BuildConfig.UNSPLASH_ACCESS_KEY))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPixabayPhoto(query: String): Result<PixabayResponse> {
        return try {
            // Note: Use actual API key from BuildConfig if available
            Result.success(pixabayApi.searchImages("48031231-6ecdb06000600cfdfcbbe06cf", query))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPexelsPhoto(query: String): Result<PexelsResponse> {
        return try {
            Result.success(pexelsApi.searchImages("563492ad6f91700001000001c6f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0", query))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
