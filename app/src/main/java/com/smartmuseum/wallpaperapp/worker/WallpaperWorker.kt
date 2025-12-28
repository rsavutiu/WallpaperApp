package com.smartmuseum.wallpaperapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.location.LocationTracker
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import com.smartmuseum.wallpaperapp.domain.usecase.GetAtmosImageUseCase
import com.smartmuseum.wallpaperapp.domain.usecase.UpdateWallpaperUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class WallpaperWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val getAtmosImageUseCase: GetAtmosImageUseCase,
    private val updateWallpaperUseCase: UpdateWallpaperUseCase,
    private val locationTracker: LocationTracker,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val PROGRESS_KEY = "progress"
    }

    override suspend fun doWork(): Result {
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
        val atmosImageResult = getAtmosImageUseCase(lastKnownLocation.first, lastKnownLocation.second)
        
        return atmosImageResult.fold(
            onSuccess = { atmosImage ->
                setProgress(workDataOf(PROGRESS_KEY to context.getString(R.string.stage_wallpaper)))
                val success = updateWallpaperUseCase(atmosImage)
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
}
