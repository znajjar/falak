package com.falak.livewallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.falak.livewallpaper.ui.FalakScreen
import com.falak.livewallpaper.ui.FalakUiState
import com.falak.livewallpaper.ui.theme.FalakTheme
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/**
 * Material Design 3 live preview and configuration activity for Falak.
 * Displays the OpenGL ES 3.0 celestial wallpaper minimized upwards in its native
 * portrait proportions above the M3 controls, expanding smoothly into full screen when tapped.
 */
class MainActivity : ComponentActivity() {

    private var glThread: FalakGlThread? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentLocation: PrayerLocation = WorldCities.DEFAULT

    @Volatile
    private var currentMethod: PrayerMethod = PrayerMethod.DEFAULT

    @Volatile
    private var currentOrbitMode: OrbitMode = OrbitMode.DEFAULT

    @Volatile
    private var currentAspect: Float = 0.45f

    @Volatile
    private var scrubEpochMs: Long? = null

    @Volatile
    private var currentStarfield: FalakPreferences.StarfieldSettings =
        FalakPreferences.StarfieldSettings()

    private var uiState by mutableStateOf<FalakUiState?>(null)
    private var isFullScreen by mutableStateOf(false)

    private val uiTicker = object : Runnable {
        override fun run() {
            refreshUiState()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val saved = FalakPreferences.load(this)
        currentLocation = saved.location
        currentMethod = saved.method
        currentOrbitMode = saved.orbitMode
        currentStarfield = saved.starfield
        currentAspect = resources.displayMetrics.widthPixels.toFloat() /
            resources.displayMetrics.heightPixels.coerceAtLeast(1).toFloat()

        glThread = FalakGlThread(
            context = applicationContext,
            stateProvider = { aspect -> currentOrbitalState(aspect) },
            starfieldProvider = { currentStarfield }
        ).apply { start() }

        refreshUiState()

        setContent {
            FalakTheme {
                val snapshot = uiState ?: return@FalakTheme
                FalakScreen(
                    uiState = snapshot,
                    isFullScreen = isFullScreen,
                    onFullScreenChange = { fullScreen ->
                        isFullScreen = fullScreen
                        applySystemBars(fullScreen)
                    },
                    onSurfaceAvailable = { surfaceTexture, w, h ->
                        if (w > 0 && h > 0) {
                            currentAspect = w.toFloat() / h.coerceAtLeast(1).toFloat()
                            glThread?.onSurfaceTextureAvailable(surfaceTexture, w, h)
                            refreshUiState()
                        }
                    },
                    onSurfaceSizeChanged = { surfaceTexture, w, h ->
                        if (w > 0 && h > 0) {
                            currentAspect = w.toFloat() / h.coerceAtLeast(1).toFloat()
                            glThread?.onSurfaceTextureSizeChanged(surfaceTexture, w, h)
                            refreshUiState()
                        }
                    },
                    onSurfaceDestroyed = {
                        glThread?.onSurfaceDestroyed()
                    },
                    onReturnToLive = {
                        scrubEpochMs = null
                        glThread?.requestRedraw()
                        refreshUiState()
                    },
                    onAlignPrayer = { prayerIndex ->
                        val today = PrayerTimesEngine.computePrayerDay(
                            currentLocation,
                            currentMethod,
                            currentLocationDate()
                        )
                        scrubEpochMs = today.instants[prayerIndex.coerceIn(0, 4)]
                        glThread?.requestRedraw()
                        refreshUiState()
                    },
                    onScrubMinutes = { minutes ->
                        val state = PrayerTimesEngine.computeStateForLocalMinutes(
                            location = currentLocation,
                            method = currentMethod,
                            date = currentLocationDate(),
                            minutesOfDay = minutes.toDouble(),
                            orbitMode = currentOrbitMode,
                            aspect = currentAspect
                        )
                        scrubEpochMs = state.nowEpochMs
                        glThread?.requestRedraw()
                        refreshUiState()
                    },
                    onSelectLocation = { location ->
                        currentLocation = location
                        scrubEpochMs = null
                        FalakPreferences.saveLocation(this@MainActivity, location)
                        glThread?.requestRedraw()
                        refreshUiState()
                    },
                    onSelectMethod = { method ->
                        currentMethod = method
                        FalakPreferences.saveMethod(this@MainActivity, method)
                        glThread?.requestRedraw()
                        refreshUiState()
                    },
                    onSelectOrbitMode = { mode ->
                        currentOrbitMode = mode
                        FalakPreferences.saveOrbitMode(this@MainActivity, mode)
                        glThread?.requestRedraw()
                        refreshUiState()
                    },
                    onToggleGlitterPreview = {
                        val aligned = currentOrbitalState().prayerActivity > 0.25f
                        if (aligned && scrubEpochMs != null) {
                            scrubEpochMs = null
                        } else {
                            alignForGlitterPreview()
                        }
                        glThread?.requestRedraw()
                        refreshUiState()
                    },
                    onResetStarfield = {
                        updateStarfield(FalakPreferences.StarfieldSettings())
                    },
                    onStarSizeChange = { size ->
                        updateStarfield(currentStarfield.copy(sizeScale = size))
                    },
                    onStarDensityChange = { density ->
                        updateStarfield(currentStarfield.copy(densityScale = density))
                    },
                    onGlitterSpeedChange = { speed ->
                        if (speed > 0.005f && currentOrbitalState().prayerActivity <= 0.25f) {
                            alignForGlitterPreview()
                        }
                        updateStarfield(currentStarfield.copy(glitterSpeed = speed))
                    },
                    onApplyLiveWallpaper = {
                        openLiveWallpaperPicker()
                    }
                )
            }
        }

        configureEdgeToEdge()
    }

    override fun onResume() {
        super.onResume()
        val saved = FalakPreferences.load(this)
        currentLocation = saved.location
        currentMethod = saved.method
        currentOrbitMode = saved.orbitMode
        currentStarfield = saved.starfield
        refreshUiState()
        applySystemBars(isFullScreen)
        glThread?.setVisible(true)
        glThread?.requestRedraw()
        uiHandler.post(uiTicker)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(uiTicker)
        glThread?.setVisible(false)
        super.onPause()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(uiTicker)
        glThread?.stopAndRelease()
        glThread = null
        super.onDestroy()
    }

    private fun currentLocationDate(): LocalDate {
        return Instant.now().atZone(currentLocation.resolveZone()).toLocalDate()
    }

    private fun currentOrbitalState(aspect: Float = currentAspect): PrayerTimesEngine.OrbitalState {
        val epoch = scrubEpochMs ?: System.currentTimeMillis()
        return PrayerTimesEngine.computeState(
            location = currentLocation,
            method = currentMethod,
            nowEpochMs = epoch,
            orbitMode = currentOrbitMode,
            aspect = aspect
        )
    }

    private fun refreshUiState() {
        val state = currentOrbitalState()
        val scrubMs = scrubEpochMs
        val alignedIdx = if (scrubMs != null) {
            var bestIdx: Int? = null
            var bestDelta = 6L * 60_000L
            for (i in 0 until 5) {
                val delta = abs(scrubMs - state.today.instants[i])
                if (delta <= bestDelta) {
                    bestDelta = delta
                    bestIdx = i
                }
            }
            bestIdx
        } else {
            null
        }

        uiState = FalakUiState(
            location = currentLocation,
            method = currentMethod,
            orbitMode = currentOrbitMode,
            starfield = currentStarfield,
            orbitalState = state,
            isLive = scrubMs == null,
            alignedPrayerIndex = alignedIdx
        )
    }

    private fun updateStarfield(updated: FalakPreferences.StarfieldSettings) {
        currentStarfield = updated
        FalakPreferences.saveStarfield(this, updated)
        glThread?.requestRedraw()
        refreshUiState()
    }

    private fun alignForGlitterPreview() {
        val state = currentOrbitalState()
        val targetIdx = state.nextPrayerIndex.coerceIn(0, 4)
        val today = PrayerTimesEngine.computePrayerDay(
            currentLocation,
            currentMethod,
            currentLocationDate()
        )
        scrubEpochMs = today.instants[targetIdx]
        glThread?.requestRedraw()
    }

    private fun openLiveWallpaperPicker() {
        val directIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this@MainActivity, FalakWallpaperService::class.java)
            )
        }
        try {
            startActivity(directIntent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
                } catch (_: Exception) {
                    // Device has no wallpaper picker activity available.
                }
            }
        }
    }

    private fun configureEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applySystemBars(isFullScreen)
    }

    @Suppress("DEPRECATION")
    private fun applySystemBars(hideForFullScreen: Boolean) {
        val decor = window?.decorView ?: return
        val apply = {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val controller = WindowCompat.getInsetsController(window, decor)
                    controller.isAppearanceLightStatusBars = false
                    controller.isAppearanceLightNavigationBars = false
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (hideForFullScreen) {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                } else {
                    decor.systemUiVisibility = if (hideForFullScreen) {
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    } else {
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    }
                }
            } catch (_: Exception) {
                // Guard against OEM PhoneWindow insets controller quirks (e.g. Samsung One UI).
            }
        }
        apply()
        decor.post { apply() }
    }
}
