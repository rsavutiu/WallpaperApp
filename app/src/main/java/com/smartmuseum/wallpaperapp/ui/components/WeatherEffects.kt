package com.smartmuseum.wallpaperapp.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ShaderBrush
import org.intellij.lang.annotations.Language

// WEATHER_HEADER: Common AGSL utility functions and uniform declarations shared by all weather shaders.
@Language("AGSL")
const val WEATHER_HEADER = """
    uniform float2 iResolution;      // Screen dimensions in pixels (width, height).
    uniform float iTime;            // Global time in seconds for continuous animations.
    uniform float iIntensity;       // Weather severity (0.0 to 1.0) for particle density and speed.
    uniform float iTemperature;     // Ambient temperature in Celsius for physically accurate effects.

    // hash12: Generates a pseudo-random float from a 2D coordinate (seed).
    // This allows each grid cell to have its own unique, deterministic random value.
    float hash12(float2 p) {
        float3 p3  = fract(float3(p.xyx) * .1031); // Scale and bit-shift via fractional part.
        p3 += dot(p3, p3.yzx + 33.33);             // Scramble bits using dot products.
        return fract((p3.x + p3.y) * p3.z);        // Return a normalized value between 0.0 and 1.0.
    }

    // hash11: Generates a 1D random float from a single input seed.
    float hash11(float p) {
        p = fract(p * .1031);                      // Initial scaling.
        p *= p + 33.33;                            // High-frequency scrambling.
        p *= p + p;                                // Additional non-linearity.
        return fract(p);                           // Return normalized random value.
    }

    // noise: Smoothly interpolated value noise on a 2D grid.
    // Creates organic transitions instead of hard-edged random blocks.
    float noise(float2 p) {
        float2 i = floor(p);                       // Integer cell coordinate.
        float2 f = fract(p);                       // Local coordinate within the cell.
        float2 u = f * f * (3.0 - 2.0 * f);        // Smooth Hermite interpolation curve (ease-in-out).
        // Linearly interpolate between the four corners of the grid cell.
        return mix(mix(hash12(i + float2(0.0, 0.0)), 
                       hash12(i + float2(1.0, 0.0)), u.x),
                   mix(hash12(i + float2(0.0, 1.0)), 
                       hash12(i + float2(1.0, 1.0)), u.x), u.y);
    }

    // fbm (Fractal Brownian Motion): Sums multiple octaves of noise at varying scales.
    // This technique is used to simulate complex, volumetric textures like clouds and mist.
    float fbm(float2 p) {
        float v = 0.0;                             // Accumulator for noise layers.
        float a = 0.5;                             // Starting amplitude (weight).
        float2 shift = float2(100.0);              // Offset applied between layers to hide repeats.
        for (int i = 0; i < 4; ++i) {              // Combine 4 layers of detail.
            v += a * noise(p);                     // Add current octave weighted by amplitude.
            p = p * 2.0 + shift;                   // Double frequency for the next octave.
            a *= 0.5;                              // Halve amplitude for the next octave.
        }
        return v;                                  // Resulting normalized density map.
    }
    
    // useAll: Essential function to prevent the AGSL compiler from optimizing out unused uniforms.
    // Calling setFloatUniform() in Kotlin on an optimized-out uniform triggers a native JNI crash.
    float useAll() {
        // Return a microscopic value that doesn't affect visual output but requires all uniforms.
        return (iResolution.x + iTime + iIntensity + iTemperature) * 0.0000001;
    }
"""

// RAIN_SHADER: High-performance particle engine for falling raindrops with depth parallax.
@Language("AGSL")
const val RAIN_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;    // Current pixel coordinate in 0.0 to 1.0 range.
        float acc = 0.0;                           // Accumulator for raindrop intensity.
        
        for (int i = 0; i < 3; i++) {              // Render 3 independent parallax layers.
            float layer = float(i);                // 0 = background, 2 = foreground.
            // Create a grid: Y-scale (25.0) is higher than X to ensure droplets are distinct segments.
            float2 p = uv * float2(30.0 + layer * 20.0, 25.0); 
            
            // Slant: Tilt the rain horizontally based on weather intensity (simulating wind).
            float slant = 0.1 + iIntensity * 0.3;
            p.x += uv.y * slant;                   // Apply cumulative shift as we go down the screen.
            
            // Velocity: Faster for nearer layers and higher weather intensities.
            float speed = (15.0 + layer * 10.0 + iIntensity * 20.0);
            p.y += iTime * speed;                  // Update grid position to move pattern DOWN.
            
            float2 id = floor(p);                  // Identify which cell we are in.
            float2 f = fract(p);                   // Local pixel position within the 0..1 cell space.
            float h = hash12(id + layer * 123.4);  // Unique random value for this specific cell.
            
            // Sparse Density: Use cubic intensity so light rain (low iIntensity) has almost no drops.
            float threshold = 1.0 - (0.0001 + pow(iIntensity, 3.0) * 0.1);
            if (h > threshold) {                   // Only draw a drop if cell passes the density check.
                // Jitter: Randomize the horizontal position of the drop inside its column.
                float jitter = hash11(id.y * 12.34 + layer);
                float xPos = 0.2 + jitter * 0.6;   // Keep drops within the center of the cell.
                
                // Drop Geometry: Sharp horizontal peak (abs(f.x)) with faded vertical edges (f.y).
                float drop = smoothstep(0.05, 0.0, abs(f.x - xPos)) * 
                             smoothstep(0.0, 0.1, f.y) * 
                             smoothstep(0.4, 0.2, f.y);
                
                acc += drop * (0.3 + layer * 0.2); // Closer layers are brighter.
            }
        }
        
        // Return soft-blue rain color with the calculated opacity.
        return half4(0.7, 0.85, 1.0, clamp(acc, 0.0, 1.0) + useAll());
    }
