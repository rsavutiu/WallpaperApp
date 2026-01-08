package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.smartmuseum.wallpaperapp.AtmosApplication.Companion.METADATA_FILE
import com.smartmuseum.wallpaperapp.AtmosApplication.Companion.RAW_IMAGE_FILE
import com.smartmuseum.wallpaperapp.data.repository.NasaImageProvider
import com.smartmuseum.wallpaperapp.data.repository.PexelsImageProvider
import com.smartmuseum.wallpaperapp.data.repository.PixabayImageProvider
import com.smartmuseum.wallpaperapp.data.repository.SourceSplashImageProvider
import com.smartmuseum.wallpaperapp.data.repository.UnsplashImageProvider
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.repository.CalendarRepository
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

class GetAtmosImageUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val unsplashProvider: UnsplashImageProvider,
    private val nasaProvider: NasaImageProvider,
    private val pixabayProvider: PixabayImageProvider,
    private val pexelsProvider: PexelsImageProvider,
    private val sourceSplashProvider: SourceSplashImageProvider,
    private val calendarRepository: CalendarRepository,
    private val getWeatherUseCase: GetWeatherUseCase,
    private val getLocationNameUseCase: GetLocationNameUseCase,
    private val getImageQueryUseCase: GetImageQueryUseCase,
    @param:ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        lat: Double,
        lon: Double,
        forceRefreshImage: Boolean = false
    ): Result<AtmosImage> {
        return try {
            val weatherData = getWeatherUseCase(lat, lon).getOrThrow()
            Log.i(TAG, "Weather: $weatherData")

            val useLocation: Boolean = userPreferencesRepository.useLocation.first()
            val locationName: String? = getLocationNameUseCase(lat, lon)
            val isCalendarEnabled = userPreferencesRepository.isCalendarEnabled.first()
            val events = if (isCalendarEnabled) {
                calendarRepository.getTodaysEvents()
            } else null

            val isDynamicWallpaper = userPreferencesRepository.isDynamicWallpaperEnabled.first()

            // Check if we already have an image and shouldn't refresh it
            val rawFile = File(context.filesDir, RAW_IMAGE_FILE)
            val metadataFile = File(context.filesDir, METADATA_FILE)

            val shouldRefresh = forceRefreshImage || isDynamicWallpaper

            if (!shouldRefresh && rawFile.exists() && metadataFile.exists()) {
                try {
                    Log.i(TAG, "Using cached image")
                    val json = metadataFile.readText()
                    val cachedImage = Gson().fromJson(json, AtmosImage::class.java)
                    if (cachedImage != null) {
                        Log.i(TAG, "Using cached image SUCCESS")

                        val newImage = cachedImage.copy(
                            weather = weatherData,
                            locationName = locationName,
                            calendarEvents = events
                        )
                        return Result.success(value = newImage)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error ${e.message}")
                    Log.e(TAG, " Fallback to fetching a new image if cache is corrupted ")
                }
            }

            // DO NOT delete the cache here anymore. 
            // We only replace it in UpdateWallpaperUseCase once the new download is successful.

            Log.w(TAG, "Fetching a new image")
            // If we reach here, we need to fetch a NEW image (either forced or none exists)
            val preferredProviderName: String =
                userPreferencesRepository.preferredImageProvider.first()

            val (primaryProvider, fallbackProvider) = if (!weatherData.isDay) {
                nasaProvider to unsplashProvider
            } else {
                when (preferredProviderName) {
                    "Pixabay" -> pixabayProvider to unsplashProvider
                    "Pexels" -> pexelsProvider to unsplashProvider
                    "NASA" -> nasaProvider to unsplashProvider
                    "SourceSplash" -> sourceSplashProvider to unsplashProvider
                    else -> unsplashProvider to pixabayProvider
                }
            }

            val query = getImageQueryUseCase(weatherData, lat, lon)
            val locationQuery: String? = if (useLocation && weatherData.isDay) locationName else null

            val atmosImageResult = withTimeoutOrNull(15_000L) {
                primaryProvider.fetchImage(query = query, location = locationQuery)
            }

            val atmosImage = if (atmosImageResult?.isSuccess == true) {
                atmosImageResult.getOrThrow()
            } else {
                fallbackProvider.fetchImage(query = query, location = locationQuery).getOrThrow()
            }

            Result.success(
                atmosImage.copy(
                    locationName = locationName,
                    weather = weatherData,
                    calendarEvents = events
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val TAG = "GetAtmosImageUseCase"
    }
}
