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

## How it Works

The application follows a clean architecture pattern, with the logic separated into three main layers: **Data**, **Domain**, and **UI**.

1.  **UI Layer**: The UI layer is built with Jetpack Compose and is responsible for displaying the wallpaper and the application's settings screens. It observes a `ViewModel` for state changes and user interactions.

2.  **Domain Layer**: The domain layer contains the core business logic of the application. It is composed of several use cases, each with a single responsibility:
    *   `GetWeatherUseCase`: Fetches the current weather conditions from the repository.
    *   `GetLocationNameUseCase`: Retrieves the user's current city name.
    *   `GetImageQueryUseCase`: Generates a search query for the image provider based on the weather and time of day. This use case also uses the `GetMoonPhaseUseCase` to determine the current moon phase for night-time queries.
    *   `GetAtmosImageUseCase`: Orchestrates the process of fetching the weather, location, and image, and combines them into a single `AtmosImage` object.
    *   `UpdateWallpaperUseCase`: Downloads the selected image and caches it locally.
    *   `SaveWallpaperUseCase`: Saves the downloaded image and its metadata to the device's internal storage.

3.  **Data Layer**: The data layer is responsible for fetching data from remote and local sources. It includes repositories for accessing the weather API, image providers, and the user's calendar.

The application uses `WorkManager` to schedule periodic background updates. The `WallpaperWorker` class is responsible for triggering the `GetAtmosImageUseCase` and `UpdateWallpaperUseCase` to refresh the wallpaper at regular intervals.

## Architecture

- **Clean Architecture**: Separation of concerns into Data, Domain, and UI layers, with a strong emphasis on the Single Responsibility Principle.
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
