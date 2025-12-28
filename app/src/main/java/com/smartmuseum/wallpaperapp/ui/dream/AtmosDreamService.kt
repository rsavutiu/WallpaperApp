package com.smartmuseum.wallpaperapp.ui.dream

import android.graphics.BitmapFactory
import android.service.dreams.DreamService
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
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

                LaunchedEffect(Unit) {
                    launch {
                        isCelsius = userPreferencesRepository.isCelsius.first()
                    }
                    launch {
                        try {
                            // Load the RAW image to avoid double text/icons in the screensaver
                            val rawFile = File(filesDir, "atmos_raw.png")
                            val wallpaperFile = File(filesDir, "atmos_wallpaper.png")
                            
                            val fileToLoad = if (rawFile.exists()) rawFile else if (wallpaperFile.exists()) wallpaperFile else null
                            
                            fileToLoad?.let {
                                bitmap = BitmapFactory.decodeFile(it.absolutePath)
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

                WallpaperAppTheme {
                    AtmosDashboard(
                        atmosImage = atmosImage,
                        currentWallpaper = bitmap,
                        isCelsius = isCelsius,
                        onToggleUnit = null // Non-interactive screensaver
                    )
                }
            }
        }
        
        setContentView(composeView)
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
