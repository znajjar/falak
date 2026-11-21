package com.falak.livewallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.time.Instant
import java.time.LocalDate

/**
 * Full-bleed OpenGL ES 3.0 live preview of Falak with Amman's daily prayer schedule,
 * tap-to-align prayer rows, 24-hour orbit scrubber, and one-tap Live Wallpaper activation.
 */
class MainActivity : Activity(), SurfaceHolder.Callback {

    private var glThread: FalakGlThread? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var scrubMinutes: Double? = null

    private lateinit var statusLabel: TextView
    private lateinit var livePill: TextView
    private lateinit var seekBar: SeekBar
    private val prayerCells = arrayOfNulls<TextView>(5)
    private var updatingSeekBar = false

    private val uiTicker = object : Runnable {
        override fun run() {
            refreshHud()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge()

        val dp = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF0C0D10.toInt())
        }

        val surfaceView = SurfaceView(this).apply {
            holder.addCallback(this@MainActivity)
        }
        root.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padH = (16 * dp).toInt()
            val padV = (12 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * dp
                setColor(0xC00D0E12.toInt())
                setStroke((1f * dp).toInt(), 0x28E6EBF5)
            }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        statusLabel = TextView(this).apply {
            setTextColor(0xEBFFF8ED.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        livePill = TextView(this).apply {
            text = "LIVE"
            setTextColor(0xFF0C0D10.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * dp
                setColor(0xEBFFF7E9.toInt())
            }
            setOnClickListener {
                scrubMinutes = null
                glThread?.requestRedraw()
                refreshHud()
            }
        }
        topRow.addView(statusLabel)
        topRow.addView(livePill)
        dock.addView(topRow)

        val prayerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * dp).toInt()
            }
        }
        for (i in 0 until 5) {
            val cell = TextView(this).apply {
                gravity = Gravity.CENTER
                setTextColor(0xB8DCE2F0.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.MONOSPACE
                setPadding((2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    val today = AmmanPrayerTimes.computePrayerDay(currentAmmanDate())
                    scrubMinutes = today.hoursLocal[i] * 60.0
                    glThread?.requestRedraw()
                    refreshHud()
                }
            }
            prayerCells[i] = cell
            prayerRow.addView(cell)
        }
        dock.addView(prayerRow)

        seekBar = SeekBar(this).apply {
            max = 1439
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * dp).toInt()
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        scrubMinutes = progress.toDouble()
                        glThread?.requestRedraw()
                        refreshHud()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        dock.addView(seekBar)

        val applyButton = Button(this).apply {
            text = getString(R.string.set_wallpaper_button)
            setTextColor(0xFFFFF8ED.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            isAllCaps = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 22f * dp
                setColor(0x28FFF7E9)
                setStroke((1f * dp).toInt(), 0x50FFF7E9)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * dp).toInt()
            }
            setOnClickListener { openLiveWallpaperPicker() }
        }
        dock.addView(applyButton)

        val dockParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ).apply {
            topMargin = (44 * dp).toInt()
            leftMargin = (14 * dp).toInt()
            rightMargin = (14 * dp).toInt()
        }
        root.addView(dock, dockParams)

        setContentView(root)

        glThread = FalakGlThread(
            context = applicationContext,
            angleProvider = { currentOrbitalState().anglesRad }
        ).apply { start() }

        refreshHud()
    }

    override fun onResume() {
        super.onResume()
        glThread?.setVisible(true)
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

    override fun surfaceCreated(holder: SurfaceHolder) {
        val rect = holder.surfaceFrame
        if (rect.width() > 0 && rect.height() > 0) {
            glThread?.onSurfaceAvailable(holder, rect.width(), rect.height())
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        glThread?.onSurfaceAvailable(holder, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        glThread?.onSurfaceDestroyed()
    }

    private fun currentAmmanDate(): LocalDate {
        return Instant.now().atZone(AmmanPrayerTimes.AMMAN_ZONE).toLocalDate()
    }

    private fun currentOrbitalState(): AmmanPrayerTimes.OrbitalState {
        val scrub = scrubMinutes
        return if (scrub != null) {
            AmmanPrayerTimes.computeStateForLocalMinutes(currentAmmanDate(), scrub)
        } else {
            AmmanPrayerTimes.computeState()
        }
    }

    private fun refreshHud() {
        val state = currentOrbitalState()
        val isLive = scrubMinutes == null
        val curTimeStr = AmmanPrayerTimes.formatHour(state.localMinutesOfDay / 60.0)
        val nextName = AmmanPrayerTimes.PRAYER_NAMES[state.nextPrayerIndex]
        val hrs = state.minutesToNext / 60
        val mins = state.minutesToNext % 60
        val countdown = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"

        statusLabel.text = "Amman $curTimeStr · $nextName in $countdown"
        livePill.alpha = if (isLive) 1.0f else 0.55f
        livePill.text = if (isLive) "LIVE" else "RESET"

        for (i in 0 until 5) {
            val label = AmmanPrayerTimes.PRAYER_NAMES[i]
            val timeStr = AmmanPrayerTimes.formatHour(state.today.hoursLocal[i])
            val isNext = (i == state.nextPrayerIndex)
            prayerCells[i]?.apply {
                text = "$label\n$timeStr"
                setTextColor(if (isNext) Color.WHITE else 0x99DCE2F0.toInt())
                typeface = Typeface.create(
                    Typeface.MONOSPACE,
                    if (isNext) Typeface.BOLD else Typeface.NORMAL
                )
            }
        }

        if (!updatingSeekBar) {
            updatingSeekBar = true
            seekBar.progress = state.localMinutesOfDay.toInt().coerceIn(0, 1439)
            updatingSeekBar = false
        }
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
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    @Suppress("DEPRECATION")
    private fun configureEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }
}
