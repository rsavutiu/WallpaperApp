package com.smartmuseum.wallpaperapp.ui.dream

import android.graphics.BitmapFactory
import android.service.dreams.DreamService
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.palette.graphics.Palette
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.gson.Gson
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import com.smartmuseum.wallpaperapp.ui.components.AtmosDashboard
import com.smartmuseum.wallpaperapp.ui.theme.WallpaperAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class AtmosDreamService : DreamService(), SavedStateRegistryOwner {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AtmosDreamService)
            setViewTreeSavedStateRegistryOwner(this@AtmosDreamService)
            
            setContent {
                var atmosImage by remember { mutableStateOf<AtmosImage?>(null) }
                var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                var isCelsius by remember { mutableStateOf(true) }
                var isCalendarEnabled by remember { mutableStateOf(true) }
                var customColorScheme by remember { mutableStateOf<ColorScheme?>(null) }

                LaunchedEffect(Unit) {
                    launch {
                        isCelsius = userPreferencesRepository.isCelsius.first()
                        isCalendarEnabled = userPreferencesRepository.isCalendarEnabled.first()
                    }
                    launch {
                        try {
                            // Load the RAW image to avoid double text/icons in the screensaver
                            val rawFile = File(filesDir, "atmos_raw.png")
                            val wallpaperFile = File(filesDir, "atmos_wallpaper.png")
                            
                            val fileToLoad = if (rawFile.exists()) rawFile else if (wallpaperFile.exists()) wallpaperFile else null
                            
                            fileToLoad?.let {
                                val loadedBitmap = BitmapFactory.decodeFile(it.absolutePath)
                                if (loadedBitmap != null) {
                                    bitmap = loadedBitmap
                                    // Extract Palette for Dynamic Colors in Screensaver
                                    val palette = Palette.from(loadedBitmap).generate()
                                    customColorScheme = createColorSchemeFromPalette(palette)
                                }
                            }

                            val metadataFile = File(filesDir, "atmos_metadata.json")
                            if (metadataFile.exists()) {
                                val json = metadataFile.readText()
                                atmosImage = Gson().fromJson(json, AtmosImage::class.java)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                WallpaperAppTheme(customColorScheme = customColorScheme) {
                    AtmosDashboard(
                        atmosImage = atmosImage,
                        currentWallpaper = bitmap,
                        isCelsius = isCelsius,
                        isCalendarEnabled = isCalendarEnabled,
                        onToggleUnit = null // Non-interactive screensaver
                    )
                }
            }
        }
        
        setContentView(composeView)
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

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
