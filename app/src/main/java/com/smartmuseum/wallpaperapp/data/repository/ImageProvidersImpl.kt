package com.smartmuseum.wallpaperapp.data.repository

import com.smartmuseum.wallpaperapp.BuildConfig
import com.smartmuseum.wallpaperapp.data.remote.*
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.repository.ImageProvider
import javax.inject.Inject

class UnsplashImageProvider @Inject constructor(
    private val api: UnsplashApi
) : ImageProvider {
    override val name: String = "Unsplash"
    override suspend fun fetchImage(query: String): Result<AtmosImage> = try {
        val response = api.getRandomPhoto(query, BuildConfig.UNSPLASH_ACCESS_KEY)
        Result.success(AtmosImage(
            id = response.id,
            url = response.urls.regular,
            blurHash = response.blur_hash,
            attribution = "Unsplash / ${response.user.name}",
            metadata = mapOf("type" to "unsplash", "description" to (response.description ?: ""))
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

class FlickrImageProvider @Inject constructor(
    private val api: FlickrApi
) : ImageProvider {
    override val name: String = "Flickr"
    override suspend fun fetchImage(query: String): Result<AtmosImage> = try {
        // Assuming query is "latitude,longitude"
        val parts = query.split(",")
        val lat = parts[0].toDouble()
        val lon = parts[1].toDouble()
        val response = api.searchImages(BuildConfig.FLICKR_API_KEY, lat, lon)
        val photo = response.photos.photo.random()
        val photoUrl = "https://farm${photo.farm}.staticflickr.com/${photo.server}/${photo.id}_${photo.secret}_b.jpg"
        Result.success(AtmosImage(
            id = photo.id,
            url = photoUrl,
            blurHash = null,
            attribution = "Flickr",
            metadata = mapOf("type" to "flickr")
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

class NasaImageProvider @Inject constructor(
    private val api: NasaApi
) : ImageProvider {
    override val name: String = "NASA"
    override suspend fun fetchImage(query: String): Result<AtmosImage> = try {
        val response = api.getApod(BuildConfig.NASA_API_KEY)
        Result.success(AtmosImage(
            id = (response.date ?: "") + response.title + response.url,
            url = response.hdurl ?: response.url,
            blurHash = null,
            attribution = response.copyright ?: "NASA/JPL",
            metadata = mapOf("type" to "nasa", "title" to response.title, "explanation" to response.explanation)
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

class PixabayImageProvider @Inject constructor(
    private val api: PixabayApi
) : ImageProvider {
    override val name: String = "Pixabay"
    override suspend fun fetchImage(query: String): Result<AtmosImage> = try {
        val response = api.searchImages(BuildConfig.PIXABAY_API_KEY, query)
        val hit = response.hits.random()
        Result.success(AtmosImage(
            id = hit.id.toString(),
            url = hit.largeImageURL,
            blurHash = null,
            attribution = "Pixabay / ${hit.user}",
            metadata = mapOf("type" to "pixabay", "tags" to hit.tags)
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

class PexelsImageProvider @Inject constructor(
    private val api: PexelsApi
) : ImageProvider {
    override val name: String = "Pexels"
    override suspend fun fetchImage(query: String): Result<AtmosImage> = try {
        val response = api.searchImages(BuildConfig.PEXELS_API_KEY, query)
        val photo = response.photos.random()
        Result.success(AtmosImage(
            id = photo.id.toString(),
            url = photo.src.large2x,
            blurHash = null,
            attribution = "Pexels / ${photo.photographer}",
            metadata = mapOf("type" to "pexels", "alt" to photo.alt)
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

class SourceSplashImageProvider @Inject constructor(
    private val api: SourceSplashApi
) : ImageProvider {
    override val name: String = "SourceSplash"
    override suspend fun fetchImage(query: String): Result<AtmosImage> = try {
        // source.unsplash.com redirects to a random image matching terms
        val url = "https://source.unsplash.com/featured/1920x1080/?$query"
        val response = api.getRandomImage(url)
        val finalUrl = response.raw().request.url.toString()
        
        Result.success(AtmosImage(
            id = System.currentTimeMillis().toString(),
            url = finalUrl,
            blurHash = null,
            attribution = "SourceSplash (Unsplash Random)",
            metadata = mapOf("type" to "sourcesplash", "query" to query)
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
