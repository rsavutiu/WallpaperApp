package com.smartmuseum.wallpaperapp.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface UnsplashApi {
    @GET("photos/random")
    suspend fun getRandomPhoto(
        @Query("query") query: String,
        @Query("client_id") clientId: String,
        @Query("orientation") orientation: String = "landscape",
        @Query("content_filter") contentFilter: String = "high"
    ): UnsplashPhotoResponse

    companion object {
        const val BASE_URL = "https://api.unsplash.com/"
    }
}

data class UnsplashPhotoResponse(
    val id: String,
    val urls: UnsplashUrls,
    val user: UnsplashUser,
    val blur_hash: String?,
    val description: String?,
    val alt_description: String?
)

data class UnsplashUrls(
    val full: String,
    val regular: String
)

data class UnsplashUser(
    val name: String
)
