package com.example.solar

import com.example.data.DailySolarInfo
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * 100% Offline Astronomical Sunrise & Sunset Engine based on the NOAA Solar Calculation equations
 * (Jean Meeus, Astronomical Algorithms). Requires no internet connection or external API.
 */
object NoaaSolarCalculator {

    private const val STANDARD_ZENITH_DEG = 90.833 // Official sunrise/sunset (refraction + solar radius)
    private const val CIVIL_TWILIGHT_ZENITH_DEG = 96.0 // Civil dawn / dusk

    fun calculateDay(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId = ZoneId.systemDefault(),
        includeDelta: Boolean = true
    ): DailySolarInfo {
        val core = computeRawSolarTimes(date, latitude, longitude, zoneId)
        val delta = if (includeDelta) {
            val prev = computeRawSolarTimes(date.minusDays(1), latitude, longitude, zoneId)
            core.daylightMinutes - prev.daylightMinutes
        } else {
            0
        }
        return core.copy(daylightDeltaMinutes = delta)
    }

    fun calculateMultiDaySchedule(
        startDate: LocalDate,
        days: Int,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<DailySolarInfo> {
        if (days <= 0) return emptyList()
        var previousDaylight = computeRawSolarTimes(
            startDate.minusDays(1),
            latitude,
            longitude,
            zoneId
        ).daylightMinutes

        return (0 until days).map { offset ->
            val currentDate = startDate.plusDays(offset.toLong())
            val info = computeRawSolarTimes(currentDate, latitude, longitude, zoneId)
            val delta = info.daylightMinutes - previousDaylight
            previousDaylight = info.daylightMinutes
            info.copy(daylightDeltaMinutes = delta)
        }
    }

    /**
     * Returns progress from 0f (sunrise) to 1f (sunset) during daytime, or clamped outside.
     */
    fun calculateDaylightProgress(now: LocalTime, info: DailySolarInfo): Float {
        val nowSec = now.toSecondOfDay().toFloat()
        val riseSec = info.sunrise.toSecondOfDay().toFloat()
        val setSec = info.sunset.toSecondOfDay().toFloat()
        if (setSec <= riseSec) return 0.5f
        return ((nowSec - riseSec) / (setSec - riseSec)).coerceIn(0f, 1f)
    }

    private fun computeRawSolarTimes(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId
    ): DailySolarInfo {
        val clampedLat = latitude.coerceIn(-89.9, 89.9)
        val zonedNoon = date.atTime(12, 0).atZone(zoneId)
        val tzOffsetHours = zonedNoon.offset.totalSeconds / 3600.0

        // Julian Day at 12:00 local time converted to UTC
        val jdLocalNoon = toJulianDay(date.year, date.monthValue, date.dayOfMonth) - tzOffsetHours / 24.0
        val julianCentury = (jdLocalNoon - 2451545.0) / 36525.0

        // Geometric Mean Longitude of Sun (deg)
        val geomMeanLongSun = (280.46646 + julianCentury * (36000.76983 + julianCentury * 0.0003032)) % 360.0
        val normalizedLong = if (geomMeanLongSun < 0) geomMeanLongSun + 360.0 else geomMeanLongSun

        // Geometric Mean Anomaly of Sun (deg)
        val geomMeanAnomSun = 357.52911 + julianCentury * (35999.05029 - 0.0001537 * julianCentury)
        val anomRad = Math.toRadians(geomMeanAnomSun)

        // Eccentricity of Earth Orbit
        val eccentEarthOrbit = 0.016708634 - julianCentury * (0.000042037 + 0.0000001267 * julianCentury)

        // Sun Eq of Ctr
        val sunEqOfCtr = sin(anomRad) * (1.914602 - julianCentury * (0.004817 + 0.000014 * julianCentury)) +
            sin(2.0 * anomRad) * (0.019993 - 0.000101 * julianCentury) +
            sin(3.0 * anomRad) * 0.000289

        val sunTrueLong = normalizedLong + sunEqOfCtr
        val omega = 125.04 - 1934.136 * julianCentury
        val sunAppLong = sunTrueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))