"""

// SNOW_SHADER: Atmospheric snowfall with temperature-sensitive flake size and natural drift.
@Language("AGSL")
const val SNOW_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;    // Screen UV coordinates.
        float acc = 0.0;                           // Alpha accumulator for snowflakes.
        
        // Temp mapping: 0.0 = -15C (powder), 1.0 = 0C (wet/heavy flakes).
        float tempFactor = clamp((iTemperature + 15.0) / 15.0, 0.0, 1.0); 
        float sizeBase = mix(0.03, 0.07, tempFactor); // Larger clusters when near freezing.
        float blurBase = mix(0.01, 0.04, tempFactor); // Softer edges for heavy clusters.
        
        for(int i=0; i<3; i++) {                   // 3 layers of parallax depth.
            float layer = float(i);                // Layer index.
            float scale = 7.0 + layer * 5.0;       // Grid density.
            float2 p = uv * scale;                 // Scale world to grid cells.

            float speed = (0.5 + layer * 0.3);     // Nearer snow falls faster.
            p.y += iTime * speed;                  // Pattern moves DOWN over time.
            
            float2 id = floor(p);                  // Discrete cell ID.
            
            // Natural Sway: Use value noise instead of sine waves for non-repeating horizontal flutter.
            p.x += (noise(float2(iTime * 0.2, id.x + layer * 10.0)) - 0.5) * 0.15;
            
            float2 f = fract(p);                   // Local cell position.
            // Two hashes: one for particle existence, one for individual size variance.
            float2 h = float2(hash12(id + layer * 13.0), hash12(id + layer * 27.0));
            
            if (h.x > 0.94 - iIntensity * 0.1) {   // Threshold check for snowfall density.
                float2 pos = 0.07 + h * 0.8;        // Randomized position within the cell.
                float d = length(f - pos);         // Distance from the flake center.
                float size = sizeBase * (0.6 + h.y * 0.5); // Randomize flake diameter.
                // Render flake as a soft circular gradient.
                acc += smoothstep(size, size - blurBase, d) * (0.5 + h.x * 0.5);
            }
        }
        
        // Return white color with the calculated particle mask.
        return half4(1.0, 1.0, 1.0, clamp(acc * (0.7 + iIntensity * 0.3), 0.0, 1.0) + useAll());
    }
"""

// FOG_SHADER: Volumetric ground mist using domain-warping for a shifting, fluid appearance.
@Language("AGSL")
const val FOG_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;    // Screen coordinates.
        
        float2 p = uv * float2(1.2, 2.0);          // Scale coordinates for mist wisps.
        float t = iTime * 0.1;                     // Slow drift speed multiplier.
        p.x += t;                                  // Slowly drift the fog horizontally.
        
        // Domain Warping: Feeding noise into noise creates complex, non-linear swirling mist.
        float n = fbm(p + fbm(p * 0.8 + t * 0.05));
        
        // Vertical Mask: Anchors fog to the BOTTOM (ground) and fades it towards the sky.
        float mask = smoothstep(0.3, 1.0, uv.y);
        float alpha = n * (0.8 + iIntensity * 0.4) * mask; // Adjust density by intensity.
        
        // Return hazy white color with transparency and crash protection.
        return half4(0.95, 0.97, 1.0, clamp(alpha * 0.8, 0.0, 0.8) + useAll());
    }
