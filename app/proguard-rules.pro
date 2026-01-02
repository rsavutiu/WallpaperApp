# --- General Android ---
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# --- Jetpack Compose ---
-keep class androidx.compose.ui.platform.** { *; }
-keep class androidx.compose.runtime.** { *; }

# --- Hilt / Dagger ---
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class * extends dager.hilt.android.internal.managers.**
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.EntryPoint class *

# --- Gson ---
# Keep model classes used for JSON serialization
-keep class com.smartmuseum.wallpaperapp.domain.model.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.TypeAdapter
-keep class com.google.gson.stream.JsonReader
-keep class com.google.gson.stream.JsonWriter

# --- Retrofit ---
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations
-keep class retrofit2.** { *; }
-keepclasseswithmembers interface retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Coil ---
-keep class io.coilkt.coil.** { *; }
-dontwarn io.coilkt.coil.**

# --- WorkManager ---
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# --- App Specific ---
# Keep the Wallpaper and Dream services
-keep class com.smartmuseum.wallpaperapp.ui.dream.AtmosDreamService { *; }

# Keep the Application class
-keep class com.smartmuseum.wallpaperapp.AtmosApplication { *; }
