package com.smartmuseum.wallpaperapp.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface FlickrApi {
    @GET("services/rest/?method=flickr.photos.search")
    suspend fun searchImages(
        @Query("api_key") apiKey: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("nojsoncallback") noJsonCallback: Int = 1,
        @Query("radius") radius: Int = 20,
        @Query("sort") sort: String = "interestingness-desc",
        @Query("per_page") perPage: Int = 20
    ): FlickrResponse

    companion object {
        const val BASE_URL = "https://api.flickr.com/"
    }
}

data class FlickrResponse(
    val photos: FlickrPhotos
)

data class FlickrPhotos(
    val photo: List<FlickrPhoto>
)

data class FlickrPhoto(
    val id: String,
    val secret: String,
    val server: String,
    val farm: Int
)
