package com.tapscroll.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tapscroll.data.OverlayFeedbackMode
import com.tapscroll.data.ZoneType
import com.tapscroll.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val preferences by viewModel.preferences.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    var showAppSelector by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tap to Scroll") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Service status banner
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ServiceStatusBanner(
                    isRunning = isServiceRunning,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            }

            // Master on/off toggle
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tap to Scroll",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (preferences.serviceEnabled) "Active" else "Paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (preferences.serviceEnabled)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.serviceEnabled,
                            onCheckedChange = { viewModel.setServiceEnabled(it) }
                        )
                    }
                }
            }

            // Scroll Settings Section
            item {
                SectionHeader(title = "Scroll Settings")
                SettingsCard {
                    SliderRow(
                        title = "Scroll Distance",
                        value = preferences.scrollDistancePercent,
                        onValueChange = { viewModel.setScrollDistance(it) },
                        valueRange = 0.1f..1f,
                        valueLabel = "${(preferences.scrollDistancePercent * 100).toInt()}% of screen"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SpeedSelector(
                        selectedSpeed = preferences.scrollSpeed,
                        onSpeedSelected = { viewModel.setScrollSpeed(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ZoneTypeSelector(
                        selectedType = preferences.zoneConfig.zoneType,
                        onTypeSelected = { viewModel.setZoneType(it) }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    SwitchRow(
                        title = "Scroll up zone",
                        description = if (preferences.zoneConfig.zoneType == ZoneType.EDGES)
                            "Top area of screen" else "Left side of screen",
                        checked = preferences.scrollUpEnabled,
                        onCheckedChange = { viewModel.setScrollUpEnabled(it) }
                    )

                    SwitchRow(
                        title = "Scroll down zone",
                        description = if (preferences.zoneConfig.zoneType == ZoneType.EDGES)
                            "Bottom area of screen" else "Right side of screen",
                        checked = preferences.scrollDownEnabled,
                        onCheckedChange = { viewModel.setScrollDownEnabled(it) }
                    )
                }
            }

            // Zone Preview with sliders
            item {
                SectionHeader(title = "Zone Preview")
                ZonePreviewCard(
                    zoneConfig = preferences.zoneConfig,
                    onZoneConfigChange = { viewModel.setZoneConfig(it) }
                )
            }

            // Overlay Appearance Section
            item {
                SectionHeader(title = "Overlay Appearance")
                SettingsCard {
                    FeedbackModeSelector(
                        selectedMode = preferences.overlayFeedbackMode,
                        onModeSelected = { viewModel.setOverlayFeedbackMode(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SliderRow(
                        title = "Overlay Opacity",
                        value = preferences.overlayOpacity,
                        onValueChange = { viewModel.setOverlayOpacity(it) },
                        valueRange = 0.05f..1f,
                        valueLabel = "${(preferences.overlayOpacity * 100).toInt()}%"
                    )
                }
            }

            // Active Apps Section
            item {
                SectionHeader(title = "Active Apps")
                SettingsCard {
                    if (preferences.activeApps.isEmpty()) {
                        Text(
                            text = "No apps selected. Add apps to enable tap-to-scroll.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // App list
            items(preferences.activeApps) { app ->
                SettingsCard {
                    AppItemRow(
                        app = app,
                        onToggle = { viewModel.toggleAppEnabled(app.packageName) },
                        onRemove = { viewModel.removeApp(app.packageName) }
                    )
                }
            }

            // Add app button
            item {
                OutlinedButton(
                    onClick = { showAppSelector = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add App")
                }
            }

            // Advanced Settings Section
            item {
                SectionHeader(title = "Advanced")
                SettingsCard {
                    SwitchRow(
                        title = "Invert scroll direction",
                        description = "Swap up and down scroll behavior",
                        checked = preferences.invertDirection,
                        onCheckedChange = { viewModel.setInvertDirection(it) }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    SwitchRow(
                        title = "Haptic feedback",
                        description = "Vibrate when scroll is triggered",
                        checked = preferences.hapticFeedback,
                        onCheckedChange = { viewModel.setHapticFeedback(it) }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    SwitchRow(
                        title = "Avoid interactive elements",
                        description = "Don't scroll when tapping links or buttons",
                        checked = preferences.avoidInteractiveElements,
                        onCheckedChange = { viewModel.setAvoidInteractiveElements(it) }
                    )
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // App selector dialog
    if (showAppSelector) {
        AppSelectorDialog(
            installedApps = installedApps,
            activePackages = preferences.activeApps.map { it.packageName }.toSet(),
            onAppSelected = { packageName, appName ->
                viewModel.addApp(packageName, appName)
            },
            onDismiss = { showAppSelector = false }
        )
    }
}

/**
 * Visual preview of zone layout with position sliders
 */
@Composable
fun ZonePreviewCard(
    zoneConfig: com.tapscroll.data.ZoneConfig,
    onZoneConfigChange: (com.tapscroll.data.ZoneConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.5f)
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val upColor = androidx.compose.ui.graphics.Color(0xFF6200EE)
                val downColor = androidx.compose.ui.graphics.Color(0xFFEE6200)
                val backgroundColor = androidx.compose.ui.graphics.Color(0xFFE0E0E0)

                drawRect(color = backgroundColor)

                when (zoneConfig.zoneType) {
                    ZoneType.EDGES -> {
                        // Scroll-up zone (top area)
                        drawRect(
                            color = upColor.copy(alpha = 0.3f),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                0f,
                                size.height * zoneConfig.scrollUpZoneStart
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                size.width,
                                size.height * (zoneConfig.scrollUpZoneEnd - zoneConfig.scrollUpZoneStart)
                            )
                        )
                        // Scroll-down zone (bottom area)
                        drawRect(
                            color = downColor.copy(alpha = 0.3f),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                0f,
                                size.height * zoneConfig.scrollDownZoneStart
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                size.width,
                                size.height * (zoneConfig.scrollDownZoneEnd - zoneConfig.scrollDownZoneStart)
                            )
                        )
                    }
                    ZoneType.SIDES -> {
                        // Scroll-up zone (left area)
                        drawRect(
                            color = upColor.copy(alpha = 0.3f),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                size.width * zoneConfig.scrollUpZoneStart,
                                0f
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                size.width * (zoneConfig.scrollUpZoneEnd - zoneConfig.scrollUpZoneStart),
                                size.height
                            )
                        )
                        // Scroll-down zone (right area)
                        drawRect(
                            color = downColor.copy(alpha = 0.3f),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                size.width * zoneConfig.scrollDownZoneStart,
                                0f
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                size.width * (zoneConfig.scrollDownZoneEnd - zoneConfig.scrollDownZoneStart),
                                size.height
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (zoneConfig.zoneType) {
                ZoneType.EDGES -> "Tap top zone to scroll up, bottom zone to scroll down"
                ZoneType.SIDES -> "Tap left zone to scroll up, right zone to scroll down"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Zone position sliders
        val isEdges = zoneConfig.zoneType == ZoneType.EDGES
        val upLabel = if (isEdges) "Scroll up zone" else "Scroll up zone"
        val downLabel = if (isEdges) "Scroll down zone" else "Scroll down zone"
        val axisLabel = if (isEdges) "screen height" else "screen width"

        Text(
            text = upLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = androidx.compose.ui.graphics.Color(0xFF6200EE)
        )
        SliderRow(
            title = if (isEdges) "Top edge" else "Left edge",
            value = zoneConfig.scrollUpZoneStart,
            onValueChange = {
                val clamped = it.coerceAtMost(zoneConfig.scrollUpZoneEnd - 0.02f)
                onZoneConfigChange(zoneConfig.copy(scrollUpZoneStart = clamped))
            },
            valueRange = 0f..0.98f,
            valueLabel = "${(zoneConfig.scrollUpZoneStart * 100).toInt()}%"
        )
        SliderRow(
            title = if (isEdges) "Bottom edge" else "Right edge",
            value = zoneConfig.scrollUpZoneEnd,
            onValueChange = {
                val clamped = it.coerceAtLeast(zoneConfig.scrollUpZoneStart + 0.02f)
                onZoneConfigChange(zoneConfig.copy(scrollUpZoneEnd = clamped))
            },
            valueRange = 0.02f..1f,
            valueLabel = "${(zoneConfig.scrollUpZoneEnd * 100).toInt()}%"
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = downLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = androidx.compose.ui.graphics.Color(0xFFEE6200)
        )
        SliderRow(
            title = if (isEdges) "Top edge" else "Left edge",
            value = zoneConfig.scrollDownZoneStart,
            onValueChange = {
                val clamped = it.coerceAtMost(zoneConfig.scrollDownZoneEnd - 0.02f)
                onZoneConfigChange(zoneConfig.copy(scrollDownZoneStart = clamped))
            },
            valueRange = 0f..0.98f,
            valueLabel = "${(zoneConfig.scrollDownZoneStart * 100).toInt()}%"
        )
        SliderRow(
            title = if (isEdges) "Bottom edge" else "Right edge",
            value = zoneConfig.scrollDownZoneEnd,
            onValueChange = {
                val clamped = it.coerceAtLeast(zoneConfig.scrollDownZoneStart + 0.02f)
                onZoneConfigChange(zoneConfig.copy(scrollDownZoneEnd = clamped))
            },
            valueRange = 0.02f..1f,
            valueLabel = "${(zoneConfig.scrollDownZoneEnd * 100).toInt()}%"
        )
    }
}

/**
 * Feedback mode selector (Invisible / Flash on Tap / Debug)
 */
@Composable
fun FeedbackModeSelector(
    selectedMode: OverlayFeedbackMode,
    onModeSelected: (OverlayFeedbackMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf("Invisible", "Flash on Tap", "Debug")
    val selectedIndex = when (selectedMode) {
        OverlayFeedbackMode.INVISIBLE -> 0
        OverlayFeedbackMode.FLASH_ON_TAP -> 1
        OverlayFeedbackMode.DEBUG -> 2
    }

    SegmentedButtonRow(
        title = "Visual Feedback",
        options = options,
        selectedIndex = selectedIndex,
        onSelectionChange = { index ->
            val mode = when (index) {
                0 -> OverlayFeedbackMode.INVISIBLE
                1 -> OverlayFeedbackMode.FLASH_ON_TAP
                else -> OverlayFeedbackMode.DEBUG
            }
            onModeSelected(mode)
        },
        modifier = modifier
    )
}

/**
 * Dialog for selecting apps to add
 */
@Composable
fun AppSelectorDialog(
    installedApps: List<InstalledAppInfo>,
    activePackages: Set<String>,
    onAppSelected: (packageName: String, appName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = installedApps.filter { app ->
        app.packageName !in activePackages &&
            (searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add App") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(filteredApps.take(50)) { app ->
                        ListItem(
                            headlineContent = { Text(app.appName) },
                            supportingContent = { Text(app.packageName) },
                            modifier = Modifier.clickable {
                                onAppSelected(app.packageName, app.appName)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
