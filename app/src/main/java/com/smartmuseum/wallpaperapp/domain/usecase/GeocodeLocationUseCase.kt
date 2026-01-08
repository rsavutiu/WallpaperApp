package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

data class LocationResult(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

class GeocodeLocationUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(query: String): List<LocationResult> = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(query, 5)
            addresses?.map { address ->
                LocationResult(
                    name = listOfNotNull(
                        address.locality,
                        address.adminArea,
                        address.countryName
                    ).joinToString(", "),
                    latitude = address.latitude,
                    longitude = address.longitude
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
