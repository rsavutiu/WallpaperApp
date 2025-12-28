# Atmos - Atmospheric Dynamic Wallpaper

Atmos is a sophisticated Android application that transforms your device's background into a living window to the world. It automatically updates your wallpaper based on your current local weather, time of day, and location, overlaying beautiful imagery with high-performance atmospheric effects.

## Features

- **Dynamic Weather Sync**: Automatically matches your wallpaper to local conditions (Sunny, Rainy, Snowy, Foggy, etc.).
- **Multi-Provider Support**: Choose from a variety of high-quality image sources:
  - **Unsplash**: Premium curated photography.
  - **Pexels**: Vibrant and artistic shots.
  - **Pixabay**: Vast library of diverse imagery.
  - **SourceSplash**: Random, lightweight Unsplash discovery.
  - **NASA APOD**: Stunning celestial backgrounds for clear nights.
- **AGSL Atmospheric Shaders**: Real-time, GPU-accelerated visual effects:
  - **Natural Rain**: Multi-layered parallax drops with randomized timing.
  - **Organic Snow**: Temperature-aware physics (Arctic powder vs. Wet flakes).
  - **Volumetric Fog**: Swirling fractal mist that reacts to your screen.
  - **Cloud Drift**: Subtle, evolving cloud layers.
- **Android TV Integration**: Fully optimized for the big screen, including a dynamic screensaver (Daydream).
- **Material You**: Harmonious UI that adapts to your system theme.
- **Intelligent Background Updates**: Powered by WorkManager for battery-efficient, periodic refreshes.

## Tech Stack

- **UI**: Jetpack Compose
- **Graphics**: AGSL (Android Graphics Shading Language)
- **Dependency Injection**: Hilt
- **Networking**: Retrofit & OkHttp
- **Async & Streams**: Kotlin Coroutines & Flow
- **Image Loading**: Coil
- **Local Storage**: DataStore (Preferences)
- **Background Tasks**: WorkManager

## Setup

1. Add your API keys to `local.properties`:
   ```properties
   NASA_API_KEY=your_key
   UNSPLASH_ACCESS_KEY=your_key
   PIXABAY_API_KEY=your_key
   PEXELS_API_KEY=your_key
   ```
2. Build and run on an Android 13+ (API 33) device for full shader support.
