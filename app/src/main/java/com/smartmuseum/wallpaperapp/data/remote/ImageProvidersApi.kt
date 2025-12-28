package com.smartmuseum.wallpaperapp.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

interface PixabayApi {
    @GET("api/")
    suspend fun searchImages(
        @Query("key") apiKey: String,
        @Query("q") query: String,
        @Query("image_type") imageType: String = "photo",
        @Query("orientation") orientation: String = "vertical",
        @Query("safesearch") safeSearch: Boolean = true,
        @Query("per_page") perPage: Int = 20
    ): PixabayResponse
}

interface PexelsApi {
    @GET("v1/search")
    suspend fun searchImages(
        @Header("Authorization") apiKey: String,
        @Query("query") query: String,
        @Query("orientation") orientation: String = "portrait",
        @Query("per_page") perPage: Int = 20
    ): PexelsResponse
}

interface SourceSplashApi {
    // source.unsplash.com is mostly random or based on query without complex JSON
    // We'll use this to just get a random image from a set of collections or tags
    @GET
    suspend fun getRandomImage(@Url url: String): retrofit2.Response<Void>
}

// Data classes for responses
data class PixabayResponse(
    val hits: List<PixabayHit>
)

data class PixabayHit(
    val id: Long,
    val largeImageURL: String,
    val user: String,
    val tags: String
)

data class PexelsResponse(
    val photos: List<PexelsPhoto>
)

data class PexelsPhoto(
    val id: Long,
    val src: PexelsUrl,
    val photographer: String,
    val alt: String
)

data class PexelsUrl(
    val large2x: String,
    val portrait: String,
    val original: String
)
