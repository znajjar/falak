package com.falak.livewallpaper

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tan

/**
 * Worldwide offline astronomical Islamic prayer-time calculator supporting arbitrary
 * latitude, longitude, IANA timezone (including DST transitions and International Date Line
 * offsets), selectable calculation sources (Jordan Ministry of Awqaf, MWL), and continuous
 * angle-based high-latitude twilight handling.
 *
 * Computes consecutive daily prayer instants so each of the five planets completes one
 * continuous 360° orbit per ~24h prayer cycle with zero midnight jump, crossing the center
 * meridian (angle = 0 rad) at exact prayer time.
 */
object PrayerTimesEngine {

    private const val SUNSET_ALT_DEG = -0.8333
    private const val MS_PER_HOUR = 3_600_000.0
    private const val MS_PER_DAY = 86_400_000.0
    private const val UNIX_EPOCH_JD = 2440587.5

    val PRAYER_NAMES = arrayOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    data class PrayerDay(
        val date: LocalDate,
        val instants: LongArray,
        val formattedTimes: Array<String>
    )

    data class OrbitalState(
        val location: PrayerLocation,
        val method: PrayerMethod,
        val anglesRad: FloatArray,
        val today: PrayerDay,
        val nextPrayerIndex: Int,
        val minutesToNext: Long,
        val localMinutesOfDay: Double,
        val nowEpochMs: Long
    )

    fun computeState(
        location: PrayerLocation,
        method: PrayerMethod,
        nowEpochMs: Long = System.currentTimeMillis()
    ): OrbitalState {
        val zone = location.resolveZone()
        val zdt = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
        val date = zdt.toLocalDate()

        val prev = computePrayerDay(location, method, date.minusDays(1))
        val today = computePrayerDay(location, method, date)
        val next = computePrayerDay(location, method, date.plusDays(1))

        var prev2: PrayerDay? = null
        var next2: PrayerDay? = null

        val angles = FloatArray(5)
        for (i in 0 until 5) {
            val t0: Long
            val t1: Long
            when {
                nowEpochMs < prev.instants[i] -> {
                    val p2 = prev2 ?: computePrayerDay(location, method, date.minusDays(2)).also { prev2 = it }
                    t0 = p2.instants[i]
                    t1 = prev.instants[i]
                }
                nowEpochMs < today.instants[i] -> {
                    t0 = prev.instants[i]
                    t1 = today.instants[i]
                }
                nowEpochMs < next.instants[i] -> {
                    t0 = today.instants[i]
                    t1 = next.instants[i]
                }
                else -> {
                    val n2 = next2 ?: computePrayerDay(location, method, date.plusDays(2)).also { next2 = it }
                    t0 = next.instants[i]
                    t1 = n2.instants[i]
                }
            }
            val span = (t1 - t0).coerceAtLeast(1L).toDouble()
            var phase = (nowEpochMs - t0).toDouble() / span
            phase = ((phase % 1.0) + 1.0) % 1.0
            if (phase >= 0.5) phase -= 1.0
            angles[i] = (phase * 2.0 * PI).toFloat()
        }

        // Find the earliest upcoming prayer instant across yesterday (e.g. post-midnight Isha),
        // today, and tomorrow.
        var nextIdx = 0
        var nextMs = Long.MAX_VALUE
        val candidateDays = arrayOf(prev, today, next)
        for (day in candidateDays) {
            for (i in 0 until 5) {
                val t = day.instants[i]
                if (t >= nowEpochMs && t < nextMs) {
                    nextMs = t
                    nextIdx = i
                }
            }
        }
        if (nextMs == Long.MAX_VALUE) {
            val n2 = next2 ?: computePrayerDay(location, method, date.plusDays(2))
            nextIdx = 0
            nextMs = n2.instants[0]
        }

        val minutesToNext = ((nextMs - nowEpochMs).coerceAtLeast(0L) + 30_000L) / 60_000L
        val localMin = zdt.hour * 60.0 + zdt.minute + zdt.second / 60.0

        return OrbitalState(
            location = location,
            method = method,
            anglesRad = angles,
            today = today,
            nextPrayerIndex = nextIdx,
            minutesToNext = minutesToNext,
            localMinutesOfDay = localMin,
            nowEpochMs = nowEpochMs
        )
    }

