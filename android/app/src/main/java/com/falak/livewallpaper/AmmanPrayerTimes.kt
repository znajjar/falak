package com.falak.livewallpaper

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline astronomical Islamic prayer time calculator for Amman, Jordan
 * (31.9539° N, 35.9106° E, Asia/Amman UTC+3, Jordan Ministry of Awqaf: Fajr 18°, Isha 18°,
 * Standard/Shafi'i Asr shadow = 1x).
 *
 * Computes consecutive daily prayer instants (yesterday, today, tomorrow) so each of the
 * five planets completes one continuous 360° orbit per ~24h prayer cycle with zero midnight
 * jump, crossing the center meridian (angle = 0 rad) at exact prayer time.
 */
object AmmanPrayerTimes {

    const val CITY_NAME = "Amman"
    private const val LAT_DEG = 31.9539
    private const val LON_DEG = 35.9106
    private const val FAJR_ANGLE_DEG = 18.0
    private const val ISHA_ANGLE_DEG = 18.0
    private const val SUNSET_ALT_DEG = -0.8333
    private const val ASR_SHADOW_FACTOR = 1.0

    val AMMAN_ZONE: ZoneId = ZoneId.of("Asia/Amman")
    val PRAYER_NAMES = arrayOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    data class PrayerDay(
        val date: LocalDate,
        val instants: LongArray,
        val hoursLocal: DoubleArray
    )

    data class OrbitalState(
        val anglesRad: FloatArray,
        val today: PrayerDay,
        val nextPrayerIndex: Int,
        val minutesToNext: Long,
        val localMinutesOfDay: Double
    )

    fun computeState(nowEpochMs: Long = System.currentTimeMillis()): OrbitalState {
        val instant = Instant.ofEpochMilli(nowEpochMs)
        val zdt = instant.atZone(AMMAN_ZONE)
        val date = zdt.toLocalDate()

        val prev = computePrayerDay(date.minusDays(1))
        val today = computePrayerDay(date)
        val next = computePrayerDay(date.plusDays(1))

        val angles = FloatArray(5)
        for (i in 0 until 5) {
            val t0: Long
            val t1: Long
            if (nowEpochMs < today.instants[i]) {
                t0 = prev.instants[i]
                t1 = today.instants[i]
            } else {
                t0 = today.instants[i]
                t1 = next.instants[i]
            }
            val span = (t1 - t0).coerceAtLeast(1L).toDouble()
            var phase = (nowEpochMs - t0).toDouble() / span
            phase = ((phase % 1.0) + 1.0) % 1.0
            if (phase >= 0.5) phase -= 1.0
            angles[i] = (phase * 2.0 * PI).toFloat()
        }

        var nextIdx = -1
        var nextMs = Long.MAX_VALUE
        for (i in 0 until 5) {
            if (today.instants[i] >= nowEpochMs) {
                nextIdx = i
                nextMs = today.instants[i]
                break
            }
        }
        if (nextIdx == -1) {
            nextIdx = 0
            nextMs = next.instants[0]
        }
        val minutesToNext = ((nextMs - nowEpochMs).coerceAtLeast(0L) + 30_000L) / 60_000L
        val localMin = zdt.hour * 60.0 + zdt.minute + zdt.second / 60.0

        return OrbitalState(
            anglesRad = angles,
            today = today,
            nextPrayerIndex = nextIdx,
            minutesToNext = minutesToNext,
            localMinutesOfDay = localMin
        )
    }

    fun computeStateForLocalMinutes(date: LocalDate, minutesOfDay: Double): OrbitalState {
        val midnightMs = date.atStartOfDay(AMMAN_ZONE).toInstant().toEpochMilli()
        val epochMs = midnightMs + (minutesOfDay * 60_000.0).toLong()
        return computeState(epochMs)
    }

    fun computePrayerDay(date: LocalDate): PrayerDay {
        val midnightZdt = date.atStartOfDay(AMMAN_ZONE)
        val tzHours = midnightZdt.offset.totalSeconds / 3600.0
        val midnightMs = midnightZdt.toInstant().toEpochMilli()

        val jd0 = julianDay(date.year, date.monthValue, date.dayOfMonth) - LON_DEG / 360.0
        val latRad = Math.toRadians(LAT_DEG)

        fun evalAt(approxHour: Double, block: (noon: Double, decl: Double) -> Double): Double {
            val (decl, eqt) = sunPosition(jd0 + approxHour / 24.0)
            val noon = 12.0 + tzHours - LON_DEG / 15.0 - eqt
            return block(noon, decl)
        }

        val fajr = evalAt(5.0) { noon, decl ->
            noon - hourAngleForAlt(latRad, decl, -FAJR_ANGLE_DEG)
        }
        val dhuhr = evalAt(12.3) { noon, _ -> noon }
        val asr = evalAt(15.3) { noon, decl ->
            val altDeg = Math.toDegrees(atan(1.0 / (ASR_SHADOW_FACTOR + tan(abs(latRad - decl)))))
            noon + hourAngleForAlt(latRad, decl, altDeg)
        }
        val maghrib = evalAt(17.5) { noon, decl ->
            noon + hourAngleForAlt(latRad, decl, SUNSET_ALT_DEG)
        }
        val isha = evalAt(19.0) { noon, decl ->
            noon + hourAngleForAlt(latRad, decl, -ISHA_ANGLE_DEG)
        }

        val hours = doubleArrayOf(fajr, dhuhr, asr, maghrib, isha)
        val instants = LongArray(5) { idx ->
            midnightMs + (hours[idx] * 3_600_000.0).toLong()
        }
        return PrayerDay(date = date, instants = instants, hoursLocal = hours)
    }

    fun formatHour(hour: Double): String {
        val totalMin = ((hour * 60.0) + 0.5).toInt()
        val h = ((totalMin / 60) % 24 + 24) % 24
        val m = ((totalMin % 60) + 60) % 60
        return String.format("%02d:%02d", h, m)
    }

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = y / 100
        val b = 2 - a + a / 4
        return (365.25 * (y + 4716)).toInt() +
            (30.6001 * (m + 1)).toInt() +
            day + b - 1524.5
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

    private fun hourAngleForAlt(latRad: Double, decl: Double, altDeg: Double): Double {
        val altRad = Math.toRadians(altDeg)
        val cosHa = ((sin(altRad) - sin(latRad) * sin(decl)) / (cos(latRad) * cos(decl)))
            .coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosHa)) / 15.0
    }
}
