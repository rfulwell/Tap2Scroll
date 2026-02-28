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
        }
    }

    /**
     * Calculate edge zones (horizontal strips at configurable vertical positions)
     *
     * Layout:
     * ┌─────────────────────────┐  ← scrollUpZoneStart
     * │      SCROLL UP ZONE     │
     * ├─────────────────────────┤  ← scrollUpZoneEnd
     * │        (gap)            │
     * ├─────────────────────────┤  ← scrollDownZoneStart
     * │      SCROLL DOWN ZONE   │
     * └─────────────────────────┘  ← scrollDownZoneEnd
     */
    private fun calculateEdgeZones(
        screenWidth: Int,
        screenHeight: Int,
        config: ZoneConfig,
        invertDirection: Boolean
    ): List<ScrollZone> {
        val upDirection = if (invertDirection) ScrollDirection.DOWN else ScrollDirection.UP
        val downDirection = if (invertDirection) ScrollDirection.UP else ScrollDirection.DOWN

        return listOf(
            ScrollZone(
                left = 0f,
                top = screenHeight * config.scrollUpZoneStart,
                right = screenWidth.toFloat(),
                bottom = screenHeight * config.scrollUpZoneEnd,
                scrollDirection = upDirection
            ),
            ScrollZone(
                left = 0f,
                top = screenHeight * config.scrollDownZoneStart,
                right = screenWidth.toFloat(),
                bottom = screenHeight * config.scrollDownZoneEnd,
                scrollDirection = downDirection
            )
        )
    }

    /**
     * Calculate side zones (vertical strips at configurable horizontal positions)
     *
     * Layout:
     * ┌────┬──────────────┬────┐
     * │    │              │    │
     * │ UP │    (gap)     │DOWN│
     * │    │              │    │
     * └────┴──────────────┴────┘
     *   ↑ scrollUpZoneStart/End  scrollDownZoneStart/End ↑
     */
    private fun calculateSideZones(
        screenWidth: Int,
        screenHeight: Int,
        config: ZoneConfig,
        invertDirection: Boolean
    ): List<ScrollZone> {
        val upDirection = if (invertDirection) ScrollDirection.DOWN else ScrollDirection.UP
        val downDirection = if (invertDirection) ScrollDirection.UP else ScrollDirection.DOWN

        return listOf(
            ScrollZone(
                left = screenWidth * config.scrollUpZoneStart,
                top = 0f,
                right = screenWidth * config.scrollUpZoneEnd,
                bottom = screenHeight.toFloat(),
                scrollDirection = upDirection
            ),
            ScrollZone(
                left = screenWidth * config.scrollDownZoneStart,
                top = 0f,
                right = screenWidth * config.scrollDownZoneEnd,
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