        // Mean & Corrected Obliquity of Ecliptic
        val meanObliqEcliptic = 23.0 + (26.0 + ((21.448 - julianCentury * (46.815 + julianCentury * (0.00059 - julianCentury * 0.001813)))) / 60.0) / 60.0
        val obliqCorr = meanObliqEcliptic + 0.00256 * cos(Math.toRadians(omega))

        // Solar Declination (rad)
        val sunDeclinRad = asin(sin(Math.toRadians(obliqCorr)) * sin(Math.toRadians(sunAppLong)))

        // Equation of Time (minutes)
        val varY = tan(Math.toRadians(obliqCorr / 2.0)).let { it * it }
        val longRad = Math.toRadians(normalizedLong)
        val eqOfTime = 4.0 * Math.toDegrees(
            varY * sin(2.0 * longRad) -
                2.0 * eccentEarthOrbit * sin(anomRad) +
                4.0 * eccentEarthOrbit * varY * sin(anomRad) * cos(2.0 * longRad) -
                0.5 * varY * varY * sin(4.0 * longRad) -
                1.25 * eccentEarthOrbit * eccentEarthOrbit * sin(2.0 * anomRad)
        )

        // Solar Noon in minutes from local midnight
        val solarNoonMinutes = 720.0 - 4.0 * longitude - eqOfTime + tzOffsetHours * 60.0

        val latRad = Math.toRadians(clampedLat)
        val cosHaSunrise = (cos(Math.toRadians(STANDARD_ZENITH_DEG)) / (cos(latRad) * cos(sunDeclinRad))) -
            tan(latRad) * tan(sunDeclinRad)

        val isPolarDay = cosHaSunrise < -1.0
        val isPolarNight = cosHaSunrise > 1.0

        val haSunriseDeg = when {
            isPolarDay -> 180.0
            isPolarNight -> 0.0
            else -> Math.toDegrees(acos(cosHaSunrise))
        }

        val cosHaCivil = (cos(Math.toRadians(CIVIL_TWILIGHT_ZENITH_DEG)) / (cos(latRad) * cos(sunDeclinRad))) -
            tan(latRad) * tan(sunDeclinRad)
        val haCivilDeg = when {
            cosHaCivil < -1.0 -> 180.0
            cosHaCivil > 1.0 -> 0.0
            else -> Math.toDegrees(acos(cosHaCivil))
        }

        val sunriseMinutes = solarNoonMinutes - haSunriseDeg * 4.0
        val sunsetMinutes = solarNoonMinutes + haSunriseDeg * 4.0
        val civilDawnMinutes = solarNoonMinutes - haCivilDeg * 4.0
        val civilDuskMinutes = solarNoonMinutes + haCivilDeg * 4.0

        val daylightMinutes = when {
            isPolarDay -> 1440
            isPolarNight -> 0
            else -> (haSunriseDeg * 8.0).roundToInt().coerceIn(0, 1440)
        }

        return DailySolarInfo(
            date = date,
            civilDawn = minutesToLocalTime(civilDawnMinutes),
            sunrise = minutesToLocalTime(sunriseMinutes),
            solarNoon = minutesToLocalTime(solarNoonMinutes),
            sunset = minutesToLocalTime(sunsetMinutes),
            civilDusk = minutesToLocalTime(civilDuskMinutes),
            daylightMinutes = daylightMinutes,
            isPolarDay = isPolarDay,
            isPolarNight = isPolarNight
        )
    }

    private fun toJulianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2.0 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun minutesToLocalTime(rawMinutes: Double): LocalTime {
        val totalSeconds = ((rawMinutes * 60.0).roundToInt() % 86400 + 86400) % 86400
        return LocalTime.ofSecondOfDay(totalSeconds.toLong())
    }
}
