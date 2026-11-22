package com.falak.livewallpaper

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/**
 * Manually chosen geographic location for offline prayer-time calculation.
 * Never requires Android GPS or network location permissions.
 */
data class PrayerLocation(
    val id: String,
    val name: String,
    val country: String,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val zoneId: String,
    val isCustom: Boolean = false
) {
    val displayName: String
        get() = if (country.isNotBlank()) "$name, $country" else name

    fun resolveZone(): ZoneId = try {
        ZoneId.of(zoneId)
    } catch (_: Exception) {
        ZoneId.systemDefault()
    }

    fun formatCoordinates(): String {
        val ns = if (latitudeDeg >= 0.0) "N" else "S"
        val ew = if (longitudeDeg >= 0.0) "E" else "W"
        return String.format(
            Locale.US,
            "%.2f°%s, %.2f°%s",
            abs(latitudeDeg), ns,
            abs(longitudeDeg), ew
        )
    }

    fun formatUtcOffset(now: Instant = Instant.now()): String {
        val totalSec = resolveZone().rules.getOffset(now).totalSeconds
        val sign = if (totalSec >= 0) "+" else "-"
        val absSec = abs(totalSec)
        val hours = absSec / 3600
        val mins = (absSec % 3600) / 60
        return if (mins == 0) {
            "UTC$sign$hours"
        } else {
            String.format(Locale.US, "UTC%s%d:%02d", sign, hours, mins)
        }
    }
}
