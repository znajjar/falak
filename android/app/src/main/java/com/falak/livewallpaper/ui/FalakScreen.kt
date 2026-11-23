package com.falak.livewallpaper.ui

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.viewinterop.AndroidView
import com.falak.livewallpaper.BackdropBlurRect
import com.falak.livewallpaper.BackdropBlurSpec
import com.falak.livewallpaper.FalakPreferences
import com.falak.livewallpaper.OrbitMode
import com.falak.livewallpaper.PrayerLocation
import com.falak.livewallpaper.PrayerMethod
import com.falak.livewallpaper.PrayerTimesEngine
import com.falak.livewallpaper.R
import com.falak.livewallpaper.ui.theme.FalakPalette
import kotlinx.coroutines.delay
import java.util.Locale

private val GlassCardFill = Color(0x6E0E1017)
private val GlassSectionFill = Color(0x4A181C26)
private val GlassTileFill = Color(0x52232836)
private val GlassActiveFill = Color(0x99483B28)
private val GlassRimBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0x3DFFFFFF),
        Color(0x14FFFFFF)
    )
)
private val GlassSectionBorder = Color(0x16FFFFFF)

@Composable
fun FalakScreen(
    uiState: FalakUiState,
    isFullScreen: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    onBlurSpecChange: (BackdropBlurSpec) -> Unit,
    onSurfaceAvailable: (SurfaceTexture, Int, Int) -> Unit,
    onSurfaceSizeChanged: (SurfaceTexture, Int, Int) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onReturnToLive: () -> Unit,
    onAlignPrayer: (Int) -> Unit,
    onScrubMinutes: (Float) -> Unit,
    onSelectLocation: (PrayerLocation) -> Unit,
    onSelectMethod: (PrayerMethod) -> Unit,
    onSelectOrbitMode: (OrbitMode) -> Unit,
    onToggleGlitterPreview: () -> Unit,
    onResetStarfield: () -> Unit,
    onStarSizeChange: (Float) -> Unit,
    onStarDensityChange: (Float) -> Unit,
    onGlitterSpeedChange: (Float) -> Unit,
    onApplyLiveWallpaper: () -> Unit
) {
    var activeSheet by remember { mutableStateOf(ActiveSheet.NONE) }
    var showFullScreenHint by remember { mutableStateOf(false) }

    BackHandler(enabled = isFullScreen) {
        onFullScreenChange(false)
    }

    LaunchedEffect(isFullScreen) {
        if (isFullScreen) {
            showFullScreenHint = true
            delay(2200L)
            showFullScreenHint = false
        } else {
            showFullScreenHint = false
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val currentTopInset = max(statusBarTop, cutoutTop)

    var stableTopInset by remember { mutableStateOf(currentTopInset) }
    var stableBottomInset by remember { mutableStateOf(navBarBottom) }
    if (currentTopInset > 0.dp && !isFullScreen) {
        stableTopInset = currentTopInset
    }
    if (navBarBottom > 0.dp && !isFullScreen) {
        stableBottomInset = navBarBottom
    }

    val glassAlpha by animateFloatAsState(
        targetValue = if (isFullScreen) 0f else 1f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "frostedCardAlpha"
    )

    val density = LocalDensity.current
    val cardRadiusPx = with(density) { 28.dp.toPx() }

    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var controlsCardRect by remember { mutableStateOf<BackdropBlurRect?>(null) }
    var lastEmittedSpec by remember { mutableStateOf<BackdropBlurSpec?>(null) }

    val blurRects = remember(controlsCardRect) {
        listOfNotNull(controlsCardRect)
    }
    val currentBlurSpec = BackdropBlurSpec(alpha = glassAlpha, rects = blurRects)

    SideEffect {
        if (currentBlurSpec != lastEmittedSpec) {
            lastEmittedSpec = currentBlurSpec
            onBlurSpecChange(currentBlurSpec)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(FalakPalette.ObsidianBg)
            .onGloballyPositioned { coords ->
                rootOrigin = coords.positionInWindow()
            }
    ) {
        val usableHeight = (maxHeight - stableTopInset - stableBottomInset).coerceAtLeast(480.dp)
        val openSkyHeight = (usableHeight * 0.37f).coerceIn(190.dp, 350.dp)

        // 1. Full-bleed native OpenGL ES 3.0 live wallpaper (renders real-time Dual-Kawase blur
        //    strictly behind the floating frosted-glass controls card)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = true
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            onSurfaceAvailable(surface, width, height)
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            onSurfaceSizeChanged(surface, width, height)
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            onSurfaceDestroyed()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                    }
                    if (isAvailable) {
                        surfaceTexture?.let { st ->
                            if (width > 0 && height > 0) {
                                onSurfaceAvailable(st, width, height)
                            }
                        }
                    }
                }
            }
        )

        // 2. Tap anywhere on the exposed wallpaper to toggle unobstructed full-screen mode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = if (isFullScreen) {
                        "Tap anywhere to show controls"
                    } else {
                        "Tap exposed wallpaper to view full screen"
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onFullScreenChange(!isFullScreen)
                }
        )

        // 3. Single Floating Frosted-Glass Material Design 3 Controls Card
        if (glassAlpha > 0.01f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = stableTopInset, bottom = stableBottomInset + 12.dp)
                    .padding(horizontal = 14.dp)
                    .alpha(glassAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pure unobstructed full-bleed sky window from the very top edge
                Spacer(modifier = Modifier.height(openSkyHeight))

                // Floating Frosted-Glass Material Design 3 Controls Card
                FrostedControlsCard(
                    uiState = uiState,
                    onReturnToLive = onReturnToLive,
                    onAlignPrayer = onAlignPrayer,
                    onScrubMinutes = onScrubMinutes,
                    onOpenLocationSheet = { activeSheet = ActiveSheet.LOCATION },
                    onOpenSourceSheet = { activeSheet = ActiveSheet.SOURCE },
                    onSelectOrbitMode = onSelectOrbitMode,
                    onToggleGlitterPreview = onToggleGlitterPreview,
                    onResetStarfield = onResetStarfield,
                    onStarSizeChange = onStarSizeChange,
                    onStarDensityChange = onStarDensityChange,
                    onGlitterSpeedChange = onGlitterSpeedChange,
                    onApplyLiveWallpaper = onApplyLiveWallpaper,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .onGloballyPositioned { coords ->
                            val b = coords.boundsInWindow()
                            controlsCardRect = BackdropBlurRect(
                                leftPx = b.left - rootOrigin.x,
                                topPx = b.top - rootOrigin.y,
                                rightPx = b.right - rootOrigin.x,
                                bottomPx = b.bottom - rootOrigin.y,
                                cornerRadiusPx = cardRadiusPx
                            )
                        }
                )
            }
        }

        // 4. Auto-fading full-screen exit hint pill
        AnimatedVisibility(
            visible = isFullScreen && showFullScreenHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = stableBottomInset + 28.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xB811141C),
                border = BorderStroke(1.dp, Color(0x30FFFFFF))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_m3_close_fullscreen),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Tap anywhere to show controls",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Material Design 3 Modal Bottom Sheets
    when (activeSheet) {
        ActiveSheet.NONE -> Unit
        ActiveSheet.LOCATION -> {
            LocationPickerSheet(
                currentLocation = uiState.location,
                onDismiss = { activeSheet = ActiveSheet.NONE },
                onSelectLocation = onSelectLocation,
                onOpenCustomCoordinates = { activeSheet = ActiveSheet.CUSTOM_COORDINATES }
            )
        }
        ActiveSheet.CUSTOM_COORDINATES -> {
            CustomCoordinatesSheet(
                currentLocation = uiState.location,
                onDismiss = { activeSheet = ActiveSheet.NONE },
                onSaveLocation = onSelectLocation
            )
        }
        ActiveSheet.SOURCE -> {
            SourcePickerSheet(
                currentMethod = uiState.method,
                onDismiss = { activeSheet = ActiveSheet.NONE },
                onSelectMethod = onSelectMethod
            )
        }
    }
}

