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
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tan

/**
 * Worldwide offline astronomical Islamic prayer-time calculator built on the high-precision
 * Jean Meeus / NOAA solar ephemeris. Supports arbitrary latitude, longitude, IANA timezones
 * (including DST transitions and International Date Line offsets), selectable calculation
 * sources (Jordan Ministry of Awqaf, MWL), continuous angle-based high-latitude handling,
 * and dual operating modes:
 *
 * - [OrbitMode.PRAYER_WINDOW] (default): Continuous prayer-to-prayer relay where each planet
 *   crosses dead center at exact prayer time—Fajr hands off to Dhuhr at Sunrise, and all other
 *   consecutive prayers (Dhuhr→Asr→Maghrib→Isha→Fajr) hand off cleanly at the midpoint between them.
 * - [OrbitMode.ORBIT_24H]: Continuous 360° ~24h astronomical cycle with zero midnight jump.
 */
object PrayerTimesEngine {

    private const val STD_SUNSET_ALT_DEG = -0.8333
    private const val MS_PER_MIN = 60_000L
    private const val MS_PER_HOUR = 3_600_000.0
    private const val MS_PER_DAY = 86_400_000.0
    private const val UNIX_EPOCH_JD = 2440587.5
    private const val DEFAULT_ASPECT = 0.45f
    private const val PRAYER_ACTIVITY_SIGMA_MIN = 10.5
    private const val PRAYER_ANIMATION_WINDOW_MIN = 25.0

    val PRAYER_NAMES = arrayOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    data class PrayerDay(
        val date: LocalDate,
        val instants: LongArray,
        val formattedTimes: Array<String>,
        val sunriseInstant: Long,
        val sunriseFormatted: String
    )

    data class OrbitalState(
        val location: PrayerLocation,
        val method: PrayerMethod,
        val orbitMode: OrbitMode,
        val anglesRad: FloatArray,
        val today: PrayerDay,
        val nextPrayerIndex: Int,
        val minutesToNext: Long,
        val localMinutesOfDay: Double,
        val nowEpochMs: Long,
        val prayerActivity: Float,
        val isNearPrayerWindow: Boolean
    )

