package com.smartmuseum.wallpaperapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.smartmuseum.wallpaperapp.AtmosApplication
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import com.smartmuseum.wallpaperapp.worker.WallpaperWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var workManager: WorkManager

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            val pendingResult = goAsync()
            GlobalScope.launch {
                try {
                    val refreshPeriod = userPreferencesRepository.refreshPeriodInMinutes.first()
                    val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(refreshPeriod, TimeUnit.MINUTES)
                        .build()

                    workManager.enqueueUniquePeriodicWork(
                        AtmosApplication.WORK_MANAGER,
                        ExistingPeriodicWorkPolicy.KEEP,
                        workRequest
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
