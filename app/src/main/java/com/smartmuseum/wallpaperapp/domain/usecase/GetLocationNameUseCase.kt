package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

class GetLocationNameUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.let { address ->
                address.locality ?: address.subAdminArea ?: address.adminArea
            }
        } catch (_: Exception) {
            null
        }
    }
}
