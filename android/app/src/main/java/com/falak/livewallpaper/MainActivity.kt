package com.falak.livewallpaper

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/**
 * Full-bleed OpenGL ES 3.0 live preview & configuration activity for Falak.
 * Provides a manual worldwide location selector (zero location permissions),
 * a calculation source selector (Jordan Ministry of Awqaf & MWL), tap-to-align
 * prayer rows, a 24-hour orbit scrubber, and one-tap Live Wallpaper activation.
 */
class MainActivity : Activity(), SurfaceHolder.Callback {

    private var glThread: FalakGlThread? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentLocation: PrayerLocation = WorldCities.DEFAULT

    @Volatile
    private var currentMethod: PrayerMethod = PrayerMethod.DEFAULT

    @Volatile
    private var scrubEpochMs: Long? = null

    private lateinit var statusLabel: TextView
    private lateinit var livePill: TextView
    private lateinit var locationValueView: TextView
    private lateinit var sourceValueView: TextView
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

        val saved = FalakPreferences.load(this)
        currentLocation = saved.location
        currentMethod = saved.method

        val dp = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
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
            val padH = (14 * dp).toInt()
            val padV = (12 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            background = roundedBox(dp, 20f, 0xC60D0E12.toInt(), 0x28E6EBF5)
        }

