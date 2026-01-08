package com.smartmuseum.wallpaperapp.domain.usecase

import com.smartmuseum.wallpaperapp.domain.model.SunData
import org.shredzone.commons.suncalc.SunTimes
import java.util.Calendar
import javax.inject.Inject

class GetSunDataUseCase @Inject constructor() {
    operator fun invoke(lat: Double, lon: Double): SunData {
        val calendar = Calendar.getInstance()

        val times = SunTimes.compute()
            .at(lat, lon)
            .on(calendar)
            .execute()

        // Astronomical twilight (Sun between 12 and 18 degrees below horizon)
        val astronomicalTimes = SunTimes.compute()
            .at(lat, lon)
            .on(calendar)
            .twilight(SunTimes.Twilight.ASTRONOMICAL)
            .execute()

        // Civil twilight (Sun between 0 and 6 degrees below horizon)
        val civilTimes = SunTimes.compute()
            .at(lat, lon)
            .on(calendar)
            .twilight(SunTimes.Twilight.CIVIL)
            .execute()

        // Nautical twilight (Sun between 6 and 12 degrees below horizon)
        val nauticalTimes = SunTimes.compute()
            .at(lat, lon)
            .on(calendar)
            .twilight(SunTimes.Twilight.NAUTICAL)
            .execute()

        return SunData(
            astronomicalDawn = astronomicalTimes.rise?.toInstant()?.toEpochMilli() ?: 0L,
            astronomicalDusk = astronomicalTimes.set?.toInstant()?.toEpochMilli() ?: 0L,
            civilDawn = civilTimes.rise?.toInstant()?.toEpochMilli() ?: 0L,
            civilDusk = civilTimes.set?.toInstant()?.toEpochMilli() ?: 0L,
            nauticalDawn = nauticalTimes.rise?.toInstant()?.toEpochMilli() ?: 0L,
            nauticalDusk = nauticalTimes.set?.toInstant()?.toEpochMilli() ?: 0L,
            sunrise = times.rise?.toInstant()?.toEpochMilli() ?: 0L,
            sunset = times.set?.toInstant()?.toEpochMilli() ?: 0L
        )
    }
}
