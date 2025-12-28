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
    uniform float iTime;            // Global time in seconds for consistent animations.
    uniform float iIntensity;       // Weather intensity (0.0 to 1.0) for density/speed control.
    uniform float iTemperature;     // Ambient temperature in Celsius for physical behavior.

    // hash12: Generates a pseudo-random float from a 2D position (seed).
    // Used to give unique random properties to each grid cell (e.g., if a drop exists).
    float hash12(float2 p) {
        float3 p3  = fract(float3(p.xyx) * .1031); // Scale and bit-shift via fract.
        p3 += dot(p3, p3.yzx + 33.33);             // Cross-multiply components.
        return fract((p3.x + p3.y) * p3.z);        // Final normalized hash (0.0 - 1.0).
    }

    // hash11: Generates a 1D random float from a single input seed.
    float hash11(float p) {
        p = fract(p * .1031);                      // Scale input.
        p *= p + 33.33;                            // Scramble.
        p *= p + p;                                // More scrambling.
        return fract(p);                           // Result (0.0 - 1.0).
    }

    // noise: Smoothly interpolated noise based on a grid of random hash values.
    // Creates organic, non-linear gradients for movement like fog or snow drift.
    float noise(float2 p) {
        float2 i = floor(p);                       // Integer grid point.
        float2 f = fract(p);                       // Relative position in cell.
        float2 u = f * f * (3.0 - 2.0 * f);        // Smooth hermite interpolation.
        // Bilinear interpolation between the four cell corners.
        return mix(mix(hash12(i + float2(0.0, 0.0)), 
                       hash12(i + float2(1.0, 0.0)), u.x),
                   mix(hash12(i + float2(0.0, 1.0)), 
                       hash12(i + float2(1.0, 1.0)), u.x), u.y);
    }

    // fbm (Fractal Brownian Motion): Sums multiple noise layers at increasing frequencies.
    // Simulates volumetric textures like clouds, smoke, or thick mist.
    float fbm(float2 p) {
        float v = 0.0;                             // Accumulator.
        float a = 0.5;                             // Amplitude of current layer.
        float2 shift = float2(100.0);              // Offset to hide pattern repeats.
        for (int i = 0; i < 4; ++i) {              // 4 octaves of detail.
            v += a * noise(p);                     // Add weighted noise.
            p = p * 2.0 + shift;                   // Double frequency for next octave.
            a *= 0.5;                              // Halve amplitude for next octave.
        }
        return v;                                  // Normalized density result.
    }
    
    // useAll: Prevents the AGSL compiler from optimizing out "unused" uniforms.
    // If a uniform is missing from the binary, setFloatUniform in Kotlin will crash.
    float useAll() {
        return (iResolution.x + iTime + iIntensity + iTemperature) * 0.0000001;
    }
"""

// RAIN_SHADER: Simulates natural falling rain with multi-layer parallax and randomized density.
@Language("AGSL")
const val RAIN_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;    // Normalized screen coordinates.
        float acc = 0.0;                           // Alpha accumulator for droplets.
        
        for (int i = 0; i < 3; i++) {              // 3 independent parallax layers.
            float layer = float(i);                // Layer index.
            // Scale grid: higher vertical frequency (25.0) prevents long stream "waterfalls".
            float2 p = uv * float2(30.0 + layer * 20.0, 25.0); 
            
            // Apply wind slant based on intensity.
            float slant = 0.1 + iIntensity * 0.3;
            p.x += uv.y * slant;                   // Horizontal shift increases with depth.
            
            // Set fall speed: near layers are faster than distant layers.
            float speed = (15.0 + layer * 10.0 + iIntensity * 20.0);
            p.y -= iTime * speed;                  // Move coordinate DOWN.
            
            float2 id = floor(p);                  // Discrete cell ID.
            float2 f = fract(p);                   // Local coordinates inside the cell.
            float h = hash12(id + layer * 123.4);  // Random check for drop existence.
            
            // Sparse Threshold: Uses pow(intensity, 2) to ensure light rain is extremely sparse.
            float threshold = 1.0 - (0.001 + pow(iIntensity, 2.0) * 0.08);
            if (h > threshold) {                   // Only render if cell hash passes check.
                // jitter: Randomizes horizontal position within cell to break column alignment.
                float jitter = hash11(id.y * 12.34 + layer);
                float xPos = 0.2 + jitter * 0.6;   // Keep drops within cell safe zones.
                
                // Drop shape: Narrow horizontal peak, faded vertically at start/end.
                float drop = smoothstep(0.05, 0.0, abs(f.x - xPos)) * 
                             smoothstep(0.0, 0.1, f.y) * 
                             smoothstep(0.4, 0.2, f.y);
                
                acc += drop * (0.3 + layer * 0.2); // Cumulative brightness based on depth.
            }
        }
        
        // Output: Subtle blue-tinted rain color with clamped opacity and uniform protection.
        return half4(0.7, 0.85, 1.0, clamp(acc, 0.0, 1.0) + useAll());
    }
"""

