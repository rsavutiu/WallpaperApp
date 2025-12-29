package com.smartmuseum.wallpaperapp.di

import com.smartmuseum.wallpaperapp.data.remote.NasaApi
import com.smartmuseum.wallpaperapp.data.remote.PexelsApi
import com.smartmuseum.wallpaperapp.data.remote.PixabayApi
import com.smartmuseum.wallpaperapp.data.remote.SourceSplashApi
import com.smartmuseum.wallpaperapp.data.remote.UnsplashApi
import com.smartmuseum.wallpaperapp.data.remote.WeatherApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideWeatherApi(okHttpClient: OkHttpClient): WeatherApi {
        return Retrofit.Builder()
            .baseUrl(WeatherApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUnsplashApi(okHttpClient: OkHttpClient): UnsplashApi {
        return Retrofit.Builder()
            .baseUrl(UnsplashApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UnsplashApi::class.java)
    }

    @Provides
    @Singleton
    fun provideNasaApi(okHttpClient: OkHttpClient): NasaApi {
        return Retrofit.Builder()
            .baseUrl(NasaApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NasaApi::class.java)
    }

    @Provides
    @Singleton
    fun providePixabayApi(okHttpClient: OkHttpClient): PixabayApi {
        return Retrofit.Builder()
            .baseUrl("https://pixabay.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PixabayApi::class.java)
    }

    @Provides
    @Singleton
    fun providePexelsApi(okHttpClient: OkHttpClient): PexelsApi {
        return Retrofit.Builder()
            .baseUrl("https://api.pexels.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PexelsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSourceSplashApi(okHttpClient: OkHttpClient): SourceSplashApi {
        return Retrofit.Builder()
            .baseUrl("https://source.unsplash.com/")
            .client(okHttpClient)
            .build()
            .create(SourceSplashApi::class.java)
    }
}