    fun computeStateForLocalMinutes(
        location: PrayerLocation,
        method: PrayerMethod,
        date: LocalDate,
        minutesOfDay: Double
    ): OrbitalState {
        val zone = location.resolveZone()
        val clamped = minutesOfDay.coerceIn(0.0, 1439.99)
        val totalSec = (clamped * 60.0).toInt()
        val h = (totalSec / 3600).coerceIn(0, 23)
        val m = ((totalSec % 3600) / 60).coerceIn(0, 59)
        val s = (totalSec % 60).coerceIn(0, 59)
        val epochMs = date.atTime(h, m, s).atZone(zone).toInstant().toEpochMilli()
        return computeState(location, method, epochMs)
    }

    fun computePrayerDay(
        location: PrayerLocation,
        method: PrayerMethod,
        date: LocalDate
    ): PrayerDay {
        val zone = location.resolveZone()
        val latRad = Math.toRadians(location.latitudeDeg.coerceIn(-85.0, 85.0))
        val lonDeg = location.longitudeDeg

        val utcMidnightMs = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        // Anchor solar transit to the local civil noon of `date` in `zone`, so DST transitions
        // and International Date Line offsets always select the exact transit belonging to `date`.
        val civilNoonUtcH = (date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() - utcMidnightMs) / MS_PER_HOUR

        fun utcNoonAt(utcHourApprox: Double): Pair<Double, Double> {
            val jd = (utcMidnightMs + utcHourApprox * MS_PER_HOUR) / MS_PER_DAY + UNIX_EPOCH_JD
            val (decl, eqt) = sunPosition(jd)
            var noonUtc = 12.0 - lonDeg / 15.0 - eqt
            noonUtc += round((civilNoonUtcH - noonUtc) / 24.0) * 24.0
            return Pair(noonUtc, decl)
        }

        // Exact Dhuhr (2-step fixed-point evaluation for sub-second precision)
        val (dhuhr0, _) = utcNoonAt(civilNoonUtcH)
        val (dhuhrUtc, _) = utcNoonAt(dhuhr0)

        // Sunrise & Sunset (Maghrib) with polar-day/polar-night guard
        val (srNoon, srDecl) = utcNoonAt(dhuhrUtc - 6.0)
        val srHaRaw = rawCosHourAngle(latRad, srDecl, SUNSET_ALT_DEG)
        val srHa = when {
            srHaRaw < -1.0 -> 11.5 // Polar day: symbolic 1h night window around solar midnight
            srHaRaw > 1.0 -> 0.5   // Polar night: symbolic 1h day window around solar noon
            else -> Math.toDegrees(acos(srHaRaw)) / 15.0
        }
        val sunriseUtc = srNoon - srHa

        val (ssNoon, ssDecl) = utcNoonAt(dhuhrUtc + 6.0)
        val ssHaRaw = rawCosHourAngle(latRad, ssDecl, SUNSET_ALT_DEG)
        val ssHa = when {
            ssHaRaw < -1.0 -> 11.5
            ssHaRaw > 1.0 -> 0.5
            else -> Math.toDegrees(acos(ssHaRaw)) / 15.0
        }
        val maghribUtc = ssNoon + ssHa

        // Night length for continuous angle-based high-latitude adjustment
        val nightHours = (24.0 - (maghribUtc - sunriseUtc)).coerceIn(1.0, 23.0)

        // Fajr (with continuous angle-based high-latitude protection)
        val (fNoon, fDecl) = utcNoonAt(sunriseUtc - 1.5)
        val fCos = rawCosHourAngle(latRad, fDecl, -method.fajrAngleDeg)
        val maxFajrPortion = nightHours * (method.fajrAngleDeg / 60.0)
        val fajrUtc = (if (fCos >= -1.0 && fCos <= 1.0) {
            val astroFajr = fNoon - Math.toDegrees(acos(fCos)) / 15.0
            val diff = sunriseUtc - astroFajr
            if (diff > maxFajrPortion && abs(location.latitudeDeg) > 45.0) {
                sunriseUtc - maxFajrPortion
            } else {
                astroFajr
            }
        } else {
            sunriseUtc - maxFajrPortion
        }).coerceAtMost(sunriseUtc - 0.05)

        // Asr (Standard shadow factor = 1x, with polar-night guard when zenith >= 90°)
        val (aNoon, aDecl) = utcNoonAt(dhuhrUtc + 3.0)
        val zenithRad = abs(latRad - aDecl)
        val asrUtc = if (zenithRad < PI * 0.495 && ssHaRaw <= 1.0) {
            val asrAltDeg = Math.toDegrees(
                atan(1.0 / (method.asrShadowFactor + tan(zenithRad)))
            )
            val aCos = rawCosHourAngle(latRad, aDecl, asrAltDeg)
            if (aCos >= -1.0 && aCos <= 1.0) {
                (aNoon + Math.toDegrees(acos(aCos)) / 15.0)
                    .coerceIn(dhuhrUtc + 0.05, maghribUtc - 0.05)
            } else {
                dhuhrUtc + 0.5 * (maghribUtc - dhuhrUtc)
            }
        } else {
            dhuhrUtc + 0.5 * (maghribUtc - dhuhrUtc)
        }

        // Isha (with continuous angle-based high-latitude protection)
        val (iNoon, iDecl) = utcNoonAt(maghribUtc + 1.5)
        val iCos = rawCosHourAngle(latRad, iDecl, -method.ishaAngleDeg)
        val maxIshaPortion = nightHours * (method.ishaAngleDeg / 60.0)
        val ishaUtc = (if (iCos >= -1.0 && iCos <= 1.0) {
            val astroIsha = iNoon + Math.toDegrees(acos(iCos)) / 15.0
            val diff = astroIsha - maghribUtc
            if (diff > maxIshaPortion && abs(location.latitudeDeg) > 45.0) {
                maghribUtc + maxIshaPortion
            } else {
                astroIsha
            }
        } else {
            maghribUtc + maxIshaPortion
        }).coerceAtLeast(maghribUtc + 0.05)

        val utcHours = doubleArrayOf(fajrUtc, dhuhrUtc, asrUtc, maghribUtc, ishaUtc)
        val instants = LongArray(5) { idx ->
            utcMidnightMs + (utcHours[idx] * MS_PER_HOUR).toLong()
        }
        val formatted = Array(5) { idx ->
            formatInstant(instants[idx], zone)
        }
        return PrayerDay(date = date, instants = instants, formattedTimes = formatted)
    }

