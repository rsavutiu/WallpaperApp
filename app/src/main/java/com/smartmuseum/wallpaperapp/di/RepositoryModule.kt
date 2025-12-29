package com.smartmuseum.wallpaperapp.di

import com.smartmuseum.wallpaperapp.data.repository.AndroidCalendarRepository
import com.smartmuseum.wallpaperapp.data.repository.DataStoreUserPreferencesRepository
import com.smartmuseum.wallpaperapp.data.repository.WallpaperRepositoryImpl
import com.smartmuseum.wallpaperapp.domain.repository.CalendarRepository
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import com.smartmuseum.wallpaperapp.domain.repository.WallpaperRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: DataStoreUserPreferencesRepository
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindWallpaperRepository(
        impl: WallpaperRepositoryImpl
    ): WallpaperRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(
        impl: AndroidCalendarRepository
    ): CalendarRepository
}
