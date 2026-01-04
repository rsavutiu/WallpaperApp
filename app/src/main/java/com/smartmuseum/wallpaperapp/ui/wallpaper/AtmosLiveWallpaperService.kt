package com.smartmuseum.wallpaperapp.ui.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
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
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.gson.Gson
import com.smartmuseum.wallpaperapp.AtmosApplication
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.domain.model.HourlyForecast
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        float n = fbm(p + fbm(p * 1.1) * 0.25);
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
        private val shaderPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            isAntiAlias = true
            isFilterBitmap = true
        }
        private var startTime = System.currentTimeMillis()
        private var frameCounter = 0L
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
                        Log.i(TAG, "Refresh period was $refreshPeriod to $it")
                        refreshPeriod = it
                    }
                    userPreferencesRepository.lastUpdateTimestamp.collectLatest {
                        lastUpdateTimestamp = it
                        loadData()
                    }
                }
                loadData()
            }
        }

        private fun loadData() {
            scope.launch(Dispatchers.IO) {
                try {
                    val rawFile = File(filesDir, "atmos_raw.png")
                    val metadataFile = File(filesDir, "atmos_metadata.json")

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
                startTime = System.currentTimeMillis()
                handler.post(drawRunnable)
            } else {
                sensorManager.unregisterListener(this)
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
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
                if (System.currentTimeMillis() - lastUpdateTimestamp > refreshPeriod * 1000 * 60) {
                    Log.i(TAG, "Enqueue work. Last update: $lastUpdateTimestamp")
                    Log.i(TAG, "Enqueue work. Elapsed realtime: ${System.currentTimeMillis()}")
                    lastUpdateTimestamp = System.currentTimeMillis()
                    val oneTimeRequest = OneTimeWorkRequestBuilder<WallpaperWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
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
                val width = canvas.width.toFloat()
                val height = canvas.height.toFloat()
                frameCounter++

                // 1. Draw Background with both Swipe and Gyro Parallax
                backgroundBitmap?.let { bmp ->
                    val src = calculateCenterCropRect(
                        bmp.width,
                        bmp.height,
                        canvas.width,
                        canvas.height,
                        currentXOffset
                    )
                    // Apply gyro offset to the destination rectangle for depth
                    val gyroScale = 2.5f
                    val dst = RectF(
                        -60f + gyroOffsetX * gyroScale,
                        -60f + gyroOffsetY * gyroScale,
                        width + 60f + gyroOffsetX * gyroScale,
                        height + 60f + gyroOffsetY * gyroScale
                    )
                    canvas.drawBitmap(bmp, src, dst.toRect(), null)
                } ?: canvas.drawColor(Color.parseColor("#0F172A"))

                // 2. AGSL Weather Effects
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    weatherShader?.let { shader ->
                        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                        shader.setFloatUniform("iResolution", width, height)
                        shader.setFloatUniform("iTime", elapsed * 4.0f)
                        shader.setFloatUniform("iOffset", gyroOffsetX, gyroOffsetY)
                        shaderPaint.shader = shader
                        canvas.drawRect(0f, 0f, width, height, shaderPaint)
                    }
                }

                // 3. Scrim
                val scrimPaint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height * 0.4f,
                        intArrayOf(
                            Color.argb(220, 0, 0, 0),
                            Color.argb(120, 0, 0, 0),
                            Color.TRANSPARENT
                        ),
                        null, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width, height, scrimPaint)

                // 4. Content
                drawDashboardContent(canvas, width, height)

            } catch (e: Exception) {
                Log.e(TAG, "RENDER ERROR", e)
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {
                }
            }
        }

        private fun drawDashboardContent(canvas: Canvas, width: Float, height: Float) {
            val margin = width * 0.08f
            var currentY = height * 0.08f

            val textPaint = Paint().apply {
                color = Color.WHITE
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(15f, 0f, 0f, Color.BLACK)
            }

            // Swipe and Gyro shift for UI
            val contentParallaxShift =
                ((currentXOffset - 0.5f) * (width * 0.05f)) - (gyroOffsetX * 2.0f)
            val shiftedMargin = margin - contentParallaxShift

            atmosImage?.locationName?.let { loc ->
                textPaint.textSize = height * 0.025f
                textPaint.color = accentColor
                canvas.drawText(loc.uppercase(), shiftedMargin, currentY, textPaint)
                currentY += height * 0.035f
            }

            atmosImage?.weather?.let { weather ->
                val temp = forcedTemperature ?: weather.currentTemp
                val displayWeatherCode = forcedWeatherCode ?: weather.weatherCode
                val tempValue = if (isCelsius) temp else (temp * 9 / 5) + 32
                val unit = if (isCelsius) "°C" else "°F"
                val tempText = "${tempValue.toInt()}$unit"

                textPaint.textSize = height * 0.08f
                textPaint.color = Color.WHITE
                canvas.drawText(
                    tempText,
                    shiftedMargin + (height * 0.06f),
                    currentY + (height * 0.06f),
                    textPaint
                )

                val iconRes = getWeatherIconRes(displayWeatherCode, weather.isDay)
                drawTintedIcon(
                    canvas,
                    iconRes,
                    shiftedMargin,
                    currentY,
                    height * 0.05f,
                    Color.WHITE
                )

                currentY += height * 0.09f

                textPaint.textSize = height * 0.03f
                textPaint.typeface = Typeface.DEFAULT
                canvas.drawText(weather.condition, shiftedMargin, currentY, textPaint)
                currentY += height * 0.025f

                textPaint.textSize = height * 0.018f
                textPaint.color = Color.LTGRAY
                val stats = "Humidity: ${weather.humidity}% | Precip: ${
                    String.format(
                        "%.1f",
                        weather.precipitation
                    )
                }mm"
                canvas.drawText(stats, shiftedMargin, currentY, textPaint)
                currentY += height * 0.05f

                val forecastParallaxShift =
                    ((currentXOffset - 0.5f) * (width * 0.3f)) - (gyroOffsetX * 0.5f)
                drawHourlyStrip(
                    canvas,
                    weather.hourlyForecast,
                    shiftedMargin - forecastParallaxShift,
                    currentY,
                    width - margin * 2,
                    height
                )
                currentY += height * 0.08f
            }

            if (isCalendarEnabled) {
                atmosImage?.calendarEvents?.let { events ->
                    if (events.isNotEmpty()) {
                        textPaint.textSize = height * 0.022f
                        textPaint.color = accentColor
                        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText("TODAY'S SCHEDULE", shiftedMargin, currentY, textPaint)
                        currentY += height * 0.03f

                        textPaint.typeface = Typeface.DEFAULT
                        textPaint.color = Color.WHITE
                        textPaint.textSize = height * 0.018f
                        events.take(3).forEach { event ->
                            val time = SimpleDateFormat(
                                "HH:mm",
                                Locale.getDefault()
                            ).format(Date(event.startTime))
                            val title =
                                if (event.title.length > 30) event.title.take(27) + "..." else event.title
                            canvas.drawText("$time - $title", shiftedMargin, currentY, textPaint)
                            currentY += height * 0.022f
                        }
                    }
                }
            }

            // Attribution and Debug Info
            atmosImage?.let { img ->
                textPaint.textSize = height * 0.015f
                textPaint.color = Color.GRAY
                textPaint.textAlign = Paint.Align.CENTER

                //attribution at bottom
                canvas.drawText(
                    img.attribution,
                    width / 2f - contentParallaxShift,
                    height - 80f,
                    textPaint
                )

                // DEBUG ONLY: Show last refresh time
                if (com.smartmuseum.wallpaperapp.BuildConfig.DEBUG) {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val timeStr = "Last Refresh: ${sdf.format(Date(lastUpdateTimestamp))}"
                    textPaint.color = Color.RED
                    canvas.drawText(
                        timeStr,
                        width / 2f - contentParallaxShift,
                        height - 200f,
                        textPaint
                    )
                }
            }
        }

        private fun drawHourlyStrip(
            canvas: Canvas,
            forecasts: List<HourlyForecast>,
            x: Float,
            y: Float,
            availableWidth: Float,
            screenHeight: Float
        ) {
            if (forecasts.isEmpty()) return
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = screenHeight * 0.014f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val data = forecasts.take(6)
            val step = availableWidth / (data.size - 1).coerceAtLeast(1)
            data.forEachIndexed { index, forecast ->
                val curX = x + index * step
                val time = forecast.time.takeLast(5)
                canvas.drawText(time, curX, y, paint)
                val iconRes = getWeatherIconRes(forecast.weatherCode, true)
                drawTintedIcon(
                    canvas,
                    iconRes,
                    curX - (screenHeight * 0.012f),
                    y + 10f,
                    screenHeight * 0.025f,
                    Color.WHITE
                )
                val temp = if (isCelsius) forecast.temp else (forecast.temp * 9 / 5) + 32
                canvas.drawText("${temp.toInt()}°", curX, y + (screenHeight * 0.05f), paint)
            }
        }

        private fun drawTintedIcon(
            canvas: Canvas,
            resId: Int,
            x: Float,
            y: Float,
            size: Float,
            color: Int
        ) {
            val drawable =
                ContextCompat.getDrawable(this@AtmosLiveWallpaperService, resId) ?: return
            drawable.mutate().colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            drawable.setBounds(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt())
            drawable.draw(canvas)
        }

        private fun calculateCenterCropRect(
            srcW: Int,
            srcH: Int,
            dstW: Int,
            dstH: Int,
            xOffset: Float = 0.5f
        ): Rect {
            if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return Rect(0, 0, 0, 0)
            val srcAspect = srcW.toFloat() / srcH
            val dstAspect = dstW.toFloat() / dstH

            return if (srcAspect > dstAspect) {
                // Image is wider than screen. We use xOffset to scroll the source window.
                val visibleWidthInSrc = (srcH * dstAspect).toInt()
                val totalScrollableWidth = srcW - visibleWidthInSrc
                val left = (totalScrollableWidth * xOffset).toInt()
                Rect(left, 0, left + visibleWidthInSrc, srcH)
            } else {
                // Image is taller than screen.
                val visibleHeightInSrc = (srcW / dstAspect).toInt()
                val topOffset = (srcH - visibleHeightInSrc) / 2
                Rect(0, topOffset, srcW, topOffset + visibleHeightInSrc)
            }
        }

        private fun getWeatherIconRes(code: Int, isDay: Boolean): Int {
            return when (code) {
                0 -> if (isDay) R.drawable.ic_sunny else R.drawable.ic_clear_night
                1, 2, 3 -> if (isDay) R.drawable.outline_partly_cloudy_day_24 else R.drawable.outline_partly_cloudy_night_24
                45, 48 -> R.drawable.outline_foggy_24
                51, 53, 55 -> R.drawable.outline_rainy_light_24
                56, 57 -> R.drawable.outline_weather_mix_24
                61, 63 -> R.drawable.outline_rainy_24
                65 -> R.drawable.outline_rainy_heavy_24
                66, 67 -> R.drawable.outline_rainy_snow_24
                71, 73 -> R.drawable.outline_weather_snowy_24
                75 -> R.drawable.outline_snowing_heavy_24
                77 -> R.drawable.outline_snowing_24
                80, 81 -> R.drawable.outline_rainy_24
                82 -> R.drawable.outline_rainy_heavy_24
                85 -> if (isDay) R.drawable.outline_sunny_snowing_24 else R.drawable.outline_snowing_24
                86 -> R.drawable.outline_snowing_heavy_24
                95 -> R.drawable.outline_thunderstorm_24
                96, 99 -> R.drawable.outline_weather_hail_24
                else -> if (isDay) R.drawable.outline_partly_cloudy_day_24 else R.drawable.outline_partly_cloudy_night_24
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            scope.cancel()
            handler.removeCallbacks(drawRunnable)
        }
    }
}

// Extension to safely convert RectF to Rect
private fun RectF.toRect(): Rect = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
