package com.falak.livewallpaper.ui

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import com.falak.livewallpaper.FalakPreferences
import com.falak.livewallpaper.OrbitMode
import com.falak.livewallpaper.PrayerLocation
import com.falak.livewallpaper.PrayerMethod
import com.falak.livewallpaper.PrayerTimesEngine
import com.falak.livewallpaper.R
import com.falak.livewallpaper.ui.theme.FalakPalette
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun FalakScreen(
    uiState: FalakUiState,
    isFullScreen: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
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

    // Preserve stable non-zero system insets so hiding bars in immersive full-screen
    // never causes the minimized preview coordinates to jitter on exit.
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

    val fullScreenProgress by animateFloatAsState(
        targetValue = if (isFullScreen) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 360f),
        label = "wallpaperFullScreenProgress"
    )

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(FalakPalette.ObsidianBg)
    ) {
        val rootW = maxWidth
        val rootH = maxHeight
        val safeRootH = rootH.coerceAtLeast(1.dp)
        val safeRootW = rootW.coerceAtLeast(1.dp)

        val topBarHeight = 52.dp
        val previewTopY = stableTopInset + topBarHeight + 6.dp
        val usableHeight = (rootH - stableTopInset - stableBottomInset).coerceAtLeast(460.dp)
        val targetPreviewH = (usableHeight * 0.39f).coerceIn(210.dp, 360.dp)
        val previewScale = minOf(
            targetPreviewH / safeRootH,
            (safeRootW - 48.dp) / safeRootW
        ).coerceIn(0.28f, 0.50f)

        val previewVisualW = rootW * previewScale
        val previewVisualH = rootH * previewScale
        val controlsTopY = previewTopY + previewVisualH + 16.dp

        val minimizedTransYPx = with(density) {
            ((previewTopY + previewVisualH / 2f) - (rootH / 2f)).toPx()
        }

        val currentScale = lerp(previewScale, 1f, fullScreenProgress)
        val currentTransYPx = lerp(minimizedTransYPx, 0f, fullScreenProgress)
        val visualCornerDp = lerp(26f, 0f, fullScreenProgress)
        val chromeAlpha = (1f - fullScreenProgress * 1.55f).coerceIn(0f, 1f)
        val controlsSlideY = (fullScreenProgress * 64f).dp
        val topBarSlideY = (-fullScreenProgress * 28f).dp

        // 1. Subtle lifted tonal backdrop behind the minimized wallpaper stage
        if (chromeAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(controlsTopY + 32.dp)
                    .alpha(chromeAlpha)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                FalakPalette.StageBackdropTop,
                                FalakPalette.StageBackdropBottom
                            )
                        )
                    )
            ) {
                // Soft ambient starlight aura beneath the floating minimized wallpaper frame
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    FalakPalette.WarmGlow.copy(alpha = 0.09f),
                                    Color.Transparent
                                ),
                                center = Offset(
                                    x = with(density) { (rootW / 2f).toPx() },
                                    y = with(density) { (previewTopY + previewVisualH * 0.78f).toPx() }
                                ),
                                radius = with(density) { (previewVisualW * 1.45f).toPx().coerceAtLeast(1f) }
                            )
                        )
                )
            }
        }

        // 2. Material 3 Top App Bar (fades & glides upward when wallpaper expands to full screen)
        if (chromeAlpha > 0.01f) {
            FalakTopBar(
                uiState = uiState,
                onReturnToLive = onReturnToLive,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = stableTopInset)
                    .height(topBarHeight)
                    .offset(y = topBarSlideY)
                    .alpha(chromeAlpha)
            )
        }

        // 3. Elevated M3 Controls Sheet below the minimized wallpaper
        if (chromeAlpha > 0.01f) {
            ControlsSheet(
                uiState = uiState,
                bottomInset = stableBottomInset,
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
                    .fillMaxSize()
                    .padding(top = controlsTopY)
                    .offset(y = controlsSlideY)
                    .alpha(chromeAlpha)
            )
        }

        // 3b. Opaque obsidian shadow card behind the minimized TextureView so drop shadow casts
        //     cleanly onto the stage backdrop without tinting the OpenGL canvas.
        if (chromeAlpha > 0.01f) {
            val backingShape = RoundedCornerShape(26.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = previewTopY)
                    .size(width = previewVisualW, height = previewVisualH)
                    .alpha(chromeAlpha)
                    .shadow(
                        elevation = 18.dp,
                        shape = backingShape,
                        clip = false,
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(backingShape)
                    .background(FalakPalette.ObsidianBg)
            )
        }

        // 4. Persistent Native-Aspect OpenGL ES 3.0 TextureView
        //    Minimized upwards into a phone-proportioned card, expanding seamlessly to full screen.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = currentScale
                    scaleY = currentScale
                    translationY = currentTransYPx
                    if (visualCornerDp > 0.25f) {
                        val safeScale = currentScale.coerceAtLeast(0.1f)
                        shape = RoundedCornerShape((visualCornerDp / safeScale).dp)
                        clip = true
                    } else {
                        clip = false
                    }
                }
        ) {
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
        }

        // 5. Minimized Preview Frame: crisp M3 bezel border, exact tap target, & expand affordance
        if (!isFullScreen && chromeAlpha > 0.05f) {
            val frameShape = RoundedCornerShape(26.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = previewTopY)
                    .size(width = previewVisualW, height = previewVisualH)
                    .alpha(chromeAlpha)
                    .clip(frameShape)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                            )
                        ),
                        shape = frameShape
                    )
                    .semantics {
                        contentDescription = "Tap wallpaper preview to view full screen"
                        role = Role.Button
                    }
                    .clickable { onFullScreenChange(true) }
            ) {
                // Subtle expand badge in top-right dark sky corner (never blocks planets or star)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(28.dp),
                    shape = CircleShape,
                    color = Color(0xA813161E),
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_m3_open_in_full),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // 6. Full-Screen Tap-to-Exit Layer + auto-fading M3 hint pill
        if (isFullScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = "Tap anywhere to exit full screen wallpaper preview"
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onFullScreenChange(false)
                    }
            ) {
                AnimatedVisibility(
                    visible = showFullScreenHint,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = stableBottomInset + 28.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xC214161D),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        ),
                        tonalElevation = 6.dp
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
                                text = "Tap anywhere to minimize",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
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
private fun FalakTopBar(
    uiState: FalakUiState,
    onReturnToLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val curTimeStr = PrayerTimesEngine.formatLocalMinutes(uiState.orbitalState.localMinutesOfDay)

    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_falak_wordmark),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 32.dp, height = 25.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${uiState.location.name} · $curTimeStr",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (uiState.isLive) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(FalakPalette.LiveDotGreen)
                    )
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onReturnToLive),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_m3_refresh),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Live",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsSheet(
    uiState: FalakUiState,
    bottomInset: Dp,
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
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Surface(
        modifier = modifier,
        shape = sheetShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Subtle M3 top grab/sheen divider line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                )
            }

            // Scrollable M3 cards area (wallpaper preview remains pinned & live above!)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrayerScheduleCard(
                    uiState = uiState,
                    onAlignPrayer = onAlignPrayer,
                    onScrubMinutes = onScrubMinutes
                )

                ConfigurationCard(
                    uiState = uiState,
                    onOpenLocationSheet = onOpenLocationSheet,
                    onOpenSourceSheet = onOpenSourceSheet,
                    onSelectOrbitMode = onSelectOrbitMode
                )

                StarfieldCard(
                    uiState = uiState,
                    onToggleGlitterPreview = onToggleGlitterPreview,
                    onResetStarfield = onResetStarfield,
                    onStarSizeChange = onStarSizeChange,
                    onStarDensityChange = onStarDensityChange,
                    onGlitterSpeedChange = onGlitterSpeedChange
                )
            }

            // Pinned M3 Primary Action Bar
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = bottomInset),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onApplyLiveWallpaper,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .height(52.dp),
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
                        modifier = Modifier.size(20.dp)
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
private fun PrayerScheduleCard(
    uiState: FalakUiState,
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
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp)
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

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = curTimeStr,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(11.dp))

            // Five tappable prayer alignment pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0 until 5) {
                    val isActive = (i == highlightedIndex)
                    val label = PrayerTimesEngine.PRAYER_NAMES[i]
                    val timeStr = state.today.formattedTimes[i]
                    val pillShape = RoundedCornerShape(15.dp)

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(pillShape)
                            .clickable { onAlignPrayer(i) },
                        shape = pillShape,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = if (isActive) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        } else {
                            null
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 8.dp),
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
                            Spacer(modifier = Modifier.height(3.dp))
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

            Spacer(modifier = Modifier.height(8.dp))

            // Material Design 3 24-hour orbit scrub slider
            Slider(
                value = state.localMinutesOfDay.toFloat().coerceIn(0f, 1439f),
                onValueChange = onScrubMinutes,
                valueRange = 0f..1439f,
                colors = falakSliderColors(),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
                Text(
                    text = "Tap a prayer or drag to preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
                Text(
                    text = "23:59",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigurationCard(
    uiState: FalakUiState,
    onOpenLocationSheet: () -> Unit,
    onOpenSourceSheet: () -> Unit,
    onSelectOrbitMode: (OrbitMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            // Side-by-side Location & Calculation Source M3 selector tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SelectorTile(
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
                SelectorTile(
                    iconRes = R.drawable.ic_m3_explore,
                    caption = "SOURCE",
                    value = uiState.method.shortName,
                    supporting = uiState.method.summary,
                    onClick = onOpenSourceSheet,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Inline M3 SingleChoiceSegmentedButtonRow for Orbit Mode
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val modes = OrbitMode.values()
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    val selected = mode == uiState.orbitMode
                    SegmentedButton(
                        selected = selected,
                        onClick = { onSelectOrbitMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
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
private fun SelectorTile(
    iconRes: Int,
    caption: String,
    value: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileShape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .clip(tileShape)
            .clickable(onClick = onClick),
        shape = tileShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
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
private fun StarfieldCard(
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
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp)
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
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = uiState.isGlitterActive,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
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
            colors = falakSliderColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun falakSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
)

private fun formatMultiplier(value: Float, allowOff: Boolean): String {
    return if (allowOff && value < 0.005f) {
        "Off"
    } else {
        String.format(Locale.US, "%.2f×", value)
    }
}
