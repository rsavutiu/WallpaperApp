package com.smartmuseum.wallpaperapp.data.repository

import com.smartmuseum.wallpaperapp.BuildConfig
import com.smartmuseum.wallpaperapp.data.remote.NasaApi
import com.smartmuseum.wallpaperapp.data.remote.PexelsApi
import com.smartmuseum.wallpaperapp.data.remote.PixabayApi
import com.smartmuseum.wallpaperapp.data.remote.SourceSplashApi
import com.smartmuseum.wallpaperapp.data.remote.UnsplashApi
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.repository.ImageProvider
import javax.inject.Inject

class UnsplashImageProvider @Inject constructor(
    private val api: UnsplashApi
) : ImageProvider {
    override val name: String = "Unsplash"
    override suspend fun fetchImage(query: String, location: String?): Result<AtmosImage> = try {
        // If location name is available, append it to the query for better local relevance
        val finalQuery = if (!location.isNullOrBlank()) "$query $location" else query
        val response = api.getRandomPhoto(finalQuery, BuildConfig.UNSPLASH_ACCESS_KEY)
        Result.success(AtmosImage(
            id = response.id,
            url = response.urls.regular,
            blurHash = response.blur_hash,
            attribution = "Unsplash / ${response.user.name}",
            title = response.description ?: response.alt_description,
            metadata = mapOf("type" to "unsplash")
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

class NasaImageProvider @Inject constructor(
    private val api: NasaApi
) : ImageProvider {
    override val name: String = "NASA"
    override suspend fun fetchImage(query: String, location: String?): Result<AtmosImage> = try {
        val response = api.getApod(BuildConfig.NASA_API_KEY)
        Result.success(AtmosImage(
            id = (response.date ?: "") + response.title + response.url,
            url = response.hdurl ?: response.url,
            blurHash = null,
            attribution = response.copyright ?: "NASA/JPL",
            title = response.title,
            explanation = response.explanation,
            metadata = mapOf("type" to "nasa")
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

class PixabayImageProvider @Inject constructor(
    private val api: PixabayApi
) : ImageProvider {
    override val name: String = "Pixabay"
    override suspend fun fetchImage(query: String, location: String?): Result<AtmosImage> = try {
        val finalQuery = if (!location.isNullOrBlank()) "$query $location" else query
        val response = api.searchImages(BuildConfig.PIXABAY_API_KEY, finalQuery)
        val hit = response.hits.random()
        Result.success(AtmosImage(
            id = hit.id.toString(),
            url = hit.largeImageURL,
            blurHash = null,
            attribution = "Pixabay / ${hit.user}",
            title = hit.tags,
            metadata = mapOf("type" to "pixabay")
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

class PexelsImageProvider @Inject constructor(
    private val api: PexelsApi
) : ImageProvider {
    override val name: String = "Pexels"
    override suspend fun fetchImage(query: String, location: String?): Result<AtmosImage> = try {
        val finalQuery = if (!location.isNullOrBlank()) "$query $location" else query
        val response = api.searchImages(BuildConfig.PEXELS_API_KEY, finalQuery)
        val photo = response.photos.random()
        Result.success(AtmosImage(
            id = photo.id.toString(),
            url = photo.src.large2x,
            blurHash = null,
            attribution = "Pexels / ${photo.photographer}",
            title = photo.alt,
            metadata = mapOf("type" to "pexels")
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

class SourceSplashImageProvider @Inject constructor(
    private val api: SourceSplashApi
) : ImageProvider {
    override val name: String = "SourceSplash"
    override suspend fun fetchImage(query: String, location: String?): Result<AtmosImage> = try {
        val finalQuery = if (!location.isNullOrBlank()) "$query,$location" else query
        // source.unsplash.com redirects to a random image matching terms
        val url = "https://source.unsplash.com/featured/1920x1080/?$finalQuery"
        val response = api.getRandomImage(url)
        val finalUrl = response.raw().request.url.toString()
        
        Result.success(AtmosImage(
            id = System.currentTimeMillis().toString(),
            url = finalUrl,
            blurHash = null,
            attribution = "SourceSplash (Unsplash Random)",
            title = finalQuery,
            metadata = mapOf("type" to "sourcesplash")
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
