package com.smartmuseum.wallpaperapp.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.ui.components.AtmosDashboard
import com.smartmuseum.wallpaperapp.ui.screens.SetupScreen
import com.smartmuseum.wallpaperapp.ui.screens.WallpaperScreen
import com.smartmuseum.wallpaperapp.ui.theme.WallpaperAppTheme

@Composable
fun MainApp(
    uiState: MainUiState,
    selectImage: (AtmosImage) -> Unit,
    setCalendarEnabled: (Boolean) -> Unit,
    toggleUseLocation: () -> Unit,
    toggleTemperatureUnit: () -> Unit,
    updateRefreshPeriod: (Long) -> Unit,
    triggerUpdate: (Boolean) -> Unit,
    setDebugWeatherCode: (Int?) -> Unit,
    setDebugTemperature: (Double?) -> Unit,
    setDynamicWallpaperEnabled: (Boolean) -> Unit,
    isTV: Boolean = false
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_CALENDAR] == true) {
            setCalendarEnabled(true)
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        // Big Run Button placed globally above the navigation bar
                        Button(
                            onClick = { triggerUpdate(true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                                    alpha = 0.9f
                                ),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.run),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        NavigationBar(
                            containerColor = Color.Transparent, // Transparent to use Column's background
                            tonalElevation = 0.dp
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(R.string.tab_wallpaper)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(R.string.tab_setup)) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .focusable()
            ) {

                // Background with current wallpaper
                AtmosDashboard(
                    atmosImage = uiState.atmosImage,
                    currentWallpaper = uiState.currentWallpaper,
                    isCelsius = uiState.isCelsius,
                    isCalendarEnabled = uiState.isCalendarEnabled,
                    onToggleUnit = { toggleTemperatureUnit() },
                    forceWeatherCode = uiState.debugWeatherCode,
                    forceTemp = if (uiState.debugWeatherCode != null) uiState.debugTemperature else null
                )

                if (!isTV) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                WallpaperScreen(
                                    uiState = uiState,
                                    onImageSelected = { image ->
                                        selectImage(image)
                                    }
                                )
                            }

                            1 -> {
                                SetupScreen(
                                    uiState = uiState,
                                    toggleUseLocation = toggleUseLocation,
                                    toggleTemperatureUnit = toggleTemperatureUnit,
                                    setCalendarEnabled = { enabled ->
                                        if (enabled) {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR))
                                        } else {
                                            setCalendarEnabled(false)
                                        }
                                    },
                                    updateRefreshPeriod = { minutes ->
                                        updateRefreshPeriod(minutes)
                                    },
                                    onRunUpdate = { triggerUpdate(true) },
                                    onDebugWeatherChange = { setDebugWeatherCode(it) },
                                    debugWeatherCode = uiState.debugWeatherCode,
                                    debugTemp = uiState.debugTemperature,
                                    onDebugTempChange = { setDebugTemperature(it) },
                                    setDynamicWallpaperEnabled = { setDynamicWallpaperEnabled(it) }
                                )
                            }
                        }

                        // Progress Indicator at the bottom
                        if (uiState.isLoading) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
                            ) {
                                val isPhasing =
                                    uiState.loadingProgress % 0.33f == 0f && uiState.loadingProgress < 1.0f

                                if (isPhasing) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        progress = { uiState.loadingProgress },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                }

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
