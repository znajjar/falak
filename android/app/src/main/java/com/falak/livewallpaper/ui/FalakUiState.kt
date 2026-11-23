package com.falak.livewallpaper.ui

import com.falak.livewallpaper.FalakPreferences
import com.falak.livewallpaper.OrbitMode
import com.falak.livewallpaper.PrayerLocation
import com.falak.livewallpaper.PrayerMethod
import com.falak.livewallpaper.PrayerTimesEngine

/**
 * Immutable UI state snapshot rendered by [FalakScreen].
 */
data class FalakUiState(
    val location: PrayerLocation,
    val method: PrayerMethod,
    val orbitMode: OrbitMode,
    val starfield: FalakPreferences.StarfieldSettings,
    val orbitalState: PrayerTimesEngine.OrbitalState,
    val isLive: Boolean,
    val alignedPrayerIndex: Int?
) {
    val isGlitterActive: Boolean
        get() = orbitalState.prayerActivity > 0.25f

    val isStarfieldModified: Boolean
        get() = kotlin.math.abs(starfield.sizeScale - FalakPreferences.DEFAULT_STAR_SIZE) > 0.005f ||
            kotlin.math.abs(starfield.densityScale - FalakPreferences.DEFAULT_STAR_DENSITY) > 0.005f ||
            kotlin.math.abs(starfield.glitterSpeed - FalakPreferences.DEFAULT_GLITTER_SPEED) > 0.005f
}

/**
 * Modal bottom sheets presented from the Material 3 configuration controls.
 */
enum class ActiveSheet {
    NONE,
    LOCATION,
    CUSTOM_COORDINATES,
    SOURCE
}
