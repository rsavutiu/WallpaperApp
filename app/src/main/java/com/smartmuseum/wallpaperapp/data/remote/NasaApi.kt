package com.smartmuseum.wallpaperapp.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface NasaApi {
    @GET("planetary/apod")
    suspend fun getApod(
        @Query("api_key") apiKey: String
    ): NasaApodResponse

    companion object {
        const val BASE_URL = "https://api.nasa.gov/"
    }
}

data class NasaApodResponse(
    val title: String,
    val url: String,
    val hdurl: String?,
    val explanation: String,
    val copyright: String?,
    val date: String?
)
