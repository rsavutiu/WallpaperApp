package com.smartmuseum.wallpaperapp.ui.wallpaper

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.google.gson.Gson
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import com.smartmuseum.wallpaperapp.ui.components.AtmosDashboard
import com.smartmuseum.wallpaperapp.ui.theme.WallpaperAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class AtmosWallpaperService : WallpaperService() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var app: Application

    override fun onCreateEngine(): Engine {
        return AtmosEngine()
    }

    inner class AtmosEngine : Engine(), LifecycleOwner, ViewModelStoreOwner,
        SavedStateRegistryOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        override val viewModelStore = ViewModelStore()
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry =
            savedStateRegistryController.savedStateRegistry

        private var composeView: ComposeView? = null
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        private val wallpaperData = MutableStateFlow<Pair<Bitmap?, AtmosImage?>>(null to null)

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            savedStateRegistryController.performRestore(null)

            loadData()

            // Observe updates from repository
            scope.launch {
                userPreferencesRepository.lastUpdateTimestamp.collect {
                    loadData()
                }
            }

            composeView = ComposeView(this@AtmosWallpaperService).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    val data by wallpaperData.collectAsState()
                    val isCelsius by userPreferencesRepository.isCelsius.collectAsState(initial = true)
                    val isCalendarEnabled by userPreferencesRepository.isCalendarEnabled.collectAsState(
                        initial = false
                    )

                    WallpaperAppTheme {
                        AtmosDashboard(
                            atmosImage = data.second,
                            currentWallpaper = data.first,
                            isCelsius = isCelsius,
                            isCalendarEnabled = isCalendarEnabled,
                            isWallpaperMode = true
                        )
                    }
                }
            }

            // Required for ComposeView to work outside of an Activity
            val view = composeView!!
            view.alpha = 1f

            // Start the render loop
            renderLoop()
        }

        private fun loadData() {
            scope.launch(Dispatchers.IO) {
                try {
                    val rawFile = File(application.filesDir, "atmos_raw.png")
                    val wallpaperFile = File(application.filesDir, "atmos_wallpaper.png")
                    val fileToLoad =
                        if (rawFile.exists()) rawFile else if (wallpaperFile.exists()) wallpaperFile else null

                    val bitmap = fileToLoad?.let { BitmapFactory.decodeFile(it.absolutePath) }

                    val metadataFile = File(application.filesDir, "atmos_metadata.json")
                    val atmosImage = if (metadataFile.exists()) {
                        Gson().fromJson(metadataFile.readText(), AtmosImage::class.java)
                    } else null

                    wallpaperData.value = bitmap to atmosImage
                } catch (_: Exception) {
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            } else {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
        }

        private fun renderLoop() {
            scope.launch {
                while (isActive) {
                    if (isVisible) {
                        draw()
                    }
                    delay(16) // ~60 FPS
                }
            }
        }

        private fun draw() {
            val holder = surfaceHolder
            val canvas = holder.lockCanvas() ?: return
            val view = composeView ?: return

            try {
                val width = canvas.width
                val height = canvas.height

                view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, width, height)

                view.draw(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            scope.cancel()
        }
    }
}