// SNOW_SHADER: Renders snowflakes with temperature-aware physics and randomized natural drift.
@Language("AGSL")
const val SNOW_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;    // Normalized screen coordinates.
        float acc = 0.0;                           // Alpha accumulator for flakes.
        
        // tempFactor: maps -15C to 0 (powder) and 0C to 1 (wet snowflakes).
        float tempFactor = clamp((iTemperature + 15.0) / 15.0, 0.0, 1.0); 
        float sizeBase = mix(0.03, 0.07, tempFactor); // Larger flakes when warmer.
        float blurBase = mix(0.01, 0.04, tempFactor); // Softer flakes when warmer.
        
        for(int i=0; i<3; i++) {                   // 3 layers of depth.
            float layer = float(i);                // Layer index.
            float scale = 7.0 + layer * 5.0;       // Grid density increases with depth.
            float2 p = uv * scale;                 // Scale coords to grid.

            float speed = (0.5 + layer * 0.3);
            p.y -= iTime * speed; 
            
            float2 id = floor(p);                  // Grid cell integer ID.
            
            // random drift: Replaced sine wobble with smooth noise for organic, subtle sway.
            p.x += (noise(float2(iTime * 0.2, id.x + layer * 10.0)) - 0.5) * 0.06;
            
            float2 f = fract(p);                   // Local coords inside cell.
            // Two random hashes for position variance and per-flake size jitter.
            float2 h = float2(hash12(id + layer * 13.0), hash12(id + layer * 27.0));
            
            if (h.x > 0.94 - iIntensity * 0.1) {   // Threshold check for flake count.
                float2 pos = 0.07 + h * 0.8;        // Randomized local center point.
                float d = length(f - pos);         // Distance to center.
                float size = sizeBase * (0.6 + h.y * 0.5); // Per-flake size variation.
                // Render as a soft circle.
                acc += smoothstep(size, size - blurBase, d) * (0.5 + h.x * 0.5);
            }
        }
        
        // Output: Pure white snow with clamped alpha and dummy uniform use.
        return half4(1.0, 1.0, 1.0, clamp(acc * (0.7 + iIntensity * 0.3), 0.0, 1.0) + useAll());
    }
"""

// FOG_SHADER: Generates low-hanging, swirling ground mist using domain-warped FBM noise.
@Language("AGSL")
const val FOG_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;    // Normalized screen coordinates.
        
        float2 p = uv * float2(1.2, 2.0);          // Scale coordinates for mist patterns.
        float t = iTime * 0.1;                     // Slow animation factor.
        p.x += t;                                  // Drift mist horizontally.
        
        // Layered swirling: warping the noise input with another noise layer.
        float n = fbm(p + fbm(p * 0.8 + t * 0.05));
        
        // Ground mask: Fog is visible at the BOTTOM (uv.y > 0.3) and fades upwards.
        float mask = smoothstep(0.3, 1.0, uv.y);
        float alpha = n * (0.8 + iIntensity * 0.4) * mask; // Adjust alpha by severity.
        
        // Output: Thick misty white with uniform stability protection.
        return half4(0.95, 0.97, 1.0, clamp(alpha * 0.8, 0.0, 0.8) + useAll());
    }
"""

