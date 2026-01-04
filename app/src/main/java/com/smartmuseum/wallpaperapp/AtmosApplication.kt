package com.smartmuseum.wallpaperapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AtmosApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        const val WORK_MANAGER = "AtmosApplicationWorkManager"
        const val RAW_IMAGE_FILE = "atmos_raw.png"
        const val METADATA_FILE = "atmos_metadata.json"
        const val WALLPAPER_UPDATE_TAG = "WALLPAPER_UPDATE_TAG"
    }
}
