# Add project specific ProGuard rules here.
# You can find more samples at https://r8.googlesource.com/r8/+/main/compatibility-tool/configs/examples

# Keep the data models used by Retrofit/Gson from being obfuscated or removed.
# This is essential for JSON serialization/deserialization to work in release builds.
-keep class com.smartmuseum.wallpaperapp.data.remote.** { *; }
-keep class com.smartmuseum.wallpaperapp.domain.model.** { *; }

# Hilt uses generated code, so we need to keep annotations and generated classes.
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager
-keep class * extends dagger.hilt.android.internal.managers.FragmentComponentManager
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager
-keep class * extends dagger.hilt.android.internal.managers.ViewModelComponentManager
-keep class * extends dagger.hilt.android.internal.managers.ServiceComponentManager

# This is generated automatically by the Android Gradle plugin.
-dontwarn edu.umd.cs.findbugs.annotations.Nullable

# Keep Hilt entry points and generated modules
-keep @dagger.hilt.EntryPoint class *
-keep @dagger.hilt.InstallIn class *
-keep @dagger.Module class *
-keep @dagger.assisted.AssistedInject class *
-keep @dagger.assisted.AssistedFactory class *
-keep @dagger.assisted.Assisted class *
