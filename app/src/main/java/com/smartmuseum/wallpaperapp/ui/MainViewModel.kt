package com.smartmuseum.wallpaperapp.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.Gson
import com.smartmuseum.wallpaperapp.AtmosApplication
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.data.repository.NasaImageProvider
import com.smartmuseum.wallpaperapp.data.repository.PexelsImageProvider
import com.smartmuseum.wallpaperapp.data.repository.PixabayImageProvider
import com.smartmuseum.wallpaperapp.data.repository.SourceSplashImageProvider
import com.smartmuseum.wallpaperapp.data.repository.UnsplashImageProvider
import com.smartmuseum.wallpaperapp.domain.location.LocationTracker
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.repository.CalendarRepository
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import com.smartmuseum.wallpaperapp.domain.repository.WallpaperRepository
import com.smartmuseum.wallpaperapp.domain.usecase.GetAtmosImageUseCase
import com.smartmuseum.wallpaperapp.domain.usecase.UpdateWallpaperUseCase
import com.smartmuseum.wallpaperapp.worker.WallpaperWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class NavigationEvent {
    object OpenWallpaperPicker : NavigationEvent()
}

data class ProviderImages(
    val providerName: String,
    val images: List<AtmosImage>,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class MainUiState(
    val currentWallpaper: Bitmap? = null,
    val atmosImage: AtmosImage? = null,
    val workerProgress: String = "",
    val rawWorkerProgress: String = "",
    val isWorkRunning: Boolean = false,
    val isCelsius: Boolean = true,
    val isLoading: Boolean = false,
    val loadingProgress: Float = 0f,
    val loadingMessage: String = "",
    val preferredProvider: String = "Unsplash",
    val useLocation: Boolean = false,
    val isCalendarEnabled: Boolean = false,
    val customColorScheme: ColorScheme? = null,
    val providerImages: List<ProviderImages> = emptyList(),
    val isFetchingImages: Boolean = false,
    val refreshPeriodInMinutes: Long = 30,
    val debugWeatherCode: Int? = null,
    val debugTemperature: Double = 20.0
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val workManager: WorkManager,
    private val application: Application,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val unsplashProvider: UnsplashImageProvider,
    private val nasaProvider: NasaImageProvider,
    private val pixabayProvider: PixabayImageProvider,
    private val pexelsProvider: PexelsImageProvider,
    private val sourceSplashProvider: SourceSplashImageProvider,
    private val getAtmosImageUseCase: GetAtmosImageUseCase,
    private val updateWallpaperUseCase: UpdateWallpaperUseCase,
    private val locationTracker: LocationTracker,
    private val wallpaperRepository: WallpaperRepository,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        refreshData()
        observeWork()
        observePreferences()
        observeUpdateSignal()
        fetchAllProviderImages()

        triggerUpdate(openWallpaperPreview = false)
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.isCelsius.collect { isCelsius ->
                _uiState.update { it.copy(isCelsius = isCelsius) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.preferredImageProvider.collect { provider ->
                _uiState.update { it.copy(preferredProvider = provider) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.useLocation.collect { useLocation ->
                _uiState.update { it.copy(useLocation = useLocation) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.isCalendarEnabled.collect { enabled ->
                _uiState.update { it.copy(isCalendarEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.refreshPeriodInMinutes.collect { period ->
                _uiState.update { it.copy(refreshPeriodInMinutes = period) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.forcedWeatherCode.collect { code ->
                _uiState.update { it.copy(debugWeatherCode = code) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.forcedTemperature.collect { temp ->
                _uiState.update { it.copy(debugTemperature = temp ?: 20.0) }
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
            reBurnCurrentWallpaper()
        }
    }

    fun setImageProvider(provider: String) {
        viewModelScope.launch {
            userPreferencesRepository.setPreferredImageProvider(provider)
        }
    }

    fun updateRefreshPeriod(minutes: Long) {
        viewModelScope.launch {
            userPreferencesRepository.setRefreshPeriod(minutes)
            triggerUpdate(openWallpaperPreview = false)
        }
    }

    fun setDebugWeatherCode(code: Int?) {
        viewModelScope.launch {
            userPreferencesRepository.setForcedWeatherCode(code)
        }
    }

    fun setDebugTemperature(temp: Double) {
        viewModelScope.launch {
            userPreferencesRepository.setForcedTemperature(temp)
        }
    }

    fun triggerLiveWallpaper() {
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.OpenWallpaperPicker)
        }
    }

    fun triggerUpdate(openWallpaperPreview: Boolean) {
        viewModelScope.launch {
            /*val period = userPreferencesRepository.refreshPeriodInMinutes.first()

            val periodicRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
                period, TimeUnit.MINUTES
            ).build()*/

            val oneTimeRequest = OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            workManager.enqueueUniqueWork(
                AtmosApplication.WORK_MANAGER + "_immediate",
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )

            if (openWallpaperPreview) {
                _navigationEvent.emit(NavigationEvent.OpenWallpaperPicker)
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            loadLocalWallpaper()
            loadLocalMetadata()
        }
    }

    private fun loadLocalWallpaper() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rawFile = File(application.filesDir, "atmos_raw.png")
                val wallpaperFile = File(application.filesDir, "atmos_wallpaper.png")

                val fileToLoad =
                    if (rawFile.exists()) rawFile else if (wallpaperFile.exists()) wallpaperFile else null

                fileToLoad?.let {
                    val bitmap = BitmapFactory.decodeFile(it.absolutePath)
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        val colorScheme = createColorSchemeFromPalette(palette)
                        withContext(Dispatchers.Main) {
                            _uiState.update { state ->
                                state.copy(
                                    currentWallpaper = bitmap,
                                    customColorScheme = colorScheme
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Handle error
            }
        }
    }

    private fun createColorSchemeFromPalette(palette: Palette): ColorScheme {
        val primary = Color(palette.getVibrantColor(palette.getMutedColor(Color.Blue.toArgb())))
        val secondary =
            Color(palette.getDominantColor(palette.getVibrantColor(Color.Cyan.toArgb())))
        val tertiary =
            Color(palette.getLightVibrantColor(palette.getLightMutedColor(Color.Magenta.toArgb())))

        return darkColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            surface = Color(palette.getDarkMutedColor(Color.DarkGray.toArgb())),
            onPrimary = if (isColorDark(primary)) Color.White else Color.Black,
            onSecondary = if (isColorDark(secondary)) Color.White else Color.Black
        )
    }

    private fun isColorDark(color: Color): Boolean {
        val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
        return luminance < 0.5
    }

    private fun loadLocalMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(application.filesDir, "atmos_metadata.json")
                if (file.exists()) {
                    val json = file.readText()
                    val atmosImage = Gson().fromJson(json, AtmosImage::class.java)
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(atmosImage = atmosImage) }
                    }
                }
            } catch (_: Exception) {
                // Handle error
            }
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
                        val shouldShowLoading = isRunning

                        val currentProvider =
                            userPreferencesRepository.preferredImageProvider.first()
                        val displayProgress = if (progressMessage.isNotEmpty()) {
                            "$currentProvider: $progressMessage"
                        } else progressMessage

                        val progressValue = when (progressMessage) {
                            application.getString(R.string.stage_location) -> 0.0f
                            application.getString(R.string.stage_weather) -> 0.33f
                            application.getString(R.string.stage_wallpaper) -> 0.66f
                            application.getString(R.string.stage_completed) -> 1.0f
                            else -> if (isRunning) 0.05f else 0f
                        }

                        _uiState.update { state ->
                            state.copy(
                                workerProgress = displayProgress,
                                rawWorkerProgress = progressMessage,
                                isLoading = shouldShowLoading,
                                loadingMessage = if (displayProgress.isEmpty() && isRunning)
                                    "$currentProvider: ${application.getString(R.string.initializing)}" else displayProgress,
                                loadingProgress = progressValue,
                                isWorkRunning = isRunning
                            )
                        }
                    } else {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                loadingProgress = 0f,
                                loadingMessage = ""
                            )
                        }
                    }
                }
        }
    }

    fun setCalendarEnabled(bool: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCalendarEnabled(enabled = bool)
            reBurnCurrentWallpaper()
        }
    }

    fun toggleUseLocation() {
        viewModelScope.launch {
            userPreferencesRepository.setUseLocation(!uiState.value.useLocation)
            reBurnCurrentWallpaper()
        }
    }

    private fun reBurnCurrentWallpaper() {
        viewModelScope.launch {
            val currentAtmosImage = _uiState.value.atmosImage ?: return@launch

            val isCalendarEnabled = userPreferencesRepository.isCalendarEnabled.first()
            val events = if (isCalendarEnabled) {
                calendarRepository.getTodaysEvents()
            } else null

            val updatedImage = currentAtmosImage.copy(calendarEvents = events)

            _uiState.update {
                it.copy(
                isLoading = true,
                loadingMessage = "Updating wallpaper info..."
                )
            }

            try {
                updateWallpaperUseCase(updatedImage, useCache = true)
            } finally {
                _uiState.update {
                    it.copy(
                    isLoading = false
                    )
                }
            }
        }
    }

    fun fetchAllProviderImages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingImages = true) }

            val providers = listOf(
                unsplashProvider,
                pexelsProvider,
                pixabayProvider,
                nasaProvider
            )

            val query = "nature landscape"

            val providerImagesList = providers.map {
                ProviderImages(providerName = it.name, images = emptyList(), isLoading = true) 
            }.toMutableList()

            _uiState.update { it.copy(providerImages = providerImagesList.toList()) }

            providers.forEachIndexed { index, provider ->
                launch {
                    try {
                        val result = provider.fetchImages(query, null, count = 15)
                        result.onSuccess { images ->
                            providerImagesList[index] = ProviderImages(
                                providerName = provider.name,
                                images = images,
                                isLoading = false
                            )
                        }.onFailure { error ->
                            providerImagesList[index] = ProviderImages(
                                providerName = provider.name,
                                images = emptyList(),
                                isLoading = false,
                                error = error.message
                            )
                        }
                    } catch (e: Exception) {
                        providerImagesList[index] = ProviderImages(
                            providerName = provider.name,
                            images = emptyList(),
                            isLoading = false,
                            error = e.message
                        )
                    }
                    _uiState.update {
                        it.copy(providerImages = providerImagesList.toList())
                    }
                }
            }
        }
    }

    fun selectImage(atmosImage: AtmosImage) {
        viewModelScope.launch {
            val currentProvider = userPreferencesRepository.preferredImageProvider.first()
            _uiState.update {
                it.copy(
                isLoading = true,
                loadingMessage = "$currentProvider: Getting weather and calendar data..."
                )
            }

            try {
                val location = locationTracker.getCurrentLocation()
                val lastKnownLocation = if (location != null) {
                    val loc = Pair(location.latitude, location.longitude)
                    userPreferencesRepository.setLastKnownLocation(loc)
                    loc
                } else {
                    userPreferencesRepository.getLastKnownLocation()
                }

                val weatherResult = wallpaperRepository.getWeather(
                    lastKnownLocation.first,
                    lastKnownLocation.second
                )

                val weatherData = weatherResult.getOrNull()?.let { weather ->
                    val current = weather.current
                    val isDay = current.is_day == 1
                    com.smartmuseum.wallpaperapp.domain.model.WeatherData(
                        currentTemp = current.temperature_2m,
                        condition = getWeatherCondition(current.weather_code),
                        weatherCode = current.weather_code,
                        isDay = isDay,
                        humidity = current.relative_humidity_2m,
                        precipitation = current.precipitation,
                        hourlyForecast = weather.hourly.time.mapIndexed { index, time ->
                            com.smartmuseum.wallpaperapp.domain.model.HourlyForecast(
                                time = time,
                                temp = weather.hourly.temperature_2m[index],
                                precipitationProb = weather.hourly.precipitation_probability[index],
                                weatherCode = weather.hourly.weather_code[index]
                            )
                        }.take(12)
                    )
                }

                val isCalendarEnabled = userPreferencesRepository.isCalendarEnabled.first()
                val calendarEvents = if (isCalendarEnabled) {
                    calendarRepository.getTodaysEvents()
                } else null

                val locationName = try {
                    val geocoder =
                        android.location.Geocoder(application, java.util.Locale.getDefault())
                    val addresses = geocoder.getFromLocation(
                        lastKnownLocation.first,
                        lastKnownLocation.second,
                        1
                    )
                    addresses?.firstOrNull()?.locality
                        ?: addresses?.firstOrNull()?.subAdminArea
                        ?: addresses?.firstOrNull()?.adminArea
                } catch (_: Exception) {
                    null
                }

                val finalImage = atmosImage.copy(
                    weather = weatherData,
                    calendarEvents = calendarEvents,
                    locationName = locationName
                )

                _uiState.update {
                    it.copy(loadingMessage = "$currentProvider: Setting wallpaper...")
                }

                val success = updateWallpaperUseCase(finalImage)
                if (success) {
                    refreshData()
                }
            } catch (e: Exception) {
                val success = updateWallpaperUseCase(atmosImage)
                if (success) {
                    refreshData()
                }
            } finally {
                _uiState.update {
                    it.copy(
                    isLoading = false,
                    loadingProgress = 0f,
                    loadingMessage = ""
                    )
                }
            }
        }
    }

    private fun getWeatherCondition(code: Int): String {
        return when (code) {
            0 -> application.getString(R.string.condition_clear)
            1, 2, 3 -> application.getString(R.string.condition_cloudy)
            45, 48 -> application.getString(R.string.condition_fog)
            51, 53, 55 -> application.getString(R.string.condition_drizzle)
            56, 57 -> application.getString(R.string.condition_freezing_drizzle)
            61, 63, 65 -> application.getString(R.string.condition_rain)
            66, 67 -> application.getString(R.string.condition_freezing_rain)
            71, 73, 75 -> application.getString(R.string.condition_snow)
            77 -> application.getString(R.string.condition_snow_grains)
            80, 81, 82 -> application.getString(R.string.condition_rain_showers)
            85, 86 -> application.getString(R.string.condition_snow_showers)
            95 -> application.getString(R.string.condition_thunderstorm)
            96, 99 -> application.getString(R.string.condition_thunderstorm_hail)
            else -> application.getString(R.string.condition_unknown)
        }
    }
}
