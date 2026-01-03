package com.smartmuseum.wallpaperapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import com.smartmuseum.wallpaperapp.worker.WallpaperWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class AtmosApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workManager: WorkManager

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleWallpaperWorker()
    }

    private fun scheduleWallpaperWorker() {
        GlobalScope.launch {
            val refreshPeriod = userPreferencesRepository.refreshPeriodInMinutes.first()
            val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(refreshPeriod, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_MANAGER,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }

    companion object {
        const val WORK_MANAGER = "AtmosApplicationWorkManager"
    }
}
