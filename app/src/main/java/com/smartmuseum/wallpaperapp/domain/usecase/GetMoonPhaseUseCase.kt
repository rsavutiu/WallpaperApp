package com.smartmuseum.wallpaperapp.domain.usecase

import android.content.Context
import com.smartmuseum.wallpaperapp.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject

class GetMoonPhaseUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val jd = if (month < 3) {
            val y = year - 1
            val m = month + 12
            (365.25 * y).toInt() + (30.6001 * (m + 1)).toInt() + day + 1720995
        } else {
            (365.25 * year).toInt() + (30.6001 * (month + 1)).toInt() + day + 1720995
        }
        val b = (jd - 2451545) / 29.53.toInt()
        val phase = (jd - 2451545) - (b * 29.53)

        return when {
            phase < 1.84 -> context.getString(R.string.moon_phase_new_moon)
            phase < 5.53 -> context.getString(R.string.moon_phase_waxing_crescent)
            phase < 9.22 -> context.getString(R.string.moon_phase_first_quarter)
            phase < 12.91 -> context.getString(R.string.moon_phase_waxing_gibbous)
            phase < 16.61 -> context.getString(R.string.moon_phase_full_moon)
            phase < 20.30 -> context.getString(R.string.moon_phase_waning_gibbous)
            phase < 23.99 -> context.getString(R.string.moon_phase_last_quarter)
            phase < 27.68 -> context.getString(R.string.moon_phase_waning_crescent)
            else -> context.getString(R.string.moon_phase_new_moon)
        }
    }
}
