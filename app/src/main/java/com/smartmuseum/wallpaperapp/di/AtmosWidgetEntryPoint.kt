package com.smartmuseum.wallpaperapp.di

import com.smartmuseum.wallpaperapp.domain.usecase.UpdateWallpaperUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AtmosWidgetEntryPoint {
    fun updateWallpaperUseCase(): UpdateWallpaperUseCase
}
