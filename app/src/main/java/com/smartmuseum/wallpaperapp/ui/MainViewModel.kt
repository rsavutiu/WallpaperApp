package com.smartmuseum.wallpaperapp.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.Gson
import com.smartmuseum.wallpaperapp.AtmosApplication
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import com.smartmuseum.wallpaperapp.worker.WallpaperWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class MainUiState(
    val currentWallpaper: Bitmap? = null,
    val atmosImage: AtmosImage? = null,
    val workerProgress: String = "",
    val isWorkRunning: Boolean = false,
    val isCelsius: Boolean = true,
    val isLoading: Boolean = false,
    val loadingProgress: Float = 0f,
    val loadingMessage: String = "",
    val preferredProvider: String = "Unsplash"
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val workManager: WorkManager,
    private val application: Application,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refreshData()
        observeWork()
        observePreferences()
        observeUpdateSignal()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.isCelsius.collect { isCelsius ->
                _uiState.value = _uiState.value.copy(isCelsius = isCelsius)
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.preferredImageProvider.collect { provider ->
                _uiState.value = _uiState.value.copy(preferredProvider = provider)
            }
        }
    }

    private fun observeUpdateSignal() {
        viewModelScope.launch {
            userPreferencesRepository.lastUpdateTimestamp.collect { 
                refreshData()
            }
        }
    }

    fun toggleTemperatureUnit() {
        viewModelScope.launch {
            userPreferencesRepository.setCelsius(!_uiState.value.isCelsius)
        }
    }

    fun setImageProvider(provider: String) {
        viewModelScope.launch {
            userPreferencesRepository.setPreferredImageProvider(provider)
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            loadLocalWallpaper()
            loadLocalMetadata()
        }
    }

    private fun loadLocalWallpaper() {
        try {
            val rawFile = File(application.filesDir, "atmos_raw.png")
            val wallpaperFile = File(application.filesDir, "atmos_wallpaper.png")
            
            val fileToLoad = if (rawFile.exists()) rawFile else if (wallpaperFile.exists()) wallpaperFile else null
            
            fileToLoad?.let {
                val bitmap = BitmapFactory.decodeFile(it.absolutePath)
                _uiState.value = _uiState.value.copy(currentWallpaper = bitmap)
            }
        } catch (_: Exception) {
            // Handle error
        }
    }

    private fun loadLocalMetadata() {
        try {
            val file = File(application.filesDir, "atmos_metadata.json")
            if (file.exists()) {
                val json = file.readText()
                val atmosImage = Gson().fromJson(json, AtmosImage::class.java)
                _uiState.value = _uiState.value.copy(atmosImage = atmosImage)
            }
        } catch (_: Exception) {
            // Handle error
        }
    }

    private fun observeWork() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(AtmosApplication.WORK_MANAGER)
                .collectLatest { workInfos ->
                    val workInfo = workInfos.firstOrNull()
                    if (workInfo != null) {
                        val progressMessage = workInfo.progress.getString(WallpaperWorker.PROGRESS_KEY) ?: ""
                        val isRunning = workInfo.state == WorkInfo.State.RUNNING
                        // We only show progress if it's currently RUNNING.
                        // ENQUEUED, SUCCEEDED, FAILED etc. will hide the bar.
                        val shouldShowLoading = isRunning
                        
                        // Map progress message to a float value for the progress bar
                        val progressValue = when (progressMessage) {
                            application.getString(R.string.stage_location) -> 0.25f
                            application.getString(R.string.stage_weather) -> 0.50f
                            application.getString(R.string.stage_wallpaper) -> 0.75f
                            application.getString(R.string.stage_completed) -> 1.0f
                            else -> if (isRunning) 0.1f else 0f
                        }

                        _uiState.value = _uiState.value.copy(
                            workerProgress = progressMessage,
                            isLoading = shouldShowLoading,
                            loadingMessage = if (progressMessage.isEmpty() && isRunning) 
                                application.getString(R.string.initializing) else progressMessage,
                            loadingProgress = progressValue,
                            isWorkRunning = isRunning
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            loadingProgress = 0f,
                            loadingMessage = ""
                        )
                    }
                }
        }
    }
}
