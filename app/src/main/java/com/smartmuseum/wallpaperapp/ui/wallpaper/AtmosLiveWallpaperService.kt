package com.smartmuseum.wallpaperapp.ui.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.palette.graphics.Palette
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.Gson
import com.smartmuseum.wallpaperapp.AtmosApplication
import com.smartmuseum.wallpaperapp.AtmosApplication.Companion.METADATA_FILE
import com.smartmuseum.wallpaperapp.AtmosApplication.Companion.RAW_IMAGE_FILE
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.repository.UserPreferencesRepository
import com.smartmuseum.wallpaperapp.worker.WallpaperWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

private const val TAG = "AtmosLiveWallpaper"

// AGSL Shaders - Fixed to use proper PREMULTIPLIED alpha for native Canvas blending
private const val WEATHER_HEADER = """
    uniform float2 iResolution;
    uniform float2 iOffset;
    uniform float iTime;
    uniform float iIntensity;
    uniform float iTemperature;

    float hash12(float2 p) {
        float3 p3  = fract(float3(p.xyx) * .1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    float hash11(float p) {
        p = fract(p * .1031);
        p *= p + 33.33;
        p *= p + p;
        return fract(p);
    }

    float noise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);
        return mix(mix(hash12(i + float2(0.0, 0.0)), 
                       hash12(i + float2(1.0, 0.0)), u.x),
                   mix(hash12(i + float2(0.0, 1.0)), 
                       hash12(i + float2(1.0, 1.0)), u.x), u.y);
    }

    float fbm(float2 p) {
        float v = 0.0;
        float a = 0.5;
        float2 shift = float2(100.0);
        for (int i = 0; i < 4; ++i) {
            v += a * noise(p);
            p = p * 2.0 + shift;
            a *= 0.5;
        }
        return v;
    }
    
    float useAll() {
        return (iResolution.x + iTime + iIntensity + iTemperature + iOffset.x) * 0.0000001;
    }
"""

private const val RAIN_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = (fragCoord + iOffset) / iResolution.xy;
        float acc = 0.0;
        for (int i = 0; i < 3; i++) {
            float layer = float(i);
            float2 p = uv * float2(25.0 + layer * 15.0, 20.0); 
            float slant = 0.1 + iIntensity * 0.3;
            p.x += uv.y * slant;
            float speed = (15.0 + layer * 10.0 + iIntensity * 20.0);
            p.y += iTime * speed;
            float2 id = floor(p);
            float f = fract(p);
            float h = hash12(id + layer * 123.4);
            float threshold = 1.0 - (0.0001 + pow(iIntensity, 3.0) * 0.1);
            if (h > threshold) {
                float jitter = hash11(id.y * 12.34 + layer);
                float xPos = 0.2 + jitter * 0.6;
                float drop = smoothstep(0.05, 0.0, abs(f.x - xPos)) * 
                             smoothstep(0.0, 0.1, f.y) * 
                             smoothstep(0.4, 0.2, f.y);
                acc += drop * (0.3 + layer * 0.2);
            }
        }
        float a = clamp(acc, 0.0, 1.0);
        return half4(0.7 * a, 0.85 * a, 1.0 * a, a);
    }
"""

private const val SNOW_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = (fragCoord + iOffset) / iResolution.xy;
        float acc = 0.0;
        float tempFactor = clamp((iTemperature + 15.0) / 15.0, 0.0, 1.0); 
        float sizeBase = mix(0.03, 0.06, tempFactor);
        float blurBase = mix(0.01, 0.03, tempFactor);
        for(int i=0; i<3; i++) {
            float layer = float(i);
            float scale = 7.0 + layer * 5.0;
            float2 p = uv * scale;
            float col_id = floor(p.x);
            float speed_rand = hash11(col_id + layer * 31.7);
            float speed = (0.15 + speed_rand * 0.2) * (0.6 + layer * 0.4);
            p.y -= iTime * speed;
            float2 id = floor(p);
            p.x += (noise(float2(iTime * 0.1, id.x + layer * 10.0)) - 0.5) * 0.2;
            float2 f = fract(p);
            float2 h = float2(hash12(id + layer * 13.0), hash12(id + layer * 27.0));
            if (h.x > 0.85 - iIntensity * 0.1) {
                float2 pos = 0.07 + h * 0.8;
                float d = length(f - pos);
                float size = sizeBase * (0.6 + h.y * 0.5);
                acc += smoothstep(size, size - blurBase, d) * (0.5 + h.x * 0.5);
            }
        }
        float a = clamp(acc * (0.7 + iIntensity * 0.3), 0.0, 1.0);
        return half4(a, a, a, a);
    }
"""

private const val FOG_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = (fragCoord + iOffset * 0.5) / iResolution.xy;
        float2 p = uv * float2(1.0, 1.8);
        float t = iTime * 0.08;
        p.x += t;
        float n = fbm(p + fbm(p * 0.8 + t * 0.05));
        float mask = smoothstep(0.2, 0.9, uv.y);
        float a = clamp(n * (0.8 + iIntensity * 0.4) * mask, 0.0, 0.8);
        return half4(0.95 * a, 0.97 * a, 1.0 * a, a);
    }
