package com.falak.livewallpaper

import kotlin.math.PI

/**
 * Calibrated responsive celestial scene parameters for Falak across any vertical phone screen
 * size and aspect ratio, optimized for LTPO OLED displays.
 */
data class FalakConfig(
    // Camera positioned below the orbital plane looking upward across the 5 prayer arches
    val camPosX: Float = 0.0f,
    val camPosY: Float = -13.0f,
    val camPosZ: Float = 31.5f,
    val camPitchDeg: Float = 53.03f,
    val camYawDeg: Float = 0.0f,
    val fovYDeg: Float = 58.0f,

    // Strictly the 5 active prayer orbits
    val ringWidthPxAt1024: Float = 0.92f,
    val ringBrightness: Float = 1.0f,

    // Five prayer planets: [Fajr, Dhuhr, Asr, Maghrib, Isha]
    val planetOrbitRs: FloatArray = floatArrayOf(13.5f, 16.8f, 20.1f, 23.4f, 26.7f),
    val planetRadii: FloatArray = floatArrayOf(0.50f, 0.58f, 0.54f, 0.60f, 0.55f),

    // Planet shading
    val planetDiffuse: Float = 0.96f,
    val terminatorWrap: Float = 0.72f,
    val rimStrength: Float = 0.14f,

    // Half-screen-width rising bottom star (35% visible above bottom edge) with 1.5x corona glow
    val sunCoreR: Float = 0.25f,
    val sunGlow: Float = 1.5f,

    // Native 1x1 OLED micro-dither & razor-sharp pinpoint starfield
    val grainStrength: Float = 0.55f,
    val starfieldStrength: Float = 1.0f,

    // Adaptive LTPO refresh: 1 Hz idle outside prayer time, smooth 30 FPS during prayer sprinkle
    val refreshIntervalMs: Long = 1000L,
    val prayerRefreshIntervalMs: Long = 33L,
    val prayerSprinkleAngleRad: Float = 0.11f // ~25 min smooth anticipation + fade-out window
) {
    val camPitchRad: Float get() = (camPitchDeg * PI / 180.0).toFloat()
    val camYawRad: Float get() = (camYawDeg * PI / 180.0).toFloat()
    val fovYRad: Float get() = (fovYDeg * PI / 180.0).toFloat()
}
