package com.falak.livewallpaper

/**
 * Supported Islamic prayer-time calculation sources.
 * Currently includes Jordan Ministry of Awqaf and Muslim World League (MWL).
 */
enum class PrayerMethod(
    val id: String,
    val displayName: String,
    val shortName: String,
    val summary: String,
    val fajrAngleDeg: Double,
    val ishaAngleDeg: Double,
    val asrShadowFactor: Double = 1.0
) {
    JORDAN_AWQAF(
        id = "JORDAN_AWQAF",
        displayName = "Jordan Ministry of Awqaf",
        shortName = "Jordan Awqaf",
        summary = "Fajr 18° · Isha 18° · Standard Asr",
        fajrAngleDeg = 18.0,
        ishaAngleDeg = 18.0,
        asrShadowFactor = 1.0
    ),
    MWL(
        id = "MWL",
        displayName = "Muslim World League (MWL)",
        shortName = "MWL",
        summary = "Fajr 18° · Isha 17° · Standard Asr",
        fajrAngleDeg = 18.0,
        ishaAngleDeg = 17.0,
        asrShadowFactor = 1.0
    );

    companion object {
        val DEFAULT: PrayerMethod = JORDAN_AWQAF

        fun fromId(id: String?): PrayerMethod {
            return values().firstOrNull { it.id == id } ?: DEFAULT
        }
    }
}
