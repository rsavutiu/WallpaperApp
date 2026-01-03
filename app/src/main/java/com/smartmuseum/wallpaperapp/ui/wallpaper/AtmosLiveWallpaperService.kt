package com.smartmuseum.wallpaperapp.ui.wallpaper

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class AtmosLiveWallpaperService : WallpaperService() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreateEngine(): Engine {
        return AtmosEngine()
    }

    inner class AtmosEngine : Engine(),
        androidx.lifecycle.LifecycleOwner,
        ViewModelStoreOwner,
        SavedStateRegistryOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        override val viewModelStore = ViewModelStore()
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry =
            savedStateRegistryController.savedStateRegistry

        private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)
        private var composeView: ComposeView? = null
        private var recomposer: Recomposer? = null
        private val frameClock = BroadcastFrameClock()

        private val atmosImageFlow = MutableStateFlow<AtmosImage?>(null)
        private val bitmapFlow = MutableStateFlow<android.graphics.Bitmap?>(null)
        private val colorSchemeFlow = MutableStateFlow<ColorScheme?>(null)

        private val handler = Handler(Looper.getMainLooper())
        private val drawRunnable = object : Runnable {
            override fun run() {
                val now = System.nanoTime()
                frameClock.sendFrame(now)
                drawFrame()
                if (isVisible) {
                    handler.postDelayed(this, 16)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

            recomposer = Recomposer(scope.coroutineContext + frameClock)
            scope.launch {
                recomposer?.runRecomposeAndApplyChanges()
            }

            composeView = ComposeView(this@AtmosLiveWallpaperService).apply {
                setViewTreeLifecycleOwner(this@AtmosEngine)
                setViewTreeViewModelStoreOwner(this@AtmosEngine)
                setViewTreeSavedStateRegistryOwner(this@AtmosEngine)
                compositionContext = recomposer
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnLifecycleDestroyed(
                        lifecycle
                    )
                )

                setContent {
                    val atmosImage by atmosImageFlow.collectAsState()
                    val bitmap by bitmapFlow.collectAsState()
                    val colorScheme by colorSchemeFlow.collectAsState()
                    val isCelsius by userPreferencesRepository.isCelsius.collectAsState(initial = true)
                    val isCalendarEnabled by userPreferencesRepository.isCalendarEnabled.collectAsState(
                        initial = true
                    )

                    WallpaperAppTheme(customColorScheme = colorScheme) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        ) {
                            if (atmosImage == null && bitmap == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF0F172A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "ATMOS",
                                            fontSize = 28.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "Downloading latest scenery...",
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            } else {
                                AtmosDashboard(
                                    atmosImage = atmosImage,
                                    currentWallpaper = bitmap,
                                    isCelsius = isCelsius,
                                    isCalendarEnabled = isCalendarEnabled,
                                    onToggleUnit = null
                                )
                            }
                        }
                    }
                }
            }

            scope.launch {
                userPreferencesRepository.lastUpdateTimestamp.collect {
                    loadData()
                }
            }
            loadData()
        }

        private fun loadData() {
            scope.launch(Dispatchers.IO) {
                try {
                    val rawFile = File(filesDir, "atmos_raw.png")
                    val wallpaperFile = File(filesDir, "atmos_wallpaper.png")
                    val fileToLoad =
                        if (rawFile.exists()) rawFile else if (wallpaperFile.exists()) wallpaperFile else null

                    if (fileToLoad != null && fileToLoad.length() > 0) {
                        val loadedBitmap = BitmapFactory.decodeFile(fileToLoad.absolutePath)
                        if (loadedBitmap != null) {
                            bitmapFlow.value = loadedBitmap
                            val palette = Palette.from(loadedBitmap).generate()
                            colorSchemeFlow.value = createColorSchemeFromPalette(palette)
                        }
                    }

                    val metadataFile = File(filesDir, "atmos_metadata.json")
                    if (metadataFile.exists() && metadataFile.length() > 0) {
                        val json = metadataFile.readText()
                        if (json.isNotBlank()) {
                            val img = Gson().fromJson(json, AtmosImage::class.java)
                            atmosImageFlow.value = img
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AtmosEngine", "Error loading data", e)
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

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            if (lifecycleRegistry.currentState == Lifecycle.State.CREATED) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder?,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            updateComposeLayout(width, height)
            drawFrame()
        }

        private fun updateComposeLayout(width: Int, height: Int) {
            if (width > 0 && height > 0) {
                composeView?.measure(
                    Constraints.fixed(width, height).minWidth,
                    Constraints.fixed(width, height).minHeight
                )
                composeView?.layout(0, 0, width, height)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                handler.post(drawRunnable)
            } else {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                handler.removeCallbacks(drawRunnable)
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            val canvas = holder.lockCanvas() ?: return
            try {
                // Background fill to verify the engine is alive and drawing
                canvas.drawColor(android.graphics.Color.BLACK)

                val width = canvas.width
                val height = canvas.height
                if (width > 0 && height > 0) {
                    updateComposeLayout(width, height)
                    composeView?.draw(canvas)
                }
            } catch (e: Exception) {
                Log.e("AtmosEngine", "Error drawing frame", e)
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            viewModelStore.clear()
            recomposer?.cancel()
            scope.cancel()
            handler.removeCallbacks(drawRunnable)
        }
    }
}
