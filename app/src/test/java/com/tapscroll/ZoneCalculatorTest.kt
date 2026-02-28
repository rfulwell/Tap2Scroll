package com.tapscroll

import com.tapscroll.data.ScrollDirection
import com.tapscroll.data.ZoneConfig
import com.tapscroll.data.ZoneType
import com.tapscroll.util.ZoneCalculator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ZoneCalculator
 */
class ZoneCalculatorTest {

    private lateinit var calculator: ZoneCalculator

    // Test screen dimensions (1080x2400 - typical phone)
    private val screenWidth = 1080
    private val screenHeight = 2400

    private val DELTA = 0.001f

    @Before
    fun setup() {
        calculator = ZoneCalculator()
    }

    @Test
    fun `edge zones - calculates correct boundaries`() {
        val config = ZoneConfig(
            zoneType = ZoneType.EDGES,
            scrollUpZoneStart = 0.0f,
            scrollUpZoneEnd = 0.15f,
            scrollDownZoneStart = 0.85f,
            scrollDownZoneEnd = 1.0f
        )

        val zones = calculator.calculateZones(screenWidth, screenHeight, config)

        assertEquals(2, zones.size)

        // Top zone (scroll up)
        val topZone = zones[0]
        assertEquals(0f, topZone.left, DELTA)
        assertEquals(0f, topZone.top, DELTA)
        assertEquals(screenWidth.toFloat(), topZone.right, DELTA)
        assertEquals(screenHeight * 0.15f, topZone.bottom, DELTA)
        assertEquals(ScrollDirection.UP, topZone.scrollDirection)

        // Bottom zone (scroll down)
        val bottomZone = zones[1]
        assertEquals(0f, bottomZone.left, DELTA)
        assertEquals(screenHeight * 0.85f, bottomZone.top, DELTA)
        assertEquals(screenWidth.toFloat(), bottomZone.right, DELTA)
        assertEquals(screenHeight.toFloat(), bottomZone.bottom, DELTA)
        assertEquals(ScrollDirection.DOWN, bottomZone.scrollDirection)
    }

    @Test
    fun `edge zones - inverted directions`() {
        val config = ZoneConfig(
            zoneType = ZoneType.EDGES,
            scrollUpZoneStart = 0.0f,
            scrollUpZoneEnd = 0.15f,
            scrollDownZoneStart = 0.85f,
            scrollDownZoneEnd = 1.0f
        )

        val zones = calculator.calculateZones(screenWidth, screenHeight, config, invertDirection = true)

        assertEquals(ScrollDirection.DOWN, zones[0].scrollDirection)  // Top zone inverted
        assertEquals(ScrollDirection.UP, zones[1].scrollDirection)    // Bottom zone inverted
    }

    @Test
    fun `side zones - calculates correct boundaries`() {
        val config = ZoneConfig(
            zoneType = ZoneType.SIDES,
            scrollUpZoneStart = 0.0f,
            scrollUpZoneEnd = 0.20f,
            scrollDownZoneStart = 0.80f,
            scrollDownZoneEnd = 1.0f
        )

        val zones = calculator.calculateZones(screenWidth, screenHeight, config)

        assertEquals(2, zones.size)

        // Left zone (scroll up)
        val leftZone = zones[0]
        assertEquals(0f, leftZone.left, DELTA)
        assertEquals(0f, leftZone.top, DELTA)
        assertEquals(screenWidth * 0.20f, leftZone.right, DELTA)
        assertEquals(screenHeight.toFloat(), leftZone.bottom, DELTA)
        assertEquals(ScrollDirection.UP, leftZone.scrollDirection)

        // Right zone (scroll down)
        val rightZone = zones[1]
        assertEquals(screenWidth * 0.80f, rightZone.left, DELTA)
        assertEquals(0f, rightZone.top, DELTA)
        assertEquals(screenWidth.toFloat(), rightZone.right, DELTA)
        assertEquals(screenHeight.toFloat(), rightZone.bottom, DELTA)
        assertEquals(ScrollDirection.DOWN, rightZone.scrollDirection)
    }

    @Test
    fun `edge zones - with gaps`() {
        // Zones positioned with gaps at top, middle, and bottom
        val config = ZoneConfig(
            zoneType = ZoneType.EDGES,
            scrollUpZoneStart = 0.10f,   // 10% gap at top
            scrollUpZoneEnd = 0.30f,
            scrollDownZoneStart = 0.70f,
            scrollDownZoneEnd = 0.90f     // 10% gap at bottom
        )

        val zones = calculator.calculateZones(screenWidth, screenHeight, config)

        assertEquals(2, zones.size)

        // Up zone starts 10% from top
        assertEquals(screenHeight * 0.10f, zones[0].top, DELTA)
        assertEquals(screenHeight * 0.30f, zones[0].bottom, DELTA)

        // Down zone ends 10% from bottom
        assertEquals(screenHeight * 0.70f, zones[1].top, DELTA)
        assertEquals(screenHeight * 0.90f, zones[1].bottom, DELTA)
    }

    @Test
    fun `findZoneAt - returns correct zone`() {
        val config = ZoneConfig(
            zoneType = ZoneType.EDGES,
            scrollUpZoneStart = 0.0f,
            scrollUpZoneEnd = 0.15f,
            scrollDownZoneStart = 0.85f,
            scrollDownZoneEnd = 1.0f
        )

        val zones = calculator.calculateZones(screenWidth, screenHeight, config)

        // Point in top zone
        val topZone = calculator.findZoneAt(zones, 500f, 100f)
        assertNotNull(topZone)
        assertEquals(ScrollDirection.UP, topZone?.scrollDirection)

        // Point in bottom zone
        val bottomZone = calculator.findZoneAt(zones, 500f, 2300f)
        assertNotNull(bottomZone)
        assertEquals(ScrollDirection.DOWN, bottomZone?.scrollDirection)

        // Point in middle (no zone)
        val noZone = calculator.findZoneAt(zones, 500f, 1200f)
        assertNull(noZone)
    }

    @Test
    fun `zone contains - boundary conditions`() {
        val config = ZoneConfig(
            zoneType = ZoneType.EDGES,
            scrollUpZoneStart = 0.0f,
            scrollUpZoneEnd = 0.15f,
            scrollDownZoneStart = 0.85f,
            scrollDownZoneEnd = 1.0f
        )

        val zones = calculator.calculateZones(screenWidth, screenHeight, config)
        val topZone = zones[0]

        // Exact boundaries should be included
        assertTrue(topZone.contains(0f, 0f))
        assertTrue(topZone.contains(screenWidth.toFloat(), 0f))
        assertTrue(topZone.contains(0f, screenHeight * 0.15f))

        // Just outside should not be included
        assertFalse(topZone.contains(500f, screenHeight * 0.15f + 1f))
    }

    @Test
    fun `custom zone sizes`() {
        val config = ZoneConfig(
            zoneType = ZoneType.EDGES,
            scrollUpZoneStart = 0.0f,
            scrollUpZoneEnd = 0.25f,
            scrollDownZoneStart = 0.90f,
            scrollDownZoneEnd = 1.0f
        )

        val zones = calculator.calculateZones(screenWidth, screenHeight, config)

        assertEquals(screenHeight * 0.25f, zones[0].bottom, DELTA)
        assertEquals(screenHeight * 0.90f, zones[1].top, DELTA)
    }
}
