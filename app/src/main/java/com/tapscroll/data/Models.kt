package com.tapscroll.data

/**
 * Represents the type of zone layout for scroll detection
 */
enum class ZoneType {
    EDGES,      // Top and bottom edge zones
    SIDES       // Left and right split
}

/**
 * Scroll direction
 */
enum class ScrollDirection {
    UP,
    DOWN
}

/**
 * Scroll speed presets
 */
enum class ScrollSpeed(val durationMs: Long) {
    SLOW(350),
    MEDIUM(200),
    FAST(100)
}

/**
 * Visual feedback mode for overlays
 */
enum class OverlayFeedbackMode {
    INVISIBLE,      // Overlays are fully invisible, no visual feedback
    FLASH_ON_TAP,   // Overlays invisible until tapped, then briefly flash
    DEBUG           // Overlays always visible with colored backgrounds + flash
}

/**
 * Configuration for an individual app
 */
data class AppConfig(
    val packageName: String,
    val appName: String,
    val enabled: Boolean = true,
    val customScrollDistance: Int? = null  // null = use global setting
)

/**
 * Zone configuration parameters.
 * Start/end values are fractions of the relevant screen dimension:
 *   EDGES: fractions of screen height (0.0 = top, 1.0 = bottom)
 *   SIDES: fractions of screen width (0.0 = left, 1.0 = right)
 */
data class ZoneConfig(
    val zoneType: ZoneType = ZoneType.EDGES,
    val scrollUpZoneStart: Float = 0.0f,
    val scrollUpZoneEnd: Float = 0.15f,
    val scrollDownZoneStart: Float = 0.85f,
    val scrollDownZoneEnd: Float = 1.0f
)

/**
 * Complete user preferences
 */
data class UserPreferences(
    val serviceEnabled: Boolean = true,
    val scrollDistancePercent: Float = 0.75f,
    val scrollSpeed: ScrollSpeed = ScrollSpeed.MEDIUM,
    val zoneConfig: ZoneConfig = ZoneConfig(),
    val invertDirection: Boolean = false,
    val hapticFeedback: Boolean = true,
    val visualIndicator: Boolean = true,
    val avoidInteractiveElements: Boolean = true,
    val scrollUpEnabled: Boolean = true,
    val scrollDownEnabled: Boolean = true,
    val overlayFeedbackMode: OverlayFeedbackMode = OverlayFeedbackMode.FLASH_ON_TAP,
    val overlayOpacity: Float = 0.3f,
    val activeApps: List<AppConfig> = listOf(
        AppConfig("com.brave.browser", "Brave Browser", true)
    )
)

/**
 * Represents a calculated scroll zone on screen
 */
data class ScrollZone(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val scrollDirection: ScrollDirection
) {
    fun contains(x: Float, y: Float): Boolean {
        return x >= left && x <= right && y >= top && y <= bottom
    }
}