    @JvmOverloads
    fun computeState(
        location: PrayerLocation,
        method: PrayerMethod,
        nowEpochMs: Long = System.currentTimeMillis(),
        orbitMode: OrbitMode = OrbitMode.DEFAULT,
        aspect: Float = DEFAULT_ASPECT,
        config: FalakConfig = FalakConfig()
    ): OrbitalState {
        val zone = location.resolveZone()
        val zdt = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
        val date = zdt.toLocalDate()

        val prev = computePrayerDay(location, method, date.minusDays(1))
        val today = computePrayerDay(location, method, date)
        val next = computePrayerDay(location, method, date.plusDays(1))

        var prev2: PrayerDay? = null
        var next2: PrayerDay? = null

        val angles = when (orbitMode) {
            OrbitMode.ORBIT_24H -> {
                FloatArray(5) { i ->
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
                    (phase * 2.0 * PI).toFloat()
                }
            }

            OrbitMode.PRAYER_WINDOW -> {
                val p2 = prev2 ?: computePrayerDay(location, method, date.minusDays(2)).also { prev2 = it }
                val n2 = next2 ?: computePrayerDay(location, method, date.plusDays(2)).also { next2 = it }
                val edgeAngles = computeEdgeAnglesRad(aspect, config)
                computePrayerWindowAngles(
                    nowEpochMs = nowEpochMs,
                    days = arrayOf(p2, prev, today, next, n2),
                    edgeAnglesRad = edgeAngles
                )
            }
        }

        // Find the earliest upcoming prayer instant and nearest prayer time (for exact time-based
        // prayer glow/glitter/pulsation and LTPO OLED 30 FPS window across both operating modes).
        var nextIdx = 0
        var nextMs = Long.MAX_VALUE
        var minDeltaMs = Long.MAX_VALUE
        val candidateDays = arrayOf(prev, today, next)
        for (day in candidateDays) {
            for (i in 0 until 5) {
                val t = day.instants[i]
                val delta = abs(nowEpochMs - t)
                if (delta < minDeltaMs) {
                    minDeltaMs = delta
                }
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
            val delta = abs(nowEpochMs - nextMs)
            if (delta < minDeltaMs) minDeltaMs = delta
        }

        val minDeltaMin = minDeltaMs.toDouble() / MS_PER_MIN.toDouble()
        val norm = minDeltaMin / PRAYER_ACTIVITY_SIGMA_MIN
        val prayerActivity = exp(-(norm * norm)).toFloat().coerceIn(0f, 1f)
        val isNearPrayer = minDeltaMin <= PRAYER_ANIMATION_WINDOW_MIN

        val minutesToNext = ((nextMs - nowEpochMs).coerceAtLeast(0L) + 30_000L) / MS_PER_MIN
        val localMin = zdt.hour * 60.0 + zdt.minute + zdt.second / 60.0

        return OrbitalState(
            location = location,
            method = method,
            orbitMode = orbitMode,
            anglesRad = angles,
            today = today,
            nextPrayerIndex = nextIdx,
            minutesToNext = minutesToNext,
            localMinutesOfDay = localMin,
            nowEpochMs = nowEpochMs,
            prayerActivity = prayerActivity,
            isNearPrayerWindow = isNearPrayer
        )
    }

    @JvmOverloads
    fun computeStateForLocalMinutes(
        location: PrayerLocation,
        method: PrayerMethod,
        date: LocalDate,
        minutesOfDay: Double,
        orbitMode: OrbitMode = OrbitMode.DEFAULT,
        aspect: Float = DEFAULT_ASPECT,
        config: FalakConfig = FalakConfig()
    ): OrbitalState {
        val zone = location.resolveZone()
        val clamped = minutesOfDay.coerceIn(0.0, 1439.99)
        val totalSec = (clamped * 60.0).toInt()
        val h = (totalSec / 3600).coerceIn(0, 23)
        val m = ((totalSec % 3600) / 60).coerceIn(0, 59)
        val s = (totalSec % 60).coerceIn(0, 59)
        val epochMs = date.atTime(h, m, s).atZone(zone).toInstant().toEpochMilli()
        return computeState(location, method, epochMs, orbitMode, aspect, config)
    }

    /**
     * Computes exact pixel-accurate screen-edge exit angles (rad) for each of the 5 prayer orbits
     * for any surface aspect ratio (23:9 narrow phones through 5:6 foldables), matching
     * [falak_frag.glsl]'s exact 3D perspective frustum (right and bottom view planes).
     */
    fun computeEdgeAnglesRad(aspect: Float, config: FalakConfig = FalakConfig()): FloatArray {
        val safeAspect = aspect.coerceIn(0.30f, 1.20f).toDouble()
        val halfW = 0.5 * safeAspect
        val focal = 0.5 / tan(0.5 * config.fovYRad.toDouble())
        val cp = cos(config.camPitchRad.toDouble())
        val sp = sin(config.camPitchRad.toDouble())
        val camY = config.camPosY.toDouble()
        val camZ = config.camPosZ.toDouble()

        // Camera basis (yaw = 0): right=(1,0,0), fwd=(0,sp,-cp), up=(0,cp,sp)
        // Outward unit normal of right frustum plane (ndc.x = +halfW): normalize(focal*right - halfW*fwd)
        val rNxRaw = focal
        val rNyRaw = -halfW * sp
        val rNzRaw = halfW * cp
        val rLen = kotlin.math.sqrt(rNxRaw * rNxRaw + rNyRaw * rNyRaw + rNzRaw * rNzRaw)
        val rNx = rNxRaw / rLen
        val rNy = rNyRaw / rLen
        val rNz = rNzRaw / rLen

        // Outward unit normal of bottom frustum plane (ndc.y = -0.5): normalize(-focal*up - 0.5*fwd)
        val bNyRaw = -focal * cp - 0.5 * sp
        val bNzRaw = -focal * sp + 0.5 * cp
        val bLen = kotlin.math.sqrt(bNyRaw * bNyRaw + bNzRaw * bNzRaw)
        val bNy = bNyRaw / bLen
        val bNz = bNzRaw / bLen

        return FloatArray(5) { idx ->
            val orbitR = config.planetOrbitRs[idx].toDouble()
            val padR = config.planetRadii[idx].toDouble() * 1.08
            var lo = 0.0
            var hi = PI * 0.92
            for (step in 0 until 28) {
                val mid = 0.5 * (lo + hi)
                val px = orbitR * sin(mid)
                val pz = orbitR * cos(mid)
                val vx = px
                val vy = -camY
                val vz = pz - camZ
                val zCam = vy * sp - vz * cp
                val dRight = vx * rNx + vy * rNy + vz * rNz
                val dBottom = vy * bNy + vz * bNz
                if (zCam > 0.01 && dRight < padR && dBottom < padR) {
                    lo = mid
                } else {
                    hi = mid
                }
            }
            hi.toFloat()
        }
    }

    /**
     * Prayer-Window orbital mapping (continuous prayer-to-prayer relay; planets glide at
     * steady speed before athan and steady speed after athan to match each half-window):
     * - Fajr (0): enters at midpoint(prev.Isha, Fajr), centered at Fajr, hands off to Dhuhr at Sunrise.
     * - Dhuhr (1): enters at Sunrise, centered at Dhuhr, hands off to Asr at midpoint(Dhuhr, Asr).
     * - Asr (2): enters at midpoint(Dhuhr, Asr), centered at Asr, hands off at midpoint(Asr, Maghrib).
     * - Maghrib (3): enters at midpoint(Asr, Maghrib), centered at Maghrib, hands off at midpoint(Maghrib, Isha).
     * - Isha (4): enters at midpoint(Maghrib, Isha), centered at Isha, hands off to next Fajr at midpoint(Isha, NextFajr).
     * A 4-minute off-screen wake taper beyond ±edge keeps orbital trails silky-smooth with zero pop
     * while the planet sphere itself enters at exact enterMs and exits at exact exitMs.
     */
    private fun computePrayerWindowAngles(
        nowEpochMs: Long,
        days: Array<PrayerDay>, // [prev2, prev, today, next, next2]
        edgeAnglesRad: FloatArray
    ): FloatArray {
        val wakeFadeMs = 4L * MS_PER_MIN
        val wakeFadeRad = 0.55
        val angles = FloatArray(5) { PI.toFloat() }
        for (i in 0 until 5) {
            val edge = edgeAnglesRad[i].toDouble()
            for (dIdx in 1..3) {
                val dPrev = days[dIdx - 1]
                val d = days[dIdx]
                val dNext = days[dIdx + 1]
                val pMs = d.instants[i]
                val enterMs: Long
                val exitMs: Long
                when (i) {
                    0 -> { // Fajr: handoff from prev Isha -> disappears & hands off to Dhuhr at exact Sunrise
                        enterMs = ((dPrev.instants[4] + d.instants[0]) / 2L).coerceAtMost(pMs - MS_PER_MIN)
                        exitMs = d.sunriseInstant.coerceAtLeast(pMs + MS_PER_MIN)
                    }
                    1 -> { // Dhuhr: handoff from Fajr at exact Sunrise -> handoff to Asr at midpoint
                        enterMs = d.sunriseInstant.coerceAtMost(pMs - MS_PER_MIN)
                        exitMs = ((d.instants[1] + d.instants[2]) / 2L).coerceAtLeast(pMs + MS_PER_MIN)
                    }
                    2 -> { // Asr: handoff from Dhuhr -> handoff to Maghrib
                        enterMs = ((d.instants[1] + d.instants[2]) / 2L).coerceAtMost(pMs - MS_PER_MIN)
                        exitMs = ((d.instants[2] + d.instants[3]) / 2L).coerceAtLeast(pMs + MS_PER_MIN)
                    }
                    3 -> { // Maghrib: handoff from Asr -> handoff to Isha
                        enterMs = ((d.instants[2] + d.instants[3]) / 2L).coerceAtMost(pMs - MS_PER_MIN)
                        exitMs = ((d.instants[3] + d.instants[4]) / 2L).coerceAtLeast(pMs + MS_PER_MIN)
                    }
                    else -> { // Isha: handoff from Maghrib -> handoff to next Fajr at midpoint(Isha, NextFajr)
                        enterMs = ((d.instants[3] + d.instants[4]) / 2L).coerceAtMost(pMs - MS_PER_MIN)
                        exitMs = ((d.instants[4] + dNext.instants[0]) / 2L).coerceAtLeast(pMs + MS_PER_MIN)
                    }
                }
                if (nowEpochMs in enterMs..exitMs) {
                    val frac = if (nowEpochMs <= pMs) {
                        (nowEpochMs - pMs).toDouble() / (pMs - enterMs).coerceAtLeast(1L).toDouble()
                    } else {
                        (nowEpochMs - pMs).toDouble() / (exitMs - pMs).coerceAtLeast(1L).toDouble()
                    }
                    angles[i] = (frac * edge).toFloat()
                    break
                } else if (nowEpochMs in (enterMs - wakeFadeMs) until enterMs) {
                    val u = (enterMs - nowEpochMs).toDouble() / wakeFadeMs.toDouble()
                    angles[i] = (-(edge + wakeFadeRad * u)).coerceAtLeast(-PI + 0.01).toFloat()
                    break
                } else if (nowEpochMs in (exitMs + 1L)..(exitMs + wakeFadeMs)) {
                    val u = (nowEpochMs - exitMs).toDouble() / wakeFadeMs.toDouble()
                    angles[i] = (edge + wakeFadeRad * u).coerceAtMost(PI - 0.01).toFloat()
                    break
                }
            }
        }
        return angles
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
        // Anchor solar transit to local civil noon of `date` in `zone`, so DST transitions
        // and International Date Line offsets always select the exact transit belonging to `date`.
        val civilNoonUtcH = (date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() - utcMidnightMs) / MS_PER_HOUR

        fun utcNoonAt(utcHourApprox: Double): Pair<Double, Double> {
            val jd = (utcMidnightMs + utcHourApprox * MS_PER_HOUR) / MS_PER_DAY + UNIX_EPOCH_JD
            val (decl, eqt) = sunPositionMeeus(jd)
            var noonUtc = 12.0 - lonDeg / 15.0 - eqt
            noonUtc += round((civilNoonUtcH - noonUtc) / 24.0) * 24.0
            return Pair(noonUtc, decl)
        }

        // Exact Dhuhr (2-step fixed-point evaluation for sub-second precision)
        val (dhuhr0, _) = utcNoonAt(civilNoonUtcH)
        val (dhuhrUtc, _) = utcNoonAt(dhuhr0)

        // Astronomical Sunrise & Sunset (-0.8333°) for night-length & high-latitude guards
        val (srNoon, srDecl) = utcNoonAt(dhuhrUtc - 6.0)
        val srHaRaw = rawCosHourAngle(latRad, srDecl, STD_SUNSET_ALT_DEG)
        val srHa = when {
            srHaRaw < -1.0 -> 11.5 // Polar day: symbolic 1h night window around solar midnight
            srHaRaw > 1.0 -> 0.5   // Polar night: symbolic 1h day window around solar noon
            else -> Math.toDegrees(acos(srHaRaw)) / 15.0
        }
        val sunriseUtc = srNoon - srHa

        val (ssNoon, ssDecl) = utcNoonAt(dhuhrUtc + 6.0)
        val ssHaRaw = rawCosHourAngle(latRad, ssDecl, STD_SUNSET_ALT_DEG)
        val ssHa = when {
            ssHaRaw < -1.0 -> 11.5
            ssHaRaw > 1.0 -> 0.5
            else -> Math.toDegrees(acos(ssHaRaw)) / 15.0
        }
        val sunsetUtc = ssNoon + ssHa

        // Night length for continuous angle-based high-latitude adjustment
        val nightHours = (24.0 - (sunsetUtc - sunriseUtc)).coerceIn(1.0, 23.0)

        // Maghrib (uses method's depression angle: 2.12° for Jordan Awqaf, 0.8333° for MWL)
        val magAltDeg = -method.maghribAngleDeg
        val maghribUtc = if (abs(method.maghribAngleDeg - 0.8333) < 1e-4) {
            sunsetUtc
        } else {
            val (mNoon, mDecl) = utcNoonAt(sunsetUtc + 0.1)
            val mCos = rawCosHourAngle(latRad, mDecl, magAltDeg)
            val maxMagPortion = nightHours * (method.maghribAngleDeg / 60.0)
            if (mCos >= -1.0 && mCos <= 1.0 && ssHaRaw <= 1.0) {
                val astroMag = mNoon + Math.toDegrees(acos(mCos)) / 15.0
                if (astroMag - sunsetUtc > maxMagPortion && abs(location.latitudeDeg) > 45.0) {
                    sunsetUtc + maxMagPortion
                } else {
                    astroMag
                }
            } else {
                sunsetUtc + maxMagPortion.coerceAtMost(0.25)
            }
        }

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
            val diff = astroIsha - sunsetUtc
            if (diff > maxIshaPortion && abs(location.latitudeDeg) > 45.0) {
                sunsetUtc + maxIshaPortion
            } else {
                astroIsha
            }
        } else {
            sunsetUtc + maxIshaPortion
        }).coerceAtLeast(maghribUtc + 0.05)

