# Atmos - Dynamic Atmospheric Wallpaper

Atmos is a dynamic wallpaper application for Android and Android TV that transforms your background
based on real-time weather, location, and your personal schedule.

## Features

- **Dynamic Weather Overlays**: Real-time AGSL shaders for rain, snow, fog, and thunderstorms that
  react to actual weather conditions.
- **Multi-Provider Imagery**: Fetches high-quality backgrounds from Unsplash, Pexels, Pixabay, and
  NASA APOD.
- **Material You Integration**: Automatically extracts colors from the current wallpaper using the
  Palette API to theme the app UI.
- **Calendar Integration**: Displays your today's schedule (Google Calendar) directly on the
  wallpaper.
- **Android TV Support**: Includes a dedicated Daydream screensaver mode optimized for large
  screens.
- **Localization**: Full support for over 15 languages.

## Architecture

- **Clean Architecture**: Separation of concerns into Data, Domain, and UI layers.
- **Jetpack Compose**: Modern declarative UI for both mobile and TV.
- **WorkManager**: Periodic background updates to keep the wallpaper fresh.
- **Hilt**: Dependency injection for a modular and testable codebase.
- **AGSL (Android Graphics Shading Language)**: High-performance GPU-based weather effects.

## Setup

1. Clone the repository.
2. Add your API keys to `local.properties`:
   ```properties
   NASA_API_KEY=your_nasa_key
   UNSPLASH_ACCESS_KEY=your_unsplash_key
   PEXELS_API_KEY=your_pexels_key
   PIXABAY_API_KEY=your_pixabay_key
   ```
3. Build and run on an Android device or TV emulator.

## Testing on Android TV

To test the Screensaver (Daydream) on an emulator:

1. Go to **Settings > Device Preferences > Screen saver**.
2. Select **Screen saver** and choose **Atmos**.
3. Use `adb shell am startservice -n com.smartmuseum.wallpaperapp/.ui.dream.AtmosDreamService` to
   trigger it immediately.

## License

MIT License
