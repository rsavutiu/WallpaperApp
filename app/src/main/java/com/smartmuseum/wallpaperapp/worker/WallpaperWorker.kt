package com.smartmuseum.wallpaperapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.data.Log
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
        const val TAG = "WALLPAPER_WORKER"
    }

    override suspend fun doWork(): Result {

        setProgress(workDataOf(PROGRESS_KEY to context.getString(R.string.stage_location)))

        val useLocation = userPreferencesRepository.useLocation.first()
        val lastKnown = userPreferencesRepository.getLastKnownLocation()

        val targetLocation: Pair<Double, Double> = if (useLocation) {
            val gpsLocation = locationTracker.getCurrentLocation()
            if (gpsLocation != null) {
                Log.i(TAG, "Using GPS location: ${gpsLocation.latitude}, ${gpsLocation.longitude}")
                val coords = Pair(gpsLocation.latitude, gpsLocation.longitude)
                userPreferencesRepository.setLastKnownLocation(coords)
                coords
            } else {
                Log.w(TAG, "GPS failed, falling back to last known: $lastKnown")
                lastKnown
            }
        } else {
            Log.i(TAG, "Manual mode: Using saved location: $lastKnown")
            lastKnown
        }

        setProgress(workDataOf(PROGRESS_KEY to context.getString(R.string.stage_weather)))

        val atmosImageResult = getAtmosImageUseCase(targetLocation.first, targetLocation.second)
        
        return atmosImageResult.fold(
            onSuccess = { atmosImage ->
                setProgress(workDataOf(PROGRESS_KEY to context.getString(R.string.stage_wallpaper)))

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
                Log.e(TAG, "Failed to get Atmos image: ${it.message}")
                Result.retry()
            }
        )
    }
}