        val utcHours = doubleArrayOf(fajrUtc, dhuhrUtc, asrUtc, maghribUtc, ishaUtc)
        val instants = LongArray(5) { idx ->
            val rawMs = utcMidnightMs + utcHours[idx] * MS_PER_HOUR
            val exactMinutes = rawMs / MS_PER_MIN.toDouble()
            val roundUp = method.roundUpDhuhrAsrIsha && (idx == 1 || idx == 2 || idx == 4)
            val roundedMin = if (roundUp) {
                ceil(exactMinutes).toLong()
            } else {
                floor(exactMinutes + 0.5).toLong()
            }
            roundedMin * MS_PER_MIN
        }
        val formatted = Array(5) { idx ->
            formatInstant(instants[idx], zone)
        }
        val sunriseRawMs = utcMidnightMs + sunriseUtc * MS_PER_HOUR
        val sunriseMin = floor(sunriseRawMs / MS_PER_MIN.toDouble() + 0.5).toLong()
        val sunriseInstant = (sunriseMin * MS_PER_MIN).coerceAtLeast(instants[0] + MS_PER_MIN)
        val sunriseFormatted = formatInstant(sunriseInstant, zone)
        return PrayerDay(
            date = date,
            instants = instants,
            formattedTimes = formatted,
            sunriseInstant = sunriseInstant,
            sunriseFormatted = sunriseFormatted
        )
    }

    fun formatInstant(epochMs: Long, zone: ZoneId): String {
        val roundedMs = ((epochMs + 30_000L) / MS_PER_MIN) * MS_PER_MIN
        val zdt = Instant.ofEpochMilli(roundedMs).atZone(zone)
        return String.format(Locale.US, "%02d:%02d", zdt.hour, zdt.minute)
    }

    fun formatLocalMinutes(minutesOfDay: Double): String {
        val totalMin = (minutesOfDay + 0.5).toInt()
        val h = ((totalMin / 60) % 24 + 24) % 24
        val m = ((totalMin % 60) + 60) % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    /**
     * High-precision solar declination (rad) and equation of time (hours)
     * from Jean Meeus, "Astronomical Algorithms" / NOAA Solar Calculations.
     */
    private fun sunPositionMeeus(jd: Double): Pair<Double, Double> {
        val t = (jd - 2451545.0) / 36525.0
        val l0Deg = ((280.46646 + t * (36000.76983 + 0.0003032 * t)) % 360.0 + 360.0) % 360.0
        val mDeg = ((357.52911 + t * (35999.05029 - 0.0001537 * t)) % 360.0 + 360.0) % 360.0
        val ecc = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mRad = Math.toRadians(mDeg)
        val cDeg = sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2.0 * mRad) * (0.019993 - 0.000101 * t) +
            sin(3.0 * mRad) * 0.000289
        val trueLongDeg = l0Deg + cDeg
        val omegaRad = Math.toRadians(125.04 - 1934.136 * t)
        val appLongRad = Math.toRadians(trueLongDeg - 0.00569 - 0.00478 * sin(omegaRad))
        val eps0Deg = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val epsRad = Math.toRadians(eps0Deg + 0.00256 * cos(omegaRad))

        val decl = asin(sin(epsRad) * sin(appLongRad))
        val tanHalfEps = tan(epsRad * 0.5)
        val y = tanHalfEps * tanHalfEps
        val l0Rad = Math.toRadians(l0Deg)
        val eqtRad = y * sin(2.0 * l0Rad) -
            2.0 * ecc * sin(mRad) +
            4.0 * ecc * y * sin(mRad) * cos(2.0 * l0Rad) -
            0.5 * y * y * sin(4.0 * l0Rad) -
            1.25 * ecc * ecc * sin(2.0 * mRad)
        val eqtHours = Math.toDegrees(eqtRad) / 15.0
        return Pair(decl, eqtHours)
    }

    private fun rawCosHourAngle(latRad: Double, decl: Double, altDeg: Double): Double {
        val altRad = Math.toRadians(altDeg)
        val denom = cos(latRad) * cos(decl)
        if (abs(denom) < 1e-9) return 2.0
        return (sin(altRad) - sin(latRad) * sin(decl)) / denom
    }
}
