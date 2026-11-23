package com.falak.livewallpaper.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.falak.livewallpaper.PrayerLocation
import com.falak.livewallpaper.PrayerMethod
import com.falak.livewallpaper.R
import com.falak.livewallpaper.WorldCities
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerSheet(
    currentLocation: PrayerLocation,
    onDismiss: () -> Unit,
    onSelectLocation: (PrayerLocation) -> Unit,
    onOpenCustomCoordinates: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filteredCities = remember(query) { WorldCities.filter(query) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Select Location",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Offline · ${currentLocation.displayName} (${currentLocation.formatCoordinates()})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search city, country, or timezone…"
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 340.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = filteredCities,
                    key = { it.id }
                ) { city ->
                    val isSelected = !currentLocation.isCustom && city.id == currentLocation.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onSelectLocation(city)
                                onDismiss()
                            },
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = city.displayName,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = "${city.formatCoordinates()} · ${city.formatUtcOffset()}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    painter = painterResource(R.drawable.ic_m3_check),
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onOpenCustomCoordinates,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_m3_location),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Custom Coordinates…",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomCoordinatesSheet(
    currentLocation: PrayerLocation,
    onDismiss: () -> Unit,
    onSaveLocation: (PrayerLocation) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember {
        mutableStateOf(if (currentLocation.isCustom) currentLocation.name else "")
    }
    var latText by remember {
        mutableStateOf(String.format(Locale.US, "%.4f", currentLocation.latitudeDeg))
    }
    var lonText by remember {
        mutableStateOf(String.format(Locale.US, "%.4f", currentLocation.longitudeDeg))
    }
    var chosenZoneId by remember {
        mutableStateOf(
            if (currentLocation.isCustom) currentLocation.zoneId else ZoneId.systemDefault().id
        )
    }
    var latError by remember { mutableStateOf<String?>(null) }
    var lonError by remember { mutableStateOf<String?>(null) }
    var showTimezoneSheet by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp)
        ) {
            Text(
                text = "Custom Coordinates",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Enter any manual latitude, longitude, and IANA timezone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Location Name") },
                placeholder = { Text("e.g. Madaba, Home") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = {
                        latText = it
                        latError = null
                    },
                    label = { Text("Latitude") },
                    placeholder = { Text("31.9500") },
                    singleLine = true,
                    isError = latError != null,
                    supportingText = latError?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lonText,
                    onValueChange = {
                        lonText = it
                        lonError = null
                    },
                    label = { Text("Longitude") },
                    placeholder = { Text("35.9450") },
                    singleLine = true,
                    isError = lonError != null,
                    supportingText = lonError?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "TIMEZONE (IANA)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showTimezoneSheet = true },
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_m3_schedule),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = formatZoneSummary(chosenZoneId),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_m3_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = {
                        val lat = latText.trim().replace(',', '.').toDoubleOrNull()
                        val lon = lonText.trim().replace(',', '.').toDoubleOrNull()
                        var valid = true
                        if (lat == null || lat < -85.0 || lat > 85.0) {
                            latError = "-85 to 85"
                            valid = false
                        }
                        if (lon == null || lon < -180.0 || lon > 180.0) {
                            lonError = "-180 to 180"
                            valid = false
                        }
                        if (!valid || lat == null || lon == null) return@Button

                        val label = name.trim().takeUnless { it.isEmpty() } ?: "Custom"
                        onSaveLocation(
                            PrayerLocation(
                                id = "custom",
                                name = label,
                                country = "",
                                latitudeDeg = lat,
                                longitudeDeg = lon,
                                zoneId = chosenZoneId,
                                isCustom = true
                            )
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Save Location", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showTimezoneSheet) {
        TimezonePickerSheet(
            currentZoneId = chosenZoneId,
            onDismiss = { showTimezoneSheet = false },
            onSelectZone = { picked ->
                chosenZoneId = picked
                showTimezoneSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimezonePickerSheet(
    currentZoneId: String,
    onDismiss: () -> Unit,
    onSelectZone: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val deviceZone = remember { ZoneId.systemDefault().id }
    val allZones = remember(deviceZone) {
        buildList {
            add(deviceZone)
            addAll(
                ZoneId.getAvailableZoneIds()
                    .filter {
                        it.contains("/") &&
                            !it.startsWith("Etc/") &&
                            !it.startsWith("SystemV/") &&
                            it != deviceZone
                    }
                    .sorted()
            )
        }
    }
    var query by remember { mutableStateOf("") }
    val filteredZones = remember(query, allZones) {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) allZones else allZones.filter { it.lowercase(Locale.US).contains(q) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Select Timezone",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Device timezone: $deviceZone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search timezone (e.g. Amman, London, Asia)…"
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = filteredZones,
                    key = { it }
                ) { zoneId ->
                    val isSelected = zoneId == currentZoneId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectZone(zoneId) },
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatZoneSummary(zoneId),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    painter = painterResource(R.drawable.ic_m3_check),
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePickerSheet(
    currentMethod: PrayerMethod,
    onDismiss: () -> Unit,
    onSelectMethod: (PrayerMethod) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp)
        ) {
            Text(
                text = "Calculation Source",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Select the astronomical prayer-time standard",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrayerMethod.values().forEach { method ->
                    val selected = method == currentMethod
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                onSelectMethod(method)
                                onDismiss()
                            },
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            }
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = method.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = method.summary,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onSelectMethod(method)
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_m3_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_m3_close),
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

internal fun formatZoneSummary(zoneId: String): String {
    return try {
        val z = ZoneId.of(zoneId)
        val sec = z.rules.getOffset(Instant.now()).totalSeconds
        val sign = if (sec >= 0) "+" else "-"
        val absSec = abs(sec)
        val h = absSec / 3600
        val m = (absSec % 3600) / 60
        val off = if (m == 0) {
            "UTC$sign$h"
        } else {
            String.format(Locale.US, "UTC%s%d:%02d", sign, h, m)
        }
        "$zoneId ($off)"
    } catch (_: Exception) {
        zoneId
    }
}