"""

// THUNDER_SHADER: High-intensity storm effects with randomized additive lightning flashes.
@Language("AGSL")
const val THUNDER_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;    // Normalized screen coordinates.
        
        // Lightning logic: Trigger a sudden white additive burst using a fast-stepping hash.
        float hTime = iTime * 10.0;                // Speed of the flash calculation.
        float flash = step(0.985, hash11(floor(hTime))) * hash11(hTime) * step(0.4, fract(hTime));
        
        float acc = 0.0;                           // Accumulator for storm rain.
        for (int i = 0; i < 2; i++) {              // 2 layers of very fast raindrops.
            float2 p = uv * float2(40.0 + float(i) * 20.0, 20.0);
            p.x += uv.y * 0.4;                     // Wind-blown slant.
            p.y += iTime * 40.0;                   // Terminal velocity: extreme speed.
            float2 id = floor(p);                  // Cell ID.
            float h = hash12(id);                  // Cell random seed.
            if (h > 0.96) {                        // Particle check.
                float jitter = hash11(id.y * 7.89); // Horizontal position jitter.
                // Draw a thin, sharp streak for each storm drop.
                acc += smoothstep(0.05, 0.0, abs(fract(p.x) - jitter)) * 
                       smoothstep(0.0, 0.1, fract(p.y)) * 
                       smoothstep(0.5, 0.2, fract(p.y));
            }
        }
        
        // Return rain streaks + full-screen additive lightning glow.
        return half4(0.8, 0.9, 1.0, clamp(acc * 0.6, 0.0, 1.0)) + half4(flash * 0.5) + useAll();
    }
"""

// CLOUD_DRIFT_SHADER: Slow volumetric noise for overcast or partly cloudy conditions.
@Language("AGSL")
const val CLOUD_DRIFT_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;    // Normalized screen coordinates.
        float2 p = uv * float2(1.0, 1.5);          // Aspect-ratio correction for noise.
        p.x -= iTime * 0.02;                       // Extremely slow horizontal drift.
        float n = fbm(p + fbm(p * 1.1) * 0.2);     // Volumetric cloud density calculation.
        // Sky mask: Only show clouds at the TOP of the screen (near uv.y=0).
        float alpha = n * 0.35 * (1.0 - uv.y) * (0.5 + iIntensity * 0.5);
        return half4(1.0, 1.0, 1.0, clamp(alpha, 0.0, 1.0) + useAll());
    }
"""

/**
 * WeatherEffects: A GPU-accelerated atmospheric overlay composable.
 * Uses AGSL shaders to render real-time weather conditions over the wallpaper.
 */
@Composable
fun WeatherEffects(
    weatherCode: Int,                              // Current WMO weather code.
    temperature: Double = 20.0,                    // Current temperature for snowflake physics.
    precipitation: Double = 0.0,                   // Rain/Snow volume for density scaling.
    forceIntensity: Float? = null                  // Debug intensity override.
) {
    // AGSL RuntimeShaders are only supported on Android 13 (Tiramisu) and higher.
    if (Build.VERSION.SDK_INT < 33) return

    // Mapping: meteorological condition codes to specific AGSL shader sources.
    val shaderSource = when (weatherCode) {
        1, 2, 3 -> CLOUD_DRIFT_SHADER              // Partly cloudy to overcast.
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> RAIN_SHADER // Drizzle, rain, and showers.
        71, 73, 75, 77, 85, 86 -> SNOW_SHADER      // Snowfall and snow grains.
        45, 48 -> FOG_SHADER                       // Fog and rime fog.
        95, 96, 99 -> THUNDER_SHADER               // Thunderstorm and heavy storm.
        else -> null                               // Clear sky (no overlay).
    } ?: return

    // Intensity Calculation: Map the weather volume to a normalized 0.0 - 1.0 scale.
    val intensity = forceIntensity ?: when (weatherCode) {
        51, 61, 80, 71, 77 -> 0.2f                 // Light intensity presets.
        53, 63, 81, 73, 85 -> 0.5f                 // Moderate intensity presets.
        55, 65, 82, 75, 86, 95, 96, 99 -> 1.0f     // Heavy intensity presets.
        else -> (precipitation.coerceIn(0.0, 10.0) / 10.0).toFloat() // Continuous mapping.
    }

    // infiniteTransition: Creates a continuous value used to drive iTime for shader animations.
    val infiniteTransition = rememberInfiniteTransition(label = "WeatherTime")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,                       // Target a large value to ensure long-running drift.
        animationSpec = infiniteRepeatable(
            animation = tween(100000, easing = LinearEasing), // 100-second cycle for smoothness.
            repeatMode = RepeatMode.Restart
        ),
        label = "Time"
    )

    // remember(shaderSource): Prevents expensive shader re-compilation unless the weather state changes.
    val shader = remember(shaderSource) { RuntimeShader(shaderSource) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                // Brush wrapper for the compiled AGSL shader.
                val brush = ShaderBrush(shader)
                onDrawWithContent {
                    drawContent()                  // Render the wallpaper/content below the weather.
                    // Set runtime uniforms for the GPU to use during the next draw pass.
                    shader.setFloatUniform("iResolution", size.width, size.height)
                    shader.setFloatUniform("iTime", time)
                    shader.setFloatUniform("iIntensity", intensity)
                    shader.setFloatUniform("iTemperature", temperature.toFloat())
                    drawRect(brush)                // Draw the AGSL effect over the entire screen.
                }
            }
    )
}
