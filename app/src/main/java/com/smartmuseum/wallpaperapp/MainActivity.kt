package com.smartmuseum.wallpaperapp

import android.app.UiModeManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.smartmuseum.wallpaperapp.ui.MainApp
import com.smartmuseum.wallpaperapp.ui.MainViewModel
import com.smartmuseum.wallpaperapp.ui.NavigationEvent
import com.smartmuseum.wallpaperapp.ui.components.LocationPickerDialog
import com.smartmuseum.wallpaperapp.ui.wallpaper.AtmosLiveWallpaperService
import dagger.hilt.android.AndroidEntryPoint

fun isAndroidTV(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var isTV: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen before calling super.onCreate()
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        isTV = isAndroidTV(this)
        enableEdgeToEdge()

        if (isTV) {
            viewModel.triggerUpdate(openWallpaperPreview = true)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            // Handle navigation events (like opening the live wallpaper picker)
            LaunchedEffect(Unit) {
                viewModel.navigationEvent.collect { event ->
                    when (event) {
                        is NavigationEvent.OpenWallpaperPicker -> {
                            val intent =
                                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                    putExtra(
                                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                        ComponentName(
                                            this@MainActivity,
                                            AtmosLiveWallpaperService::class.java
                                        )
                                    )
                                }
                            startActivity(intent)
                        }
                    }
                }
            }

            if (uiState.showLocationPicker) {
                LocationPickerDialog(
                    onDismiss = { viewModel.dismissLocationPicker() },
                    onSearch = { viewModel.searchLocation(it) },
                    searchResults = uiState.locationSearchResults,
                    onLocationSelected = { viewModel.selectManualLocation(it) },
                    isLoading = uiState.isSearchingLocation
                )
            }

            MainApp(
                uiState = uiState,
                selectImage = { viewModel.selectImage(it) },
                setCalendarEnabled = { viewModel.setCalendarEnabled(it) },
                toggleUseLocation = { viewModel.toggleUseLocation() },
                toggleTemperatureUnit = { viewModel.toggleTemperatureUnit() },
                triggerUpdate = { viewModel.triggerLiveWallpaper() },
                updateRefreshPeriod = { viewModel.updateRefreshPeriod(it) },
                setDebugTemperature = { viewModel.setDebugTemperature(it) },
                setDebugWeatherCode = { viewModel.setDebugWeatherCode(it) },
                setDynamicWallpaperEnabled = { viewModel.setDynamicWallpaperEnabled(it) },
                setShowLocation = { viewModel.setShowLocation(it) },
                setShowTemperature = { viewModel.setShowTemperature(it) },
                setShowForecast = { viewModel.setShowForecast(it) },
                onToggleTemperatureUnit = { viewModel.toggleTemperatureUnit() },
                onToggleShowSunTransitions = { viewModel.toggleShowSunTransitions() },
                onChooseManualLocation = { viewModel.showManualLocationPicker() },
                isTV = isTV
            )
        }
    }
}