    fun formatInstant(epochMs: Long, zone: ZoneId): String {
        // Round to nearest minute for clean prayer timetable display
        val roundedMs = ((epochMs + 30_000L) / 60_000L) * 60_000L
        val zdt = Instant.ofEpochMilli(roundedMs).atZone(zone)
        return String.format(Locale.US, "%02d:%02d", zdt.hour, zdt.minute)
    }

    fun formatLocalMinutes(minutesOfDay: Double): String {
        val totalMin = (minutesOfDay + 0.5).toInt()
        val h = ((totalMin / 60) % 24 + 24) % 24
        val m = ((totalMin % 60) + 60) % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = Math.toRadians(((357.529 + 0.98560028 * d) % 360.0 + 360.0) % 360.0)
        val q = ((280.459 + 0.98564736 * d) % 360.0 + 360.0) % 360.0
        val l = Math.toRadians(((q + 1.915 * sin(g) + 0.020 * sin(2.0 * g)) % 360.0 + 360.0) % 360.0)
        val e = Math.toRadians(23.439 - 0.00000036 * d)
        val ra = ((Math.toDegrees(atan2(cos(e) * sin(l), cos(l))) / 15.0) % 24.0 + 24.0) % 24.0
        val decl = asin(sin(e) * sin(l))
        var eqt = q / 15.0 - ra
        if (eqt > 12.0) eqt -= 24.0
        if (eqt < -12.0) eqt += 24.0
        return Pair(decl, eqt)
    }

    private fun rawCosHourAngle(latRad: Double, decl: Double, altDeg: Double): Double {
        val altRad = Math.toRadians(altDeg)
        val denom = cos(latRad) * cos(decl)
        if (abs(denom) < 1e-9) return 2.0
        return (sin(altRad) - sin(latRad) * sin(decl)) / denom
    }
}
