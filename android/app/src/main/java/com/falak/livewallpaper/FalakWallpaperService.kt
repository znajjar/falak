package com.falak.livewallpaper

import android.app.WallpaperColors
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * Android Live Wallpaper service rendering Falak's celestial worldwide prayer orbits.
 * Reacts live to manual location and calculation source updates saved in FalakPreferences.
 */
class FalakWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = FalakEngine()

    private inner class FalakEngine : Engine() {
        private var glThread: FalakGlThread? = null

        @Volatile
        private var currentSettings: FalakPreferences.Settings =
            FalakPreferences.load(applicationContext)

        private val prefsListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                currentSettings = FalakPreferences.load(applicationContext)
                glThread?.requestRedraw()
            }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            currentSettings = FalakPreferences.load(applicationContext)
            FalakPreferences.registerListener(applicationContext, prefsListener)
            glThread = FalakGlThread(
                context = applicationContext,
                stateProvider = { aspect ->
                    val s = currentSettings
                    PrayerTimesEngine.computeState(
                        location = s.location,
                        method = s.method,
                        orbitMode = s.orbitMode,
                        aspect = aspect
                    )
                },
                starfieldProvider = { currentSettings.starfield }
            ).apply { start() }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            glThread?.onSurfaceAvailable(holder, width, height)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                currentSettings = FalakPreferences.load(applicationContext)
            }
            glThread?.setVisible(visible)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            glThread?.onSurfaceDestroyed()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            FalakPreferences.unregisterListener(applicationContext, prefsListener)
            glThread?.stopAndRelease()
            glThread = null
            super.onDestroy()
        }

        override fun onComputeColors(): WallpaperColors {
            // Deep obsidian-grey Material You dark theme hints for Android lockscreen & launcher.
            val primary = Color.valueOf(0xFF0E0F12.toInt())
            val secondary = Color.valueOf(0xFF3E424D.toInt())
            val tertiary = Color.valueOf(0xFFFFF7E9.toInt())
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                WallpaperColors(primary, secondary, tertiary, WallpaperColors.HINT_SUPPORTS_DARK_THEME)
            } else {
                WallpaperColors(primary, secondary, tertiary)
            }
        }
    }
}
