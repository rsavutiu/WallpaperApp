package com.smartmuseum.wallpaperapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.smartmuseum.wallpaperapp.AtmosApplication
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import com.smartmuseum.wallpaperapp.worker.WallpaperWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    // Create a scope for the receiver to run the coroutine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "com.smartmuseum.DEBUG_TRIGGER_BOOT" ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {

            val pendingResult = goAsync() // Informs the OS to keep the process alive briefly

            scope.launch {
                try {
                    // Use .first() instead of collectLatest because we only need
                    // the current value to schedule the work once after boot.
                    val refreshPeriodInMinutes =
                        userPreferencesRepository.refreshPeriodInMinutes.first()

                    val workManager = WorkManager.getInstance(context)
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
                        refreshPeriodInMinutes,
                        TimeUnit.MINUTES
                    )
                        .setConstraints(constraints)
                        .build()

                    workManager.enqueueUniquePeriodicWork(
                        AtmosApplication.WORK_MANAGER,
                        ExistingPeriodicWorkPolicy.KEEP,
                        workRequest
                    )
                } finally {
                    pendingResult.finish() // Must call finish so the broadcast can be released
                }
            }
        }
    }
}
