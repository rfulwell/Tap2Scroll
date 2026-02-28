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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tapscroll.data.ZoneType
import com.tapscroll.ui.components.*

/**
 * Main settings screen
 */
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
                }
            }

            // Zone Preview
            item {
                SectionHeader(title = "Zone Preview")
                ZonePreviewCard(
                    zoneType = preferences.zoneConfig.zoneType,
                    topZonePercent = preferences.zoneConfig.topZonePercent,
                    bottomZonePercent = preferences.zoneConfig.bottomZonePercent,
                    leftZonePercent = preferences.zoneConfig.leftZonePercent,
                    rightZonePercent = preferences.zoneConfig.rightZonePercent,
                    cornerZonePercent = preferences.zoneConfig.cornerZonePercent
                )
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

            // Debug Section
            item {
                SectionHeader(title = "Debug")
                SettingsCard {
                    SwitchRow(
                        title = "Debug overlay",
                        description = "Show colored zones and flash on tap",
                        checked = preferences.debugMode,
                        onCheckedChange = { viewModel.setDebugMode(it) }
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
 * Visual preview of zone layout
 */
@Composable
fun ZonePreviewCard(
    zoneType: ZoneType,
    topZonePercent: Float,
    bottomZonePercent: Float,
    leftZonePercent: Float,
    rightZonePercent: Float,
    cornerZonePercent: Float,
    modifier: Modifier = Modifier
) {
    SettingsCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.5f) // Phone-like aspect ratio
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val primaryColor = androidx.compose.ui.graphics.Color(0xFF6200EE)
                val backgroundColor = androidx.compose.ui.graphics.Color(0xFFE0E0E0)
                
                // Background
                drawRect(color = backgroundColor)
                
                when (zoneType) {
                    ZoneType.EDGES -> {
                        // Top zone
                        drawRect(
                            color = primaryColor.copy(alpha = 0.3f),
                            size = androidx.compose.ui.geometry.Size(
                                size.width,
                                size.height * topZonePercent
                            )
                        )
                        // Bottom zone
                        drawRect(
                            color = primaryColor.copy(alpha = 0.3f),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                0f,
                                size.height * (1 - bottomZonePercent)
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                size.width,
                                size.height * bottomZonePercent
                            )
                        )
                    }
                    ZoneType.SIDES -> {
                        // Left zone
                        drawRect(
                            color = primaryColor.copy(alpha = 0.3f),
                            size = androidx.compose.ui.geometry.Size(
                                size.width * leftZonePercent,
                                size.height
                            )
                        )
                        // Right zone
                        drawRect(
                            color = primaryColor.copy(alpha = 0.3f),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                size.width * (1 - rightZonePercent),
                                0f
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                size.width * rightZonePercent,
                                size.height
                            )
                        )
                    }
                    ZoneType.CORNERS -> {
                        val cornerW = size.width * cornerZonePercent
                        val cornerH = size.height * cornerZonePercent
                        
                        // Top-left
                        drawRect(
                            color = primaryColor.copy(alpha = 0.3f),
                            size = androidx.compose.ui.geometry.Size(cornerW, cornerH)
                        )
                        // Top-right
                        drawRect(
                            color = primaryColor.copy(alpha = 0.3f),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                size.width - cornerW,
                                0f
                            ),
                            size = androidx.compose.ui.geometry.Size(cornerW, cornerH)
                        )
                        // Bottom-left
                        drawRect(
                            color = primaryColor.copy(alpha = 0.3f),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                0f,
                                size.height - cornerH
                            ),
                            size = androidx.compose.ui.geometry.Size(cornerW, cornerH)
                        )
                        // Bottom-right
                        drawRect(
                            color = primaryColor.copy(alpha = 0.3f),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                size.width - cornerW,
                                size.height - cornerH
                            ),
                            size = androidx.compose.ui.geometry.Size(cornerW, cornerH)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = when (zoneType) {
                ZoneType.EDGES -> "Tap top to scroll up, bottom to scroll down"
                ZoneType.SIDES -> "Tap left to scroll up, right to scroll down"
                ZoneType.CORNERS -> "Tap top corners to scroll up, bottom corners to scroll down"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
