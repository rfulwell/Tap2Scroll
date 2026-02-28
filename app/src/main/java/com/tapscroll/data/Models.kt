package com.tapscroll.data

/**
 * Represents the type of zone layout for scroll detection
 */
enum class ZoneType {
    EDGES,      // Top and bottom edge zones
    SIDES,      // Left and right split
    CORNERS     // Four corner zones
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
 * Configuration for an individual app
 */
data class AppConfig(
    val packageName: String,
    val appName: String,
    val enabled: Boolean = true,
    val customScrollDistance: Int? = null  // null = use global setting
)

/**
 * Zone configuration parameters
 */
data class ZoneConfig(
    val zoneType: ZoneType = ZoneType.EDGES,
    val topZonePercent: Float = 0.15f,
    val bottomZonePercent: Float = 0.15f,
    val leftZonePercent: Float = 0.20f,
    val rightZonePercent: Float = 0.20f,
    val cornerZonePercent: Float = 0.15f
)

/**
 * Complete user preferences
 */
data class UserPreferences(
    val serviceEnabled: Boolean = false,
    val scrollDistancePercent: Float = 0.75f,
    val scrollSpeed: ScrollSpeed = ScrollSpeed.MEDIUM,
    val zoneConfig: ZoneConfig = ZoneConfig(),
    val invertDirection: Boolean = false,
    val hapticFeedback: Boolean = true,
    val visualIndicator: Boolean = true,
    val avoidInteractiveElements: Boolean = true,
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
