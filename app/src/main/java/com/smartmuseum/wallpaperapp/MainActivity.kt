package com.smartmuseum.wallpaperapp

import android.Manifest
import android.app.UiModeManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.smartmuseum.wallpaperapp.ui.MainViewModel
import com.smartmuseum.wallpaperapp.ui.components.AtmosDashboard
import com.smartmuseum.wallpaperapp.ui.theme.WallpaperAppTheme
import com.smartmuseum.wallpaperapp.worker.WallpaperWorker
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

fun isAndroidTV(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
}


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var isTV: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTV = isAndroidTV(this)
        enableEdgeToEdge()

        if (isTV) {
            triggerUpdate(immediate = true)
        }
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            var debugWeatherCode by remember { mutableStateOf<Int?>(null) }
            var debugTemp by remember { mutableStateOf(20.0) }

            val locationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->

            }

            LaunchedEffect(Unit) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

            WallpaperAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier
                        .padding(innerPadding)
                        .focusable()) {
                        AtmosDashboard(
                            atmosImage = uiState.atmosImage,
                            currentWallpaper = uiState.currentWallpaper,
                            isCelsius = uiState.isCelsius,
                            onToggleUnit = { viewModel.toggleTemperatureUnit() },
                            forceWeatherCode = debugWeatherCode,
                            forceTemp = if (debugWeatherCode != null) debugTemp else null
                        )
                        if (!isTV) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Top
                            ) {
                                var runImmediately by remember { mutableStateOf(false) }

                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = runImmediately,
                                        onCheckedChange = { runImmediately = it },
                                        colors = CheckboxDefaults.colors(checkmarkColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = stringResource(R.string.run_immediately),
                                        color = androidx.compose.ui.graphics.Color.White,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                Button(
                                    onClick = {
                                        triggerUpdate(immediate = runImmediately)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.setup_dynamic_wallpaper))
                                }

                                if (BuildConfig.DEBUG) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(stringResource(R.string.debug_image_provider), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall)
                                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("Unsplash", "Pexels", "Pixabay", "SourceSplash", "NASA").forEach { provider ->
                                            FilterChip(
                                                selected = uiState.preferredProvider == provider,
                                                onClick = { viewModel.setImageProvider(provider) },
                                                label = { Text(provider) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    labelColor = androidx.compose.ui.graphics.Color.White,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Debug Weather Overlay:", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall)
                                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Button(onClick = { debugWeatherCode = null }, contentPadding = PaddingValues(4.dp)) { Text("Auto") }
                                        Button(onClick = { debugWeatherCode = 61 }, contentPadding = PaddingValues(4.dp)) { Text("L. Rain") }
                                        Button(onClick = { debugWeatherCode = 65 }, contentPadding = PaddingValues(4.dp)) { Text("H. Rain") }
                                        Button(onClick = { debugWeatherCode = 71 }, contentPadding = PaddingValues(4.dp)) { Text("L. Snow") }
                                        Button(onClick = { debugWeatherCode = 75 }, contentPadding = PaddingValues(4.dp)) { Text("H. Snow") }
                                        Button(onClick = { debugWeatherCode = 45 }, contentPadding = PaddingValues(4.dp)) { Text("Fog") }
                                        Button(onClick = { debugWeatherCode = 95 }, contentPadding = PaddingValues(4.dp)) { Text("Storm") }
                                        Button(
                                            onClick = { debugTemp = if (debugTemp > 0) -20.0 else 0.0 }, 
                                            contentPadding = PaddingValues(4.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        ) { 
                                            Text("Temp: ${debugTemp.toInt()}°C") 
                                        }
                                    }
                                }

                                if (uiState.isLoading) {
                                    LinearProgressIndicator(
                                        progress = {
                                            uiState.loadingProgress
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        color = ProgressIndicatorDefaults.linearColor,
                                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                                    )
                                    Text(
                                        text = uiState.loadingMessage,
                                        color = androidx.compose.ui.graphics.Color.White,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun triggerUpdate(immediate: Boolean) {
        val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(2, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
            AtmosApplication.WORK_MANAGER,
            if (immediate) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
