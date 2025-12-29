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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.smartmuseum.wallpaperapp.ui.MainUiState
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
            var selectedTab by remember { mutableIntStateOf(0) }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions[Manifest.permission.READ_CALENDAR] == true) {
                    viewModel.setCalendarEnabled(true)
                }
            }

            LaunchedEffect(Unit) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.READ_CALENDAR
                    )
                )
            }

            WallpaperAppTheme(customColorScheme = uiState.customColorScheme) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (!isTV) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface.copy(
                                    alpha = 0.8f
                                )
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = null
                                        )
                                    },
                                    label = { Text(stringResource(R.string.tab_setup)) }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = {
                                        Icon(
                                            Icons.Default.CalendarMonth,
                                            contentDescription = null
                                        )
                                    },
                                    label = { Text(stringResource(R.string.tab_calendar)) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .focusable()
                    ) {

                        AtmosDashboard(
                            atmosImage = uiState.atmosImage,
                            currentWallpaper = uiState.currentWallpaper,
                            isCelsius = uiState.isCelsius,
                            isCalendarEnabled = uiState.isCalendarEnabled,
                            onToggleUnit = { viewModel.toggleTemperatureUnit() },
                            forceWeatherCode = debugWeatherCode,
                            forceTemp = if (debugWeatherCode != null) debugTemp else null
                        )

                        if (!isTV) {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                                if (selectedTab == 0) {
                                    SetupTab(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        debugWeatherCode = debugWeatherCode,
                                        onDebugWeatherChange = { debugWeatherCode = it },
                                        debugTemp = debugTemp,
                                        onDebugTempChange = { debugTemp = it },
                                        onTriggerUpdate = { immediate -> triggerUpdate(immediate) }
                                    )
                                } else {
                                    CalendarTab(
                                        isEnabled = uiState.isCalendarEnabled,
                                        onToggle = { enabled ->
                                            if (enabled) {
                                                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR))
                                            } else {
                                                viewModel.setCalendarEnabled(false)
                                            }
                                        }
                                    )
                                }

                                // Progress Indicator at the bottom of the content area
                                if (uiState.isLoading) {
                                    Column(
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                            .padding(bottom = 16.dp)
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { uiState.loadingProgress },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = Color.White.copy(alpha = 0.3f)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = uiState.loadingMessage,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        )
                                    }
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

@Composable
fun SetupTab(
    uiState: MainUiState,
    viewModel: MainViewModel,
    debugWeatherCode: Int?,
    onDebugWeatherChange: (Int?) -> Unit,
    debugTemp: Double,
    onDebugTempChange: (Double) -> Unit,
    onTriggerUpdate: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
        var runImmediately by remember { mutableStateOf(false) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = runImmediately,
                onCheckedChange = { runImmediately = it },
                colors = CheckboxDefaults.colors(checkmarkColor = MaterialTheme.colorScheme.primary)
            )
            Text(
                text = stringResource(R.string.run_immediately),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = uiState.useLocation,
                onCheckedChange = { viewModel.toggleUseLocation() },
                colors = CheckboxDefaults.colors(checkmarkColor = MaterialTheme.colorScheme.primary)
            )
            Text(
                text = stringResource(R.string.use_location_wallpapers),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }

        Button(
            onClick = { onTriggerUpdate(runImmediately) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.setup_dynamic_wallpaper))
        }

        if (BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.debug_image_provider),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "Unsplash",
                    "Pexels",
                    "Pixabay",
                    "SourceSplash",
                    "NASA"
                ).forEach { provider ->
                    FilterChip(
                        selected = uiState.preferredProvider == provider,
                        onClick = { viewModel.setImageProvider(provider) },
                        label = { Text(provider) },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = Color.White,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedContainerColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Debug Weather Overlay:",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { onDebugWeatherChange(null) },
                    contentPadding = PaddingValues(4.dp)
                ) { Text("Auto") }
                Button(
                    onClick = { onDebugWeatherChange(0) },
                    contentPadding = PaddingValues(4.dp)
                ) { Text("Clear") }
                Button(
                    onClick = { onDebugWeatherChange(61) },
                    contentPadding = PaddingValues(4.dp)
                ) { Text("L. Rain") }
                Button(
                    onClick = { onDebugWeatherChange(65) },
                    contentPadding = PaddingValues(4.dp)
                ) { Text("H. Rain") }
                Button(
                    onClick = { onDebugWeatherChange(71) },
                    contentPadding = PaddingValues(4.dp)
                ) { Text("L. Snow") }
                Button(
                    onClick = { onDebugWeatherChange(75) },
                    contentPadding = PaddingValues(4.dp)
                ) { Text("H. Snow") }
                Button(
                    onClick = { onDebugWeatherChange(45) },
                    contentPadding = PaddingValues(4.dp)
                ) { Text("Fog") }
                Button(
                    onClick = { onDebugWeatherChange(95) },
                    contentPadding = PaddingValues(4.dp)
                ) { Text("Storm") }
                Button(
                    onClick = { onDebugTempChange(if (debugTemp > 0) -20.0 else 20.0) },
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text("Temp: ${debugTemp.toInt()}°C")
                }
            }
        }
    }
}

@Composable
fun CalendarTab(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
        Text(
            text = stringResource(R.string.tab_calendar),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = isEnabled, onCheckedChange = onToggle)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = stringResource(R.string.enable_calendar), color = Color.White)
        }
        if (!isEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.calendar_permission_required),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
