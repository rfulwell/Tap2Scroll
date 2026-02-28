package com.tapscroll.util

import com.tapscroll.data.ScrollDirection
import com.tapscroll.data.ScrollZone
import com.tapscroll.data.ZoneConfig
import com.tapscroll.data.ZoneType

/**
 * Calculates scroll zones based on screen dimensions and user configuration
 */
class ZoneCalculator {

    /**
     * Calculate scroll zones for the given screen dimensions and configuration
     * 
     * @param screenWidth Width of the screen in pixels
     * @param screenHeight Height of the screen in pixels
     * @param config Zone configuration from user preferences
     * @param invertDirection Whether to invert scroll directions
     * @return List of ScrollZone objects representing active scroll regions
     */
    fun calculateZones(
        screenWidth: Int,
        screenHeight: Int,
        config: ZoneConfig,
        invertDirection: Boolean = false
    ): List<ScrollZone> {
        return when (config.zoneType) {
            ZoneType.EDGES -> calculateEdgeZones(screenWidth, screenHeight, config, invertDirection)
            ZoneType.SIDES -> calculateSideZones(screenWidth, screenHeight, config, invertDirection)
            ZoneType.CORNERS -> calculateCornerZones(screenWidth, screenHeight, config, invertDirection)
        }
    }

    /**
     * Calculate edge zones (top and bottom strips)
     * 
     * Layout:
     * ┌─────────────────────────┐
     * │      TOP ZONE           │  ← Scroll up
     * ├─────────────────────────┤
     * │      (no zone)          │
     * ├─────────────────────────┤
     * │      BOTTOM ZONE        │  ← Scroll down
     * └─────────────────────────┘
     */
    private fun calculateEdgeZones(
        screenWidth: Int,
        screenHeight: Int,
        config: ZoneConfig,
        invertDirection: Boolean
    ): List<ScrollZone> {
        val topHeight = screenHeight * config.topZonePercent
        val bottomHeight = screenHeight * config.bottomZonePercent

        val upDirection = if (invertDirection) ScrollDirection.DOWN else ScrollDirection.UP
        val downDirection = if (invertDirection) ScrollDirection.UP else ScrollDirection.DOWN

        return listOf(
            // Top zone - scrolls up (or down if inverted)
            ScrollZone(
                left = 0f,
                top = 0f,
                right = screenWidth.toFloat(),
                bottom = topHeight,
                scrollDirection = upDirection
            ),
            // Bottom zone - scrolls down (or up if inverted)
            ScrollZone(
                left = 0f,
                top = screenHeight - bottomHeight,
                right = screenWidth.toFloat(),
                bottom = screenHeight.toFloat(),
                scrollDirection = downDirection
            )
        )
    }

    /**
     * Calculate side zones (left and right strips)
     * 
     * Layout:
     * ┌────────────┬────────────┐
     * │            │            │
     * │   LEFT     │   RIGHT    │
     * │   ZONE     │   ZONE     │
     * │ Scroll Up  │ Scroll Down│
     * │            │            │
     * └────────────┴────────────┘
     */
    private fun calculateSideZones(
        screenWidth: Int,
        screenHeight: Int,
        config: ZoneConfig,
        invertDirection: Boolean
    ): List<ScrollZone> {
        val leftWidth = screenWidth * config.leftZonePercent
        val rightWidth = screenWidth * config.rightZonePercent

        val upDirection = if (invertDirection) ScrollDirection.DOWN else ScrollDirection.UP
        val downDirection = if (invertDirection) ScrollDirection.UP else ScrollDirection.DOWN

        return listOf(
            // Left zone - scrolls up
            ScrollZone(
                left = 0f,
                top = 0f,
                right = leftWidth,
                bottom = screenHeight.toFloat(),
                scrollDirection = upDirection
            ),
            // Right zone - scrolls down
            ScrollZone(
                left = screenWidth - rightWidth,
                top = 0f,
                right = screenWidth.toFloat(),
                bottom = screenHeight.toFloat(),
                scrollDirection = downDirection
            )
        )
    }

    /**
     * Calculate corner zones (four corners)
     * 
     * Layout:
     * ┌───────┬─────────┬───────┐
     * │ ↑ UP  │         │ ↑ UP  │
     * ├───────┤  (none) ├───────┤
     * │       │         │       │
     * ├───────┤         ├───────┤
     * │ ↓ DOWN│         │ ↓ DOWN│
     * └───────┴─────────┴───────┘
     */
    private fun calculateCornerZones(
        screenWidth: Int,
        screenHeight: Int,
        config: ZoneConfig,
        invertDirection: Boolean
    ): List<ScrollZone> {
        val cornerWidth = screenWidth * config.cornerZonePercent
        val cornerHeight = screenHeight * config.cornerZonePercent

        val upDirection = if (invertDirection) ScrollDirection.DOWN else ScrollDirection.UP
        val downDirection = if (invertDirection) ScrollDirection.UP else ScrollDirection.DOWN

        return listOf(
            // Top-left corner - scroll up
            ScrollZone(
                left = 0f,
                top = 0f,
                right = cornerWidth,
                bottom = cornerHeight,
                scrollDirection = upDirection
            ),
            // Top-right corner - scroll up
            ScrollZone(
                left = screenWidth - cornerWidth,
                top = 0f,
                right = screenWidth.toFloat(),
                bottom = cornerHeight,
                scrollDirection = upDirection
            ),
            // Bottom-left corner - scroll down
            ScrollZone(
                left = 0f,
                top = screenHeight - cornerHeight,
                right = cornerWidth,
                bottom = screenHeight.toFloat(),
                scrollDirection = downDirection
            ),
            // Bottom-right corner - scroll down
            ScrollZone(
                left = screenWidth - cornerWidth,
                top = screenHeight - cornerHeight,
                right = screenWidth.toFloat(),
                bottom = screenHeight.toFloat(),
                scrollDirection = downDirection
            )
        )
    }

    /**
     * Find which zone (if any) contains the given coordinates
     */
    fun findZoneAt(zones: List<ScrollZone>, x: Float, y: Float): ScrollZone? {
        return zones.find { it.contains(x, y) }
    }
}