"""

private const val THUNDER_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = (fragCoord + iOffset) / iResolution.xy;
        float hTime = iTime * 8.0;
        float flash = step(0.99, hash11(floor(hTime))) * hash11(hTime) * step(0.3, fract(hTime));
        float acc = 0.0;
        for (int i = 0; i < 2; i++) {
            float2 p = uv * float2(35.0 + float(i) * 15.0, 18.0);
            p.x += uv.y * 0.3;
            p.y += iTime * 35.0;
            float2 id = floor(p);
            float h = hash12(id);
            if (h > 0.95) {
                float jitter = hash11(id.y * 7.89);
                acc += smoothstep(0.06, 0.0, abs(fract(p.x) - jitter)) * 
                       smoothstep(0.0, 0.1, fract(p.y)) * 
                       smoothstep(0.6, 0.3, fract(p.y));
            }
        }
        float a = clamp(acc * 0.7, 0.0, 1.0);
        half4 storm = half4(0.7 * a, 0.8 * a, 1.0 * a, a);
        float f = flash * 0.6;
        return (storm + half4(f, f, f, f));
    }
"""

private const val CLOUD_DRIFT_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = (fragCoord + iOffset * 0.3) / iResolution.xy;
        float2 p = uv * float2(0.8, 1.2);
        p.x -= iTime * 0.015;
        float n = fbm(p + fbm(p * 1.0) * 0.25);
        float a = clamp(n * 0.5 * (1.0 - uv.y) * (0.6 + iIntensity * 0.4), 0.0, 1.0);
        return half4(a, a, a, a);
    }
