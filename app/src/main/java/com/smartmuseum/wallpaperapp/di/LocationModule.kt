package com.smartmuseum.wallpaperapp.di

import android.app.Application
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    companion object {
        @Provides
        @Singleton
        fun provideFusedLocationProviderClient(application: Application): FusedLocationProviderClient {
            return LocationServices.getFusedLocationProviderClient(application)
        }
    }
}
