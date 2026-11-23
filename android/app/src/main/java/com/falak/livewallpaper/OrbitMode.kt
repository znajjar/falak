package com.falak.livewallpaper

/**
 * Operating mode controlling how the five prayer planets traverse their orbital arches.
 *
 * - [PRAYER_WINDOW] (default): Planets cross the screen closely around each prayer time at
 *   steady speed—crossing dead center at athan, with Fajr exiting at Sunrise and the remaining
 *   prayers handing off cleanly at the midpoint between consecutive prayers.
 * - [ORBIT_24H]: Continuous 360° ~24-hour astronomical cycle around the rising star.
 */
enum class OrbitMode(
    val id: String,
    val displayName: String,
    val shortName: String,
    val summary: String
) {
    PRAYER_WINDOW(
        id = "PRAYER_WINDOW",
        displayName = "Prayer Window",
        shortName = "Prayer",
        summary = "Fajr exits at Sunrise · other prayers hand off at midpoints"
    ),
    ORBIT_24H(
        id = "ORBIT_24H",
        displayName = "24-Hour Orbit",
        shortName = "24h Orbit",
        summary = "Continuous 360° daily astronomical cycle"
    );

    companion object {
        val DEFAULT: OrbitMode = PRAYER_WINDOW

        fun fromId(id: String?): OrbitMode {
            return values().firstOrNull { it.id == id } ?: DEFAULT
        }
    }
}