        // Row 1: City time + next prayer countdown + LIVE/RESET pill
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        statusLabel = TextView(this).apply {
            setTextColor(COLOR_IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.2f)
            typeface = Typeface.MONOSPACE
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = (8 * dp).toInt()
            }
        }

        livePill = TextView(this).apply {
            text = "LIVE"
            setTextColor(COLOR_BG)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            background = roundedBox(dp, 12f, 0xEBFFF7E9.toInt(), 0)
            setOnClickListener {
                scrubEpochMs = null
                glThread?.requestRedraw()
                refreshHud()
            }
        }
        topRow.addView(statusLabel)
        topRow.addView(livePill)
        dock.addView(topRow)

        // Row 2: Manual Location Selector & Calculation Source Selector pills
        val selectorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (9 * dp).toInt()
            }
        }

        val (locPill, locVal) = buildSelectorPill(
            dp = dp,
            caption = "LOCATION",
            onClick = { showLocationDialog() }
        )
        locationValueView = locVal
        locPill.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f).apply {
            rightMargin = (6 * dp).toInt()
        }

        val (srcPill, srcVal) = buildSelectorPill(
            dp = dp,
            caption = "SOURCE",
            onClick = { showSourceDialog() }
        )
        sourceValueView = srcVal
        srcPill.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.95f)

        selectorRow.addView(locPill)
        selectorRow.addView(srcPill)
        dock.addView(selectorRow)

        // Row 3: Five prayer schedule cells (tap to align)
        val prayerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * dp).toInt()
            }
        }
        val screenWidthDp = resources.displayMetrics.widthPixels / dp
        val prayerTextSp = (screenWidthDp / 34f).coerceIn(9.2f, 11.2f)
        for (i in 0 until 5) {
            val cell = TextView(this).apply {
                gravity = Gravity.CENTER
                maxLines = 2
                setTextColor(0xB8DCE2F0.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, prayerTextSp)
                typeface = Typeface.MONOSPACE
                setPadding((2 * dp).toInt(), (5 * dp).toInt(), (2 * dp).toInt(), (5 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    val today = PrayerTimesEngine.computePrayerDay(
                        currentLocation,
                        currentMethod,
                        currentLocationDate()
                    )
                    scrubEpochMs = today.instants[i]
                    glThread?.requestRedraw()
                    refreshHud()
                }
            }
            prayerCells[i] = cell
            prayerRow.addView(cell)
        }
        dock.addView(prayerRow)

        // Row 4: 24-hour orbit scrub SeekBar
        seekBar = SeekBar(this).apply {
            max = 1439
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (3 * dp).toInt()
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val state = PrayerTimesEngine.computeStateForLocalMinutes(
                            currentLocation,
                            currentMethod,
                            currentLocationDate(),
                            progress.toDouble()
                        )
                        scrubEpochMs = state.nowEpochMs
                        glThread?.requestRedraw()
                        refreshHud()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        dock.addView(seekBar)

        // Row 5: Set as Live Wallpaper button
        val applyButton = Button(this).apply {
            text = getString(R.string.set_wallpaper_button)
            setTextColor(COLOR_IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            isAllCaps = false
            background = roundedBox(dp, 22f, 0x28FFF7E9, 0x50FFF7E9)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * dp).toInt()
            }
            setOnClickListener { openLiveWallpaperPicker() }
        }
        dock.addView(applyButton)

        val dockWidth = if (screenWidthDp > 480f) {
            (460 * dp).toInt()
        } else {
            FrameLayout.LayoutParams.MATCH_PARENT
        }
        val dockParams = FrameLayout.LayoutParams(
            dockWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = (38 * dp).toInt()
            leftMargin = (14 * dp).toInt()
            rightMargin = (14 * dp).toInt()
        }
        root.addView(dock, dockParams)

        @Suppress("DEPRECATION")
        root.setOnApplyWindowInsetsListener { _, insets ->
            val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
            } else {
                maxOf(insets.systemWindowInsetTop, insets.displayCutout?.safeInsetTop ?: 0)
            }
            dockParams.topMargin = topInset + (10 * dp).toInt()
            dock.layoutParams = dockParams
            insets
        }

        setContentView(root)
        configureEdgeToEdge()
        root.requestApplyInsets()

        glThread = FalakGlThread(
            context = applicationContext,
            angleProvider = { currentOrbitalState().anglesRad }
        ).apply { start() }

        refreshHud()
    }

    override fun onResume() {
        super.onResume()
        val saved = FalakPreferences.load(this)
        currentLocation = saved.location
        currentMethod = saved.method
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

    private fun currentLocationDate(): LocalDate {
        return Instant.now().atZone(currentLocation.resolveZone()).toLocalDate()
    }

    private fun currentOrbitalState(): PrayerTimesEngine.OrbitalState {
        val epoch = scrubEpochMs ?: System.currentTimeMillis()
        return PrayerTimesEngine.computeState(currentLocation, currentMethod, epoch)
    }

    private fun refreshHud() {
        val state = currentOrbitalState()
        val isLive = scrubEpochMs == null
        val curTimeStr = PrayerTimesEngine.formatLocalMinutes(state.localMinutesOfDay)
        val nextName = PrayerTimesEngine.PRAYER_NAMES[state.nextPrayerIndex]
        val hrs = state.minutesToNext / 60
        val mins = state.minutesToNext % 60
        val countdown = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"

        statusLabel.text = "${currentLocation.name} $curTimeStr · $nextName in $countdown"
        livePill.alpha = if (isLive) 1.0f else 0.55f
        livePill.text = if (isLive) "LIVE" else "RESET"

        locationValueView.text = "${currentLocation.name} ▾"
        sourceValueView.text = "${currentMethod.shortName} ▾"

        for (i in 0 until 5) {
            val label = PrayerTimesEngine.PRAYER_NAMES[i]
            val timeStr = state.today.formattedTimes[i]
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

    private fun buildSelectorPill(
        dp: Float,
        caption: String,
        onClick: () -> Unit
    ): Pair<LinearLayout, TextView> {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (7 * dp).toInt())
            background = roundedBox(dp, 13f, 0x1AE6EBF5, 0x2AE6EBF5)
            setOnClickListener { onClick() }
        }
        val capView = TextView(this).apply {
            text = caption
            setTextColor(0x80DCE2F0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val valView = TextView(this).apply {
            setTextColor(COLOR_IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (1 * dp).toInt()
            }
        }
        container.addView(capView)
        container.addView(valView)
        return Pair(container, valView)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Calculation Source Selector Dialog (MWL & Jordan Ministry of Awqaf)
    // ─────────────────────────────────────────────────────────────────────────

    private fun showSourceDialog() {
        val dp = resources.displayMetrics.density
        val container = createDialogSurface(dp)

        container.addView(dialogTitle(dp, "Calculation Source"))
        container.addView(dialogSubtitle(dp, "Select the astronomical prayer-time standard"))

        var dialog: AlertDialog? = null

        for (method in PrayerMethod.values()) {
            val selected = (method == currentMethod)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
                background = roundedBox(
                    dp,
                    14f,
                    if (selected) 0x26FFF7E9 else 0x12E6EBF5,
                    if (selected) 0x80FFF7E9.toInt() else 0x22E6EBF5
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (10 * dp).toInt()
                }
                setOnClickListener {
                    currentMethod = method
                    FalakPreferences.saveMethod(this@MainActivity, method)
                    glThread?.requestRedraw()
                    refreshHud()
                    dialog?.dismiss()
                }
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val titleView = TextView(this).apply {
                text = method.displayName
                setTextColor(if (selected) COLOR_IVORY else 0xE6E8ECF5.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val descView = TextView(this).apply {
                text = method.summary
                setTextColor(0x9ADCE2F0.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (2 * dp).toInt()
                }
            }
            textCol.addView(titleView)
            textCol.addView(descView)

            val radioView = TextView(this).apply {
                text = if (selected) "●" else "○"
                setTextColor(if (selected) COLOR_IVORY else 0x66DCE2F0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding((10 * dp).toInt(), 0, 0, 0)
            }

            card.addView(textCol)
            card.addView(radioView)
            container.addView(card)
        }

        val closeBtn = buildGhostButton(dp, "Close") { dialog?.dismiss() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (14 * dp).toInt()
            }
        }
        container.addView(closeBtn)

        dialog = showStyledDialog(container)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manual Worldwide Location Selector Dialog (No Location Permission)
    // ─────────────────────────────────────────────────────────────────────────

    private fun showLocationDialog() {
        val dp = resources.displayMetrics.density
        val container = createDialogSurface(dp)

        container.addView(dialogTitle(dp, "Select Location"))
        container.addView(
            dialogSubtitle(
                dp,
                "Manual selection · ${currentLocation.displayName} (${currentLocation.formatCoordinates()})"
            )
        )

        val searchInput = buildDarkEditText(dp, "Search city, country, or timezone…").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10 * dp).toInt()
            }
        }
        container.addView(searchInput)

        var filteredCities = WorldCities.ALL
        var dialog: AlertDialog? = null

        val listHeight = minOf((310 * dp).toInt(), (resources.displayMetrics.heightPixels * 0.42f).toInt())
        val listView = ListView(this).apply {
            divider = ColorDrawable(0x18E6EBF5)
            dividerHeight = (1 * dp).toInt()
            isVerticalScrollBarEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                listHeight,
                1f
            ).apply {
                topMargin = (8 * dp).toInt()
            }
        }

        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = filteredCities.size
            override fun getItem(position: Int): PrayerLocation = filteredCities[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val city = filteredCities[position]
                val isSelected = !currentLocation.isCustom && city.id == currentLocation.id

                val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt())

                    val left = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val nameTv = TextView(this@MainActivity).apply {
                        tag = "name"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                        isSingleLine = true
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    val metaTv = TextView(this@MainActivity).apply {
                        tag = "meta"
                        setTextColor(0x88DCE2F0.toInt())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        typeface = Typeface.MONOSPACE
                        isSingleLine = true
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    left.addView(nameTv)
                    left.addView(metaTv)

                    val checkTv = TextView(this@MainActivity).apply {
                        tag = "check"
                        setTextColor(COLOR_IVORY)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        setPadding((8 * dp).toInt(), 0, 0, 0)
                    }
                    addView(left)
                    addView(checkTv)
                }

                val nameTv = row.findViewWithTag<TextView>("name")
                val metaTv = row.findViewWithTag<TextView>("meta")
                val checkTv = row.findViewWithTag<TextView>("check")

                nameTv.text = city.displayName
                nameTv.setTextColor(if (isSelected) COLOR_IVORY else 0xE6E8ECF5.toInt())
                nameTv.typeface = Typeface.create(
                    Typeface.SANS_SERIF,
                    if (isSelected) Typeface.BOLD else Typeface.NORMAL
                )
                metaTv.text = "${city.formatCoordinates()} · ${city.formatUtcOffset()}"
                checkTv.text = if (isSelected) "✓" else ""
                return row
            }
        }
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val picked = filteredCities[position]
            applySelectedLocation(picked)
            dialog?.dismiss()
        }
        container.addView(listView)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filteredCities = WorldCities.filter(s?.toString() ?: "")
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10 * dp).toInt()
            }
        }

        val customBtn = Button(this).apply {
            text = "Custom Coordinates…"
            setTextColor(COLOR_IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            isAllCaps = false
            background = roundedBox(dp, 14f, 0x26FFF7E9, 0x55FFF7E9)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f).apply {
                rightMargin = (8 * dp).toInt()
            }
            setOnClickListener {
                dialog?.dismiss()
                showCustomCoordinatesDialog()
            }
        }
        val closeBtn = buildGhostButton(dp, "Close") { dialog?.dismiss() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
        }
        buttonRow.addView(customBtn)
        buttonRow.addView(closeBtn)
        container.addView(buttonRow)

        dialog = showStyledDialog(container)
    }

    private fun showCustomCoordinatesDialog() {
        val dp = resources.displayMetrics.density
        val container = createDialogSurface(dp)

        container.addView(dialogTitle(dp, "Custom Coordinates"))
        container.addView(dialogSubtitle(dp, "Enter any manual latitude, longitude, and timezone"))

        val initialName = if (currentLocation.isCustom) currentLocation.name else ""
        val initialLat = String.format(Locale.US, "%.4f", currentLocation.latitudeDeg)
        val initialLon = String.format(Locale.US, "%.4f", currentLocation.longitudeDeg)
        var chosenZoneId = if (currentLocation.isCustom) {
            currentLocation.zoneId
        } else {
            ZoneId.systemDefault().id
        }

        container.addView(fieldLabel(dp, "Location Name"))
        val nameInput = buildDarkEditText(dp, "e.g. Madaba, Home").apply {
            setText(initialName)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        container.addView(nameInput)

        val coordRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * dp).toInt()
            }
        }

        val latCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = (6 * dp).toInt()
            }
        }
        latCol.addView(fieldLabel(dp, "Latitude (-85..85)", topMarginDp = 0f))
        val latInput = buildDarkEditText(dp, "31.9500").apply {
            setText(initialLat)
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        latCol.addView(latInput)

        val lonCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = (6 * dp).toInt()
            }
        }
        lonCol.addView(fieldLabel(dp, "Longitude (-180..180)", topMarginDp = 0f))
        val lonInput = buildDarkEditText(dp, "35.9450").apply {
            setText(initialLon)
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        lonCol.addView(lonInput)

        coordRow.addView(latCol)
        coordRow.addView(lonCol)
        container.addView(coordRow)

        container.addView(fieldLabel(dp, "Timezone (IANA)"))
        val tzButton = TextView(this).apply {
            setTextColor(COLOR_IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setPadding((12 * dp).toInt(), (11 * dp).toInt(), (12 * dp).toInt(), (11 * dp).toInt())
            background = roundedBox(dp, 12f, 0x1CE6EBF5, 0x35E6EBF5)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            text = formatZoneSummary(chosenZoneId)
            setOnClickListener {
                showTimezonePickerDialog(chosenZoneId) { pickedZone ->
                    chosenZoneId = pickedZone
                    text = formatZoneSummary(chosenZoneId)
                }
            }
        }
        container.addView(tzButton)

        val errorLabel = TextView(this).apply {
            setTextColor(0xFFFF8A80.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * dp).toInt()
            }
        }
        container.addView(errorLabel)

        var dialog: AlertDialog? = null

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (14 * dp).toInt()
            }
        }
        val cancelBtn = buildGhostButton(dp, "Cancel") { dialog?.dismiss() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = (8 * dp).toInt()
            }
        }
        val saveBtn = Button(this).apply {
            text = "Save Location"
            setTextColor(COLOR_BG)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAllCaps = false
            background = roundedBox(dp, 14f, 0xEBFFF7E9.toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            setOnClickListener {
                val lat = latInput.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
                val lon = lonInput.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
                if (lat == null || lat < -85.0 || lat > 85.0) {
                    errorLabel.text = "Enter a valid latitude between -85 and 85."
                    errorLabel.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                if (lon == null || lon < -180.0 || lon > 180.0) {
                    errorLabel.text = "Enter a valid longitude between -180 and 180."
                    errorLabel.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                val label = nameInput.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: "Custom"
                val customLoc = PrayerLocation(
                    id = "custom",
                    name = label,
                    country = "",
                    latitudeDeg = lat,
                    longitudeDeg = lon,
                    zoneId = chosenZoneId,
                    isCustom = true
                )
                applySelectedLocation(customLoc)
                dialog?.dismiss()
            }
        }
        actionRow.addView(cancelBtn)
        actionRow.addView(saveBtn)
        container.addView(actionRow)

        val scrollWrapper = ScrollView(this).apply {
            isFillViewport = true
            addView(container)
        }
        dialog = showStyledDialog(scrollWrapper)
    }

    private fun showTimezonePickerDialog(
        currentZoneId: String,
        onPicked: (String) -> Unit
    ) {
        val dp = resources.displayMetrics.density
        val container = createDialogSurface(dp)

        container.addView(dialogTitle(dp, "Select Timezone"))
        val deviceZone = ZoneId.systemDefault().id
        container.addView(dialogSubtitle(dp, "Device timezone: $deviceZone"))

        val allZones = buildList {
            add(deviceZone)
            val rest = ZoneId.getAvailableZoneIds()
                .filter { it.contains("/") && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && it != deviceZone }
                .sorted()
            addAll(rest)
        }

        val searchInput = buildDarkEditText(dp, "Search timezone (e.g. London, New_York, Asia)…").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10 * dp).toInt()
            }
        }
        container.addView(searchInput)

        var filteredZones = allZones
        var dialog: AlertDialog? = null

        val listHeight = minOf((290 * dp).toInt(), (resources.displayMetrics.heightPixels * 0.40f).toInt())
        val listView = ListView(this).apply {
            divider = ColorDrawable(0x18E6EBF5)
            dividerHeight = (1 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                listHeight,
                1f
            ).apply {
                topMargin = (8 * dp).toInt()
            }
        }

        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = filteredZones.size
            override fun getItem(position: Int): String = filteredZones[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val zid = filteredZones[position]
                val isSelected = (zid == currentZoneId)
                val tv = (convertView as? TextView) ?: TextView(this@MainActivity).apply {
                    setPadding((6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    typeface = Typeface.MONOSPACE
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                }
                val marker = if (isSelected) "✓ " else ""
                tv.text = "$marker${formatZoneSummary(zid)}"
                tv.setTextColor(if (isSelected) COLOR_IVORY else 0xDCE8ECF5.toInt())
                return tv
            }
        }
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            onPicked(filteredZones[position])
            dialog?.dismiss()
        }
        container.addView(listView)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = (s?.toString() ?: "").trim().lowercase()
                filteredZones = if (q.isEmpty()) {
                    allZones
                } else {
                    allZones.filter { it.lowercase().contains(q) }
                }
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val closeBtn = buildGhostButton(dp, "Cancel") { dialog?.dismiss() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10 * dp).toInt()
            }
        }
        container.addView(closeBtn)

        dialog = showStyledDialog(container)
    }

    private fun applySelectedLocation(location: PrayerLocation) {
        currentLocation = location
        scrubEpochMs = null
        FalakPreferences.saveLocation(this, location)
        glThread?.requestRedraw()
        refreshHud()
    }

    private fun formatZoneSummary(zoneId: String): String {
        return try {
            val z = ZoneId.of(zoneId)
            val sec = z.rules.getOffset(Instant.now()).totalSeconds
            val sign = if (sec >= 0) "+" else "-"
            val absSec = abs(sec)
            val h = absSec / 3600
            val m = (absSec % 3600) / 60
            val off = if (m == 0) "UTC$sign$h" else String.format(Locale.US, "UTC%s%d:%02d", sign, h, m)
            "$zoneId ($off)"
        } catch (_: Exception) {
            zoneId
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dark Obsidian Dialog Styling Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun createDialogSurface(dp: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (18 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            background = roundedBox(dp, 22f, 0xFA121318.toInt(), 0x30E6EBF5)
        }
    }

    @Suppress("DEPRECATION")
    private fun showStyledDialog(contentView: View): AlertDialog {
        val dialog = AlertDialog.Builder(this)
            .setView(contentView)
            .create()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.show()
        return dialog
    }

    private fun dialogTitle(dp: Float, text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(COLOR_IVORY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setPadding(0, 0, 0, (2 * dp).toInt())
        }
    }

    private fun dialogSubtitle(dp: Float, text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(0x99DCE2F0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, (2 * dp).toInt())
        }
    }

    private fun fieldLabel(dp: Float, text: String, topMarginDp: Float = 10f): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(0xB0DCE2F0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (topMarginDp * dp).toInt()
                bottomMargin = (4 * dp).toInt()
            }
        }
    }

    private fun buildDarkEditText(dp: Float, hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            setTextColor(COLOR_IVORY)
            setHintTextColor(0x66DCE2F0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            isSingleLine = true
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            background = roundedBox(dp, 12f, 0x1CE6EBF5, 0x35E6EBF5)
        }
    }

    private fun buildGhostButton(dp: Float, label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(0xCCDCE2F0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            isAllCaps = false
            background = roundedBox(dp, 14f, 0x14E6EBF5, 0x26E6EBF5)
            setOnClickListener { onClick() }
        }
    }

    private fun roundedBox(dp: Float, radiusDp: Float, fillColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * dp
            setColor(fillColor)
            if (strokeColor != 0) {
                setStroke((1f * dp).toInt().coerceAtLeast(1), strokeColor)
            }
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

    @Suppress("DEPRECATION")
    private fun configureEdgeToEdge() {
        val decor = window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val applyAppearance = {
                try {
                    val controller = decor.windowInsetsController ?: window.insetsController
                    controller?.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                } catch (_: Exception) {
                    // Ignore OEM insets controller quirks on older Android 11/12 builds.
                }
            }
            applyAppearance()
            decor.post { applyAppearance() }
        } else {
            decor.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    companion object {
        private const val COLOR_BG = 0xFF0C0D10.toInt()
        private const val COLOR_IVORY = 0xEBFFF8ED.toInt()
    }
}
