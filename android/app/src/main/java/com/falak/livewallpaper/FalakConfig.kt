package com.falak.livewallpaper

import kotlin.math.PI

/**
 * Calibrated celestial scene parameters for Falak on Google Pixel 10 Pro (1280 x 2856, 20:9).
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

    // Half-screen-width rising bottom star (35% visible above bottom edge)
    val sunCoreR: Float = 0.25f,
    val sunGlow: Float = 1.0f,

    // Deep space grain & distant pinpoint starfield
    val grainStrength: Float = 0.65f,
    val starfieldStrength: Float = 1.0f,

    // 1 Hz LTPO-friendly refresh while visible
    val refreshIntervalMs: Long = 1000L
) {
    val camPitchRad: Float get() = (camPitchDeg * PI / 180.0).toFloat()
    val camYawRad: Float get() = (camYawDeg * PI / 180.0).toFloat()
    val fovYRad: Float get() = (fovYDeg * PI / 180.0).toFloat()
}
