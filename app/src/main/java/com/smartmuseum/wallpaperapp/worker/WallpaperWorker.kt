package com.smartmuseum.wallpaperapp.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.location.LocationTracker
import com.smartmuseum.wallpaperapp.domain.repository.CalendarRepository
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import com.smartmuseum.wallpaperapp.domain.usecase.GetAtmosImageUseCase
import com.smartmuseum.wallpaperapp.domain.usecase.UpdateWallpaperUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class WallpaperWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val getAtmosImageUseCase: GetAtmosImageUseCase,
    private val updateWallpaperUseCase: UpdateWallpaperUseCase,
    private val locationTracker: LocationTracker,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val calendarRepository: CalendarRepository,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val PROGRESS_KEY = "progress"
    }

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())

        setProgress(workDataOf(PROGRESS_KEY to context.getString(R.string.stage_location)))
        val location = locationTracker.getCurrentLocation()
        val lastKnownLocation: Pair<Double, Double> = if (location != null) {
            val temp = Pair(location.latitude, location.longitude)
            userPreferencesRepository.setLastKnownLocation(temp)
            temp
        }
        else {
            userPreferencesRepository.getLastKnownLocation()
        }

        setProgress(workDataOf(PROGRESS_KEY to context.getString(R.string.stage_weather)))
        val atmosImageResult =
            getAtmosImageUseCase(lastKnownLocation.first, lastKnownLocation.second)
        
        return atmosImageResult.fold(
            onSuccess = { atmosImage ->
                setProgress(workDataOf(PROGRESS_KEY to context.getString(R.string.stage_wallpaper)))

                // Fetch calendar events if enabled
                val isCalendarEnabled = userPreferencesRepository.isCalendarEnabled.first()
                val events = if (isCalendarEnabled) {
                    calendarRepository.getTodaysEvents()
                } else null

                val finalAtmosImage = atmosImage.copy(calendarEvents = events)

                val success = updateWallpaperUseCase(finalAtmosImage)
                if (success) {
                    setProgress(workDataOf(PROGRESS_KEY to context.getString(R.string.stage_completed)))
                    Result.success()
                } else {
                    Result.retry()
                }
            },
            onFailure = {
                Result.retry()
            }
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        // Create the Notification Channel (Required for API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "atmos_updates",
                context.getString(R.string.atmos_wallpaper_updates),
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, "atmos_updates")
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText("Updating weather conditions...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        // Pass the notification and the type declared in the Manifest
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            ForegroundInfo(1, notification)
        }
    }
}
