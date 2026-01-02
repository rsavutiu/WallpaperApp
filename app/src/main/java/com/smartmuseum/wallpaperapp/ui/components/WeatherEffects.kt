package com.smartmuseum.wallpaperapp.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    float hash12(float2 p) {
        float3 p3  = fract(float3(p.xyx) * .1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    // hash11: Generates a 1D random float from a single input seed.
    float hash11(float p) {
        p = fract(p * .1031);
        p *= p + 33.33;
        p *= p + p;
        return fract(p);
    }

    // noise: Smoothly interpolated value noise on a 2D grid.
    float noise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);
        return mix(mix(hash12(i + float2(0.0, 0.0)), 
                       hash12(i + float2(1.0, 0.0)), u.x),
                   mix(hash12(i + float2(0.0, 1.0)), 
                       hash12(i + float2(1.0, 1.0)), u.x), u.y);
    }

    // fbm (Fractal Brownian Motion): Sums multiple octaves of noise at varying scales.
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
    
    // useAll: Essential function to prevent the AGSL compiler from optimizing out unused uniforms.
    float useAll() {
        return (iResolution.x + iTime + iIntensity + iTemperature) * 0.0000001;
    }
"""

// RAIN_SHADER: High-performance particle engine for falling raindrops with depth parallax.
@Language("AGSL")
const val RAIN_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;
        float acc = 0.0;
        for (int i = 0; i < 3; i++) {
            float layer = float(i);
            float2 p = uv * float2(30.0 + layer * 20.0, 25.0); 
            float slant = 0.1 + iIntensity * 0.3;
            p.x += uv.y * slant;
            float speed = (15.0 + layer * 10.0 + iIntensity * 20.0);
            p.y += iTime * speed;
            float2 id = floor(p);
            float2 f = fract(p);
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
        return half4(0.7, 0.85, 1.0, clamp(acc, 0.0, 1.0) + useAll());
    }
"""

// SNOW_SHADER: Atmospheric snowfall with temperature-sensitive flake size and randomized speed.
@Language("AGSL")
const val SNOW_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;
        float acc = 0.0;
        
        float tempFactor = clamp((iTemperature + 15.0) / 15.0, 0.0, 1.0); 
        float sizeBase = mix(0.02, 0.05, tempFactor);
        float blurBase = mix(0.01, 0.03, tempFactor);
        
        for(int i=0; i<3; i++) {
            float layer = float(i);
            float scale = 7.0 + layer * 5.0;
            float2 p = uv * scale;

            // Randomized vertical speed per column
            float col_id = floor(p.x);
            float speed_rand = hash11(col_id + layer * 31.7);
            // Slower overall movement for natural drift: base + variance + parallax
            float speed = (0.15 + speed_rand * 0.2) * (0.6 + layer * 0.4);
            p.y -= iTime * speed;
            
            float2 id = floor(p);
            
            // Horizontal sway
            p.x += (noise(float2(iTime * 0.1, id.x + layer * 10.0)) - 0.5) * 0.2;
            
            float2 f = fract(p);
            float2 h = float2(hash12(id + layer * 13.0), hash12(id + layer * 27.0));
            
            if (h.x > 0.94 - iIntensity * 0.1) {
                float2 pos = 0.07 + h * 0.8;
                float d = length(f - pos);
                float size = sizeBase * (0.6 + h.y * 0.5);
                acc += smoothstep(size, size - blurBase, d) * (0.5 + h.x * 0.5);
            }
        }
        return half4(1.0, 1.0, 1.0, clamp(acc * (0.7 + iIntensity * 0.3), 0.0, 1.0) + useAll());
    }
"""

// FOG_SHADER: Volumetric ground mist using domain-warping.
@Language("AGSL")
const val FOG_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;
        float2 p = uv * float2(1.2, 2.0);
        float t = iTime * 0.1;
        p.x += t;
        float n = fbm(p + fbm(p * 0.8 + t * 0.05));
        float mask = smoothstep(0.3, 1.0, uv.y);
        float alpha = n * (0.8 + iIntensity * 0.4) * mask;
        return half4(0.95, 0.97, 1.0, clamp(alpha * 0.8, 0.0, 0.8) + useAll());
    }
"""

// THUNDER_SHADER: High-intensity storm effects with lightning.
@Language("AGSL")
const val THUNDER_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;
        float hTime = iTime * 10.0;
        float flash = step(0.985, hash11(floor(hTime))) * hash11(hTime) * step(0.4, fract(hTime));
        float acc = 0.0;
        for (int i = 0; i < 2; i++) {
            float2 p = uv * float2(40.0 + float(i) * 20.0, 20.0);
            p.x += uv.y * 0.4;
            p.y += iTime * 40.0;
            float2 id = floor(p);
            float h = hash12(id);
            if (h > 0.96) {
                float jitter = hash11(id.y * 7.89);
                acc += smoothstep(0.05, 0.0, abs(fract(p.x) - jitter)) * 
                       smoothstep(0.0, 0.1, fract(p.y)) * 
                       smoothstep(0.5, 0.2, fract(p.y));
            }
        }
        return half4(0.8, 0.9, 1.0, clamp(acc * 0.6, 0.0, 1.0)) + half4(flash * 0.5) + useAll();
    }
"""

// CLOUD_DRIFT_SHADER: Slow volumetric noise for overcast conditions.
@Language("AGSL")
const val CLOUD_DRIFT_SHADER = WEATHER_HEADER + """
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution.xy;
        float2 p = uv * float2(1.0, 1.5);
        p.x -= iTime * 0.02;
        float n = fbm(p + fbm(p * 1.1) * 0.2);
        float alpha = n * 0.35 * (1.0 - uv.y) * (0.5 + iIntensity * 0.5);
        return half4(1.0, 1.0, 1.0, clamp(alpha, 0.0, 1.0) + useAll());
    }
"""

@Composable
fun WeatherEffects(
    weatherCode: Int,
    temperature: Double = 20.0,
    precipitation: Double = 0.0,
    forceIntensity: Float? = null
) {
    if (Build.VERSION.SDK_INT < 33) return

    val shaderSource = when (weatherCode) {
        1, 2, 3 -> CLOUD_DRIFT_SHADER
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> RAIN_SHADER
        71, 73, 75, 77, 85, 86 -> SNOW_SHADER
        45, 48 -> FOG_SHADER
        95, 96, 99 -> THUNDER_SHADER
        else -> null
    } ?: return

    val intensity = forceIntensity ?: when (weatherCode) {
        51, 61, 80, 71, 77 -> 0.2f
        53, 63, 81, 73, 85 -> 0.5f
        55, 65, 82, 75, 86, 95, 96, 99 -> 1.0f
        else -> (precipitation.coerceIn(0.0, 10.0) / 10.0).toFloat()
    }

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

    val shader = remember(shaderSource) { RuntimeShader(shaderSource) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val brush = ShaderBrush(shader)
                onDrawWithContent {
                    drawContent()
                    shader.setFloatUniform("iResolution", size.width, size.height)
                    shader.setFloatUniform("iTime", time)
                    shader.setFloatUniform("iIntensity", intensity)
                    shader.setFloatUniform("iTemperature", temperature.toFloat())
                    drawRect(brush)
                }
            }
    )
}