@Composable
private fun FrostedControlsCard(
    uiState: FalakUiState,
    onReturnToLive: () -> Unit,
    onAlignPrayer: (Int) -> Unit,
    onScrubMinutes: (Float) -> Unit,
    onOpenLocationSheet: () -> Unit,
    onOpenSourceSheet: () -> Unit,
    onSelectOrbitMode: (OrbitMode) -> Unit,
    onToggleGlitterPreview: () -> Unit,
    onResetStarfield: () -> Unit,
    onStarSizeChange: (Float) -> Unit,
    onStarDensityChange: (Float) -> Unit,
    onGlitterSpeedChange: (Float) -> Unit,
    onApplyLiveWallpaper: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .clip(cardShape)
            .background(GlassCardFill)
            .border(width = 1.dp, brush = GlassRimBrush, shape = cardShape)
            // Prevent taps inside the card from bubbling to the background full-screen toggle
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Scrollable M3 frosted sections inside the floating card
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrayerScheduleSection(
                    uiState = uiState,
                    onReturnToLive = onReturnToLive,
                    onAlignPrayer = onAlignPrayer,
                    onScrubMinutes = onScrubMinutes
                )

                ConfigurationSection(
                    uiState = uiState,
                    onOpenLocationSheet = onOpenLocationSheet,
                    onOpenSourceSheet = onOpenSourceSheet,
                    onSelectOrbitMode = onSelectOrbitMode
                )

                StarfieldSection(
                    uiState = uiState,
                    onToggleGlitterPreview = onToggleGlitterPreview,
                    onResetStarfield = onResetStarfield,
                    onStarSizeChange = onStarSizeChange,
                    onStarDensityChange = onStarDensityChange,
                    onGlitterSpeedChange = onGlitterSpeedChange
                )
            }

            // Pinned bottom M3 primary CTA inside the floating glass card
            HorizontalDivider(
                color = Color(0x1EFFFFFF),
                thickness = 1.dp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            ) {
                Button(
                    onClick = onApplyLiveWallpaper,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_m3_wallpaper),
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.set_wallpaper_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun PrayerScheduleSection(
    uiState: FalakUiState,
    onReturnToLive: () -> Unit,
    onAlignPrayer: (Int) -> Unit,
    onScrubMinutes: (Float) -> Unit
) {
    val state = uiState.orbitalState
    val curTimeStr = PrayerTimesEngine.formatLocalMinutes(state.localMinutesOfDay)
    val nextName = PrayerTimesEngine.PRAYER_NAMES[state.nextPrayerIndex]
    val hrs = state.minutesToNext / 60
    val mins = state.minutesToNext % 60
    val countdown = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"

    val highlightedIndex = if (uiState.isLive) {
        state.nextPrayerIndex
    } else {
        uiState.alignedPrayerIndex ?: state.nextPrayerIndex
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = GlassSectionFill,
        border = BorderStroke(0.75.dp, GlassSectionBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.isLive) "NEXT PRAYER" else "TIME PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isLive) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$nextName in $countdown",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.orbitMode == OrbitMode.PRAYER_WINDOW) {
                            Text(
                                text = "  ·  Sunrise ${state.today.sunriseFormatted}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (!uiState.isLive) {
                    Surface(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onReturnToLive),
                        shape = CircleShape,
                        color = GlassActiveFill,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_m3_refresh),
                                contentDescription = "Return to live time",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Live",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GlassTileFill,
                    border = BorderStroke(0.75.dp, GlassSectionBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (uiState.isLive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(FalakPalette.LiveDotGreen)
                            )
                        }
                        Text(
                            text = curTimeStr,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Five tappable prayer alignment pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (i in 0 until 5) {
                    val isActive = (i == highlightedIndex)
                    val label = PrayerTimesEngine.PRAYER_NAMES[i]
                    val timeStr = state.today.formattedTimes[i]
                    val pillShape = RoundedCornerShape(14.dp)

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(pillShape)
                            .clickable { onAlignPrayer(i) },
                        shape = pillShape,
                        color = if (isActive) GlassActiveFill else GlassTileFill,
                        border = BorderStroke(
                            width = if (isActive) 1.dp else 0.5.dp,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            } else {
                                GlassSectionBorder
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Slider(
                value = state.localMinutesOfDay.toFloat().coerceIn(0f, 1439f),
                onValueChange = onScrubMinutes,
                valueRange = 0f..1439f,
                colors = glassSliderColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "00:00",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                )
                Text(
                    text = "Tap a prayer or drag to preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                )
                Text(
                    text = "23:59",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigurationSection(
    uiState: FalakUiState,
    onOpenLocationSheet: () -> Unit,
    onOpenSourceSheet: () -> Unit,
    onSelectOrbitMode: (OrbitMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = GlassSectionFill,
        border = BorderStroke(0.75.dp, GlassSectionBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassSelectorTile(
                    iconRes = R.drawable.ic_m3_location,
                    caption = "LOCATION",
                    value = uiState.location.name,
                    supporting = if (uiState.location.country.isNotBlank()) {
                        uiState.location.country
                    } else {
                        uiState.location.formatCoordinates()
                    },
                    onClick = onOpenLocationSheet,
                    modifier = Modifier.weight(1f)
                )
                GlassSelectorTile(
                    iconRes = R.drawable.ic_m3_explore,
                    caption = "SOURCE",
                    value = uiState.method.shortName,
                    supporting = uiState.method.summary,
                    onClick = onOpenSourceSheet,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ORBIT MODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.orbitMode.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            val modes = OrbitMode.values()
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    val selected = mode == uiState.orbitMode
                    SegmentedButton(
                        selected = selected,
                        onClick = { onSelectOrbitMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = GlassActiveFill,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            inactiveContainerColor = GlassTileFill,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            inactiveBorderColor = Color(0x28FFFFFF)
                        ),
                        label = {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassSelectorTile(
    iconRes: Int,
    caption: String,
    value: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileShape = RoundedCornerShape(15.dp)
    Surface(
        modifier = modifier
            .clip(tileShape)
            .clickable(onClick = onClick),
        shape = tileShape,
        color = GlassTileFill,
        border = BorderStroke(0.75.dp, GlassSectionBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(R.drawable.ic_m3_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StarfieldSection(
    uiState: FalakUiState,
    onToggleGlitterPreview: () -> Unit,
    onResetStarfield: () -> Unit,
    onStarSizeChange: (Float) -> Unit,
    onStarDensityChange: (Float) -> Unit,
    onGlitterSpeedChange: (Float) -> Unit
) {
    val sf = uiState.starfield

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = GlassSectionFill,
        border = BorderStroke(0.75.dp, GlassSectionBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_m3_sparkles),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Starfield",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = uiState.isGlitterActive,
                    onClick = onToggleGlitterPreview,
                    label = {
                        Text(
                            text = if (uiState.isGlitterActive) "Glitter On" else "Test Glitter",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = GlassTileFill,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = Color(0x9949371E),
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = uiState.isGlitterActive,
                        borderColor = Color(0x28FFFFFF),
                        selectedBorderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                    )
                )

                if (uiState.isStarfieldModified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onResetStarfield,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            StarfieldSliderRow(
                label = "Star Size",
                valueText = formatMultiplier(sf.sizeScale, allowOff = false),
                value = sf.sizeScale,
                valueRange = FalakPreferences.MIN_STAR_SIZE..FalakPreferences.MAX_STAR_SIZE,
                onValueChange = onStarSizeChange
            )

            StarfieldSliderRow(
                label = "Star Density",
                valueText = formatMultiplier(sf.densityScale, allowOff = true),
                value = sf.densityScale,
                valueRange = FalakPreferences.MIN_STAR_DENSITY..FalakPreferences.MAX_STAR_DENSITY,
                onValueChange = onStarDensityChange
            )

            StarfieldSliderRow(
                label = "Glitter Speed",
                valueText = formatMultiplier(sf.glitterSpeed, allowOff = true),
                value = sf.glitterSpeed,
                valueRange = FalakPreferences.MIN_GLITTER_SPEED..FalakPreferences.MAX_GLITTER_SPEED,
                onValueChange = onGlitterSpeedChange
            )
        }
    }
}

@Composable
private fun StarfieldSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = glassSliderColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun glassSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = Color(0x40DCE2F0)
)

private fun formatMultiplier(value: Float, allowOff: Boolean): String {
    return if (allowOff && value < 0.005f) {
        "Off"
    } else {
        String.format(Locale.US, "%.2f×", value)
    }
}
