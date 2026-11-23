package com.falak.livewallpaper

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceHolder

/**
 * Dedicated EGL14 + OpenGL ES 3.0 render thread shared by FalakWallpaperService and MainActivity.
 * Updates at 1 Hz while visible and sleeps at 0 Hz when hidden or screen-off.
 */
class FalakGlThread(
    private val context: Context,
    private val config: FalakConfig = FalakConfig(),
    private val stateProvider: (Float) -> PrayerTimesEngine.OrbitalState = { aspect ->
        val settings = FalakPreferences.load(context)
        PrayerTimesEngine.computeState(
            location = settings.location,
            method = settings.method,
            orbitMode = settings.orbitMode,
            aspect = aspect,
            config = config
        )
    },
    private val starfieldProvider: () -> FalakPreferences.StarfieldSettings = {
        FalakPreferences.load(context).starfield
    },
    private val blurSpecProvider: () -> BackdropBlurSpec = { BackdropBlurSpec() }
) : Thread("FalakGLThread") {

    private val lock = Object()

    private var running = true
    private var visible = false
    private var nativeWindow: Any? = null
    private var surfaceChanged = false
    private var surfaceDestroyedPending = false
    private var width = 0
    private var height = 0
    private var immediateFrames = 0
    private var lastDrawMs = 0L
    private var prayerSprinkleActive = false

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var eglConfig: EGLConfig? = null
    private var renderer: FalakRenderer? = null

    fun onSurfaceAvailable(surfaceHolder: SurfaceHolder, w: Int, h: Int) {
        synchronized(lock) {
            nativeWindow = surfaceHolder
            width = w
            height = h
            surfaceChanged = true
            surfaceDestroyedPending = false
            immediateFrames = 3
            lock.notifyAll()
        }
    }

    fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, w: Int, h: Int) {
        synchronized(lock) {
            nativeWindow = surfaceTexture
            width = w
            height = h
            surfaceChanged = true
            surfaceDestroyedPending = false
            immediateFrames = 3
            lock.notifyAll()
        }
    }

    fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, w: Int, h: Int) {
        synchronized(lock) {
            val sameWindow = (nativeWindow === surfaceTexture)
            nativeWindow = surfaceTexture
            width = w
            height = h
            if (!sameWindow) {
                surfaceChanged = true
            }
            surfaceDestroyedPending = false
            immediateFrames = maxOf(immediateFrames, 3)
            lock.notifyAll()
        }
    }

    fun onSurfaceDestroyed() {
        synchronized(lock) {
            nativeWindow = null
            surfaceDestroyedPending = true
            lock.notifyAll()
            var attempts = 0
            while (surfaceDestroyedPending && running && isAlive && attempts < 15) {
                attempts++
                try {
                    lock.wait(100)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    fun setVisible(isVisible: Boolean) {
        synchronized(lock) {
            visible = isVisible
            if (isVisible && nativeWindow != null) {
                immediateFrames = maxOf(immediateFrames, 2)
            }
            lock.notifyAll()
        }
    }

    fun requestRedraw() {
        synchronized(lock) {
            immediateFrames = maxOf(immediateFrames, 1)
            lock.notifyAll()
        }
    }

    fun stopAndRelease() {
        synchronized(lock) {
            running = false
            nativeWindow = null
            lock.notifyAll()
        }
        try {
            join(1500)
        } catch (_: InterruptedException) {
            interrupt()
        }
    }

    override fun run() {
        var lastPhaseMs = SystemClock.uptimeMillis()
        var glitterPhaseSec = 0.0
        var pulseTimeSec = 0.0
        try {
            if (!initEglDisplayAndContext()) {
                Log.e(TAG, "Failed to initialize EGL14 / OpenGL ES 3.0")
                return
            }

            while (true) {
                var activeWindow: Any? = null
                var w = 0
                var h = 0
                var needRecreate = false
                var needDestroySurface = false
                var shouldDraw = false
                var shouldExit = false

                synchronized(lock) {
                    while (running && !surfaceDestroyedPending) {
                        val ready = visible && nativeWindow != null && width > 0 && height > 0
                        if (!ready) {
                            try {
                                lock.wait()
                            } catch (_: InterruptedException) {
                                break
                            }
                            continue
                        }

                        if (surfaceChanged || immediateFrames > 0) {
                            break
                        }

                        val nowMs = SystemClock.uptimeMillis()
                        val elapsed = nowMs - lastDrawMs
                        val interval = if (prayerSprinkleActive) {
                            config.prayerRefreshIntervalMs
                        } else {
                            config.refreshIntervalMs
                        }
                        val waitMs = interval - elapsed
                        if (waitMs <= 0L) {
                            break
                        }

                        try {
                            lock.wait(waitMs)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }

                    if (!running) {
                        shouldExit = true
                    } else {
                        needDestroySurface = surfaceDestroyedPending
                        activeWindow = nativeWindow
                        w = width
                        h = height
                        needRecreate = surfaceChanged
                        surfaceChanged = false
                        shouldDraw = visible && activeWindow != null && w > 0 && h > 0
                        if (immediateFrames > 0) immediateFrames--
                    }
                }

                if (shouldExit) break

                if (needDestroySurface) {
                    destroyEglSurface()
                    synchronized(lock) {
                        surfaceDestroyedPending = false
                        lock.notifyAll()
                    }
                    continue
                }

                if (needRecreate || eglSurface == EGL14.EGL_NO_SURFACE) {
                    val targetWindow = activeWindow ?: continue
                    if (!recreateEglSurface(targetWindow)) {
                        continue
                    }
                    if (renderer == null) {
                        val r = FalakRenderer(context, config)
                        if (!r.init()) {
                            Log.e(TAG, "FalakRenderer initialization failed")
                            return
                        }
                        renderer = r
                    }
                }

                if (shouldDraw && eglSurface != EGL14.EGL_NO_SURFACE) {
                    val nowMs = SystemClock.uptimeMillis()
                    val dtSec = ((nowMs - lastPhaseMs).coerceIn(0L, 100L)) * 0.001
                    lastPhaseMs = nowMs
                    lastDrawMs = nowMs

                    val aspect = w.toFloat() / h.coerceAtLeast(1).toFloat()
                    val orbitalState = stateProvider(aspect)
                    val starfield = starfieldProvider()
                    val isNearPrayer = orbitalState.isNearPrayerWindow
                    if (isNearPrayer) {
                        pulseTimeSec = (pulseTimeSec + dtSec) % 100_000.0
                        if (starfield.glitterSpeed > 0.001f && starfield.densityScale > 0.001f) {
                            glitterPhaseSec = (glitterPhaseSec + dtSec * starfield.glitterSpeed.toDouble()) % 100_000.0
                        }
                    }
                    synchronized(lock) {
                        prayerSprinkleActive = isNearPrayer
                    }
                    renderer?.draw(
                        width = w,
                        height = h,
                        timeSeconds = glitterPhaseSec.toFloat(),
                        pulseTimeSeconds = pulseTimeSec.toFloat(),
                        planetAnglesRad = orbitalState.anglesRad,
                        starfield = starfield,
                        prayerActivity = orbitalState.prayerActivity,
                        blurSpec = blurSpecProvider()
                    )
                    if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                        val err = EGL14.eglGetError()
                        if (err == EGL14.EGL_BAD_SURFACE || err == EGL14.EGL_CONTEXT_LOST) {
                            destroyEglSurface()
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected GL render thread error", t)
        } finally {
            releaseAll()
        }
    }

    private fun initEglDisplayAndContext(): Boolean {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val renderableTypes = intArrayOf(EGL_OPENGL_ES3_BIT_KHR, EGL14.EGL_OPENGL_ES2_BIT)
        var chosen: EGLConfig? = null
        for (renderable in renderableTypes) {
            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, renderable,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE
            )
            if (EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) &&
                numConfigs[0] > 0 && configs[0] != null
            ) {
                chosen = configs[0]
                break
            }
        }
        if (chosen == null) return false
        eglConfig = chosen

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0
        )
        return eglContext != EGL14.EGL_NO_CONTEXT
    }

    private fun recreateEglSurface(window: Any): Boolean {
        destroyEglSurface()
        if (eglConfig == null) return false

        val target: Any = when (window) {
            is SurfaceHolder -> {
                val s = window.surface
                if (s == null || !s.isValid) return false
                s
            }
            is SurfaceTexture -> {
                if (window.isReleased) return false
                window
            }
            else -> return false
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, eglConfig, target, surfaceAttribs, 0
        )
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreateWindowSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            return false
        }
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            Log.e(TAG, "eglMakeCurrent failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            destroyEglSurface()
            return false
        }
        EGL14.eglSwapInterval(eglDisplay, 1)
        return true
    }

    private fun destroyEglSurface() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
        }
    }

    private fun releaseAll() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT) {
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            }
            renderer?.release()
            renderer = null
        }
        destroyEglSurface()
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }

    companion object {
        private const val TAG = "FalakGlThread"
        private const val EGL_OPENGL_ES3_BIT_KHR = 0x0040
    }
}