"""

@AndroidEntryPoint
class AtmosLiveWallpaperService : WallpaperService() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreateEngine(): Engine {
        return AtmosEngine()
    }

    inner class AtmosEngine : Engine(), SensorEventListener {
        private var workManager = WorkManager.getInstance(this@AtmosLiveWallpaperService)

        private var lastUpdateTimestamp: Long = 0L
        private var refreshPeriod: Long = 0L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val handler = Handler(Looper.getMainLooper())

        private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
        private val rotationSensor by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }

        @Volatile
        private var atmosImage: AtmosImage? = null
        @Volatile
        private var backgroundBitmap: Bitmap? = null
        private var accentColor: Int = Color.WHITE

        private var isCelsius: Boolean = true
        private var isCalendarEnabled: Boolean = true

        private var forcedWeatherCode: Int? = null
        private var forcedTemperature: Double? = null

        private var weatherShader: RuntimeShader? = null
        private val wallpaperRenderer = WallpaperRenderer(this@AtmosLiveWallpaperService)
        private var currentXOffset: Float = 0.5f

        // Gyro/Tilt variables
        private var gyroOffsetX = 0f
        private var gyroOffsetY = 0f
        private var smoothedGyroX = 0f
        private var smoothedGyroY = 0f
        private val smoothingFactor = 0.1f
        private val deadZone = 0.01f

        private val drawRunnable = object : Runnable {
            override fun run() {
                if (isVisible) {
                    drawFrame()
                    handler.postDelayed(this, 66) // 15 FPS
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)

            scope.launch {
                isCelsius = userPreferencesRepository.isCelsius.first()
                isCalendarEnabled = userPreferencesRepository.isCalendarEnabled.first()

                launch {
                    userPreferencesRepository.isCelsius.collect {
                        isCelsius = it
                        drawFrame()
                    }
                }
                launch {
                    userPreferencesRepository.isCalendarEnabled.collect {
                        isCalendarEnabled = it
                        drawFrame()
                    }
                }
                launch {
                    userPreferencesRepository.forcedWeatherCode.collect {
                        forcedWeatherCode = it
                        updateWeatherShader()
                        drawFrame()
                    }
                }
                launch {
                    userPreferencesRepository.forcedTemperature.collect {
                        forcedTemperature = it
                        updateWeatherShader()
                        drawFrame()
                    }
                }
                launch {
                    userPreferencesRepository.refreshPeriodInMinutes.collectLatest {
                        refreshPeriod = it
                    }
                }
                // Observe WorkManager progress/completion
                launch {
                    workManager.getWorkInfosByTagFlow(AtmosApplication.WALLPAPER_UPDATE_TAG)
                        .collect { workInfos ->
                            val info = workInfos.firstOrNull()
                            if (info != null && info.state == WorkInfo.State.SUCCEEDED) {
                                loadData()
                            }
                        }
                }
                loadData()
            }
        }

        private fun loadData() {
            scope.launch(Dispatchers.IO) {
                try {
                    val rawFile = File(filesDir, RAW_IMAGE_FILE)
                    val metadataFile = File(filesDir, METADATA_FILE)

                    if (rawFile.exists() && rawFile.length() > 0) {
                        val loadedBitmap = BitmapFactory.decodeFile(rawFile.absolutePath)
                        if (loadedBitmap != null) {
                            backgroundBitmap = loadedBitmap
                            val palette = Palette.from(loadedBitmap).generate()
                            accentColor = palette.getLightVibrantColor(Color.WHITE)
                        }
                    }

                    if (metadataFile.exists() && metadataFile.length() > 0) {
                        val json = metadataFile.readText()
                        atmosImage = Gson().fromJson(json, AtmosImage::class.java)
                        updateWeatherShader()
                    }

                    withContext(Dispatchers.Main) {
                        drawFrame()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "DATA loadData ERROR", e)
                }
            }
        }

        private fun updateWeatherShader() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

            val code = forcedWeatherCode ?: atmosImage?.weather?.weatherCode ?: -1
            val shaderSource = when (code) {
                1, 2, 3 -> CLOUD_DRIFT_SHADER
                51, 53, 55, 61, 63, 65, 80, 81, 82 -> RAIN_SHADER
                71, 73, 75, 77, 85, 86 -> SNOW_SHADER
                45, 48 -> FOG_SHADER
                95, 96, 99 -> THUNDER_SHADER
                else -> null
            }

            if (shaderSource != null) {
                try {
                    val shader = RuntimeShader(shaderSource)
                    val intensity = when (code) {
                        51, 61, 80, 71, 77 -> 0.2f
                        53, 63, 81, 73, 85 -> 0.5f
                        55, 65, 82, 75, 86, 95, 96, 99 -> 1.0f
                        else -> ((atmosImage?.weather?.precipitation ?: 0.0).coerceIn(
                            0.0,
                            10.0
                        ) / 10.0).toFloat()
                    }
                    val temp = forcedTemperature ?: atmosImage?.weather?.currentTemp ?: 20.0
                    shader.setFloatUniform("iIntensity", intensity)
                    shader.setFloatUniform("iTemperature", temp.toFloat())
                    weatherShader = shader
                } catch (e: Exception) {
                    Log.e(TAG, "SHADER compile error", e)
                    weatherShader = null
                }
            } else {
                weatherShader = null
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(
                xOffset,
                yOffset,
                xOffsetStep,
                yOffsetStep,
                xPixelOffset,
                yPixelOffset
            )
            currentXOffset = xOffset
            drawFrame()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                rotationSensor?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                }
                handler.post(drawRunnable)
            } else {
                sensorManager.unregisterListener(this)
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                // event.values[1] is roll (left/right tilt)
                // event.values[0] is pitch (forward/back tilt)
                val rawX = event.values[0] * 120f
                val rawY = event.values[1] * 100f

                if (abs(rawX - smoothedGyroX) > deadZone * 50f) {
                    smoothedGyroX += (rawX - smoothedGyroX) * smoothingFactor
                }
                if (abs(rawY - smoothedGyroY) > deadZone * 50f) {
                    smoothedGyroY += (rawY - smoothedGyroY) * smoothingFactor
                }

                gyroOffsetX = smoothedGyroX
                gyroOffsetY = smoothedGyroY
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        private fun drawFrame() {
            scope.launch {
                val current = System.currentTimeMillis()
                if (current - lastUpdateTimestamp > refreshPeriod * 1000 * 60) {
                    lastUpdateTimestamp = current
                    val oneTimeRequest = OneTimeWorkRequestBuilder<WallpaperWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .addTag(AtmosApplication.WALLPAPER_UPDATE_TAG)
                        .build()

                    workManager.enqueueUniqueWork(
                        AtmosApplication.WORK_MANAGER + "_immediate",
                        ExistingWorkPolicy.REPLACE,
                        oneTimeRequest
                    )
                }
            }

            if (!isVisible) return
            val holder = surfaceHolder
            val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.lockHardwareCanvas()
            } else {
                holder.lockCanvas()
            } ?: return

            try {
                wallpaperRenderer.drawFrame(
                    canvas = canvas,
                    atmosImage = atmosImage,
                    backgroundBitmap = backgroundBitmap,
                    weatherShader = weatherShader,
                    currentXOffset = currentXOffset,
                    gyroOffsetX = gyroOffsetX,
                    gyroOffsetY = gyroOffsetY,
                    accentColor = accentColor,
                    isCelsius = isCelsius,
                    isCalendarEnabled = isCalendarEnabled,
                    forcedTemperature = forcedTemperature,
                    forcedWeatherCode = forcedWeatherCode,
                    lastUpdateTimestamp = lastUpdateTimestamp
                )
            } catch (e: Exception) {
                Log.e(TAG, "RENDER ERROR", e)
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {
                }
            }
        }






        override fun onDestroy() {
            super.onDestroy()
            scope.cancel()
            handler.removeCallbacks(drawRunnable)
        }
    }
}

