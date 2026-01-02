package com.smartmuseum.wallpaperapp

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.smartmuseum.wallpaperapp.ui.MainApp
import com.smartmuseum.wallpaperapp.ui.MainViewModel
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
            viewModel.triggerUpdate(immediate = true)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            MainApp(
                uiState = uiState,
                selectImage = { viewModel.selectImage(it) },
                setCalendarEnabled = { viewModel.setCalendarEnabled(it) },
                toggleUseLocation = { viewModel.toggleUseLocation() },
                toggleTemperatureUnit = { viewModel.toggleTemperatureUnit() },
                triggerUpdate = { viewModel.triggerUpdate(it) },
                updateRefreshPeriod = { viewModel.updateRefreshPeriod(it) },
                isTV = isTV
            )
        }
    }
}