// THUNDER_SHADER: Aggressive heavy rain with randomized full-screen lightning bursts.
@Language("AGSL")
const val THUNDER_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;    // Normalized coordinates.
        
        // lightning: sudden white additive flash using high-speed stepped hash.
        float hTime = iTime * 10.0;
        float flash = step(0.985, hash11(floor(hTime))) * hash11(hTime) * step(0.4, fract(hTime));
        
        float acc = 0.0;                           // Storm rain accumulator.
        for (int i = 0; i < 2; i++) {              // 2 layers of fast storm drops.
            float2 p = uv * float2(40.0 + float(i) * 20.0, 20.0);
            p.x += uv.y * 0.4;                     // Sharp slant for wind.
            p.y -= iTime * 40.0;                   // Very high downward velocity.
            float2 id = floor(p);                  // Grid ID.
            float h = hash12(id);                  // Cell hash.
            if (h > 0.96) {                        // Particle check.
                float jitter = hash11(id.y * 7.89);
                // Sharp droplet streaks.
                acc += smoothstep(0.05, 0.0, abs(fract(p.x) - jitter)) * 
                       smoothstep(0.0, 0.1, fract(p.y)) * 
                       smoothstep(0.5, 0.2, fract(p.y));
            }
        }
        
        // Result: Combined rain opacity and lightning flash brightness.
        return half4(0.8, 0.9, 1.0, clamp(acc * 0.6, 0.0, 1.0)) + half4(flash * 0.5) + useAll();
    }
"""

// CLOUD_DRIFT_SHADER: Slow-moving cloud noise for overcast conditions.
@Language("AGSL")
const val CLOUD_DRIFT_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;    // Normalized coordinates.
        float2 p = uv * float2(1.0, 1.5);          // Coordinate scaling for wisps.
        p.x -= iTime * 0.02;                       // Extremely slow drifting movement.
        float n = fbm(p + fbm(p * 1.1) * 0.2);     // Volumetric cloud density.
        // Sky mask: clouds appear at the top (uv.y near 0) and fade downward.
        float alpha = n * 0.35 * (1.0 - uv.y) * (0.5 + iIntensity * 0.5);
        return half4(1.0, 1.0, 1.0, clamp(alpha, 0.0, 1.0) + useAll());
    }
"""

/**
 * WeatherEffects: GPU-powered atmospheric overlay component.
 * Maps weather states to AGSL shaders and drives them with real-time uniforms.
 */
@Composable
fun WeatherEffects(
    weatherCode: Int,                              // WMO Weather code from API.
    temperature: Double = 20.0,                    // Current temp for flake sizing.
    precipitation: Double = 0.0,                   // Rain volume for intensity scaling.
    forceIntensity: Float? = null                  // Debug override.
) {
    // Shaders require Android 13+ APIs.
    if (Build.VERSION.SDK_INT < 33) return

    // Pick the shader source based on the weather state.
    val shaderSource = when (weatherCode) {
        1, 2, 3 -> CLOUD_DRIFT_SHADER
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> RAIN_SHADER
        71, 73, 75, 77, 85, 86 -> SNOW_SHADER
        45, 48 -> FOG_SHADER
        95, 96, 99 -> THUNDER_SHADER
        else -> null
    } ?: return

    // Calculate normalized intensity (0.0 to 1.0).
    val intensity = forceIntensity ?: when (weatherCode) {
        51, 61, 80, 71, 77 -> 0.2f                 // Light states.
        53, 63, 81, 73, 85 -> 0.5f                 // Moderate states.
        55, 65, 82, 75, 86, 95, 96, 99 -> 1.0f     // Heavy states.
        else -> (precipitation.coerceIn(0.0, 10.0) / 10.0).toFloat()
    }

    // infiniteTransition: Drives the 'iTime' uniform for continuous movement.
    val infiniteTransition = rememberInfiniteTransition(label = "WeatherTime")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(100000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Time"
    )

    // remember(shaderSource): Only recompile the shader if the weather type changes.
    val shader = remember(shaderSource) { RuntimeShader(shaderSource) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                // Brush created from the compiled AGSL shader.
                val brush = ShaderBrush(shader)
                onDrawWithContent {
                    drawContent()                  // Draw the background image first.
                    // Set latest uniform values for this frame.
                    shader.setFloatUniform("iResolution", size.width, size.height)
                    shader.setFloatUniform("iTime", time)
                    shader.setFloatUniform("iIntensity", intensity)
                    shader.setFloatUniform("iTemperature", temperature.toFloat())
                    drawRect(brush)                // Draw the weather effect over it.
                }
            }
    )
}
