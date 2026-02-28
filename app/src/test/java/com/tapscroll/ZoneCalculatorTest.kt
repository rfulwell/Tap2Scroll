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
            topZonePercent = 0.15f,
            bottomZonePercent = 0.15f
        )

        val zones = calculator.calculateZones(screenWidth, screenHeight, config)

        assertEquals(2, zones.size)

        // Top zone
        val topZone = zones[0]
        assertEquals(0f, topZone.left, DELTA)
        assertEquals(0f, topZone.top, DELTA)
        assertEquals(screenWidth.toFloat(), topZone.right, DELTA)
        assertEquals(screenHeight * 0.15f, topZone.bottom, DELTA)
        assertEquals(ScrollDirection.UP, topZone.scrollDirection)

        // Bottom zone
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
            topZonePercent = 0.15f,
            bottomZonePercent = 0.15f
        )

        val zones = calculator.calculateZones(screenWidth, screenHeight, config, invertDirection = true)

        assertEquals(ScrollDirection.DOWN, zones[0].scrollDirection)  // Top zone inverted
        assertEquals(ScrollDirection.UP, zones[1].scrollDirection)    // Bottom zone inverted
    }

    @Test
    fun `side zones - calculates correct boundaries`() {
        val config = ZoneConfig(
            zoneType = ZoneType.SIDES,
            leftZonePercent = 0.20f,
            rightZonePercent = 0.20f
        )

        val zones = calculator.calculateZones(screenWidth, screenHeight, config)

        assertEquals(2, zones.size)

        // Left zone
        val leftZone = zones[0]
        assertEquals(0f, leftZone.left, DELTA)
        assertEquals(0f, leftZone.top, DELTA)
        assertEquals(screenWidth * 0.20f, leftZone.right, DELTA)
        assertEquals(screenHeight.toFloat(), leftZone.bottom, DELTA)
        assertEquals(ScrollDirection.UP, leftZone.scrollDirection)

        // Right zone
        val rightZone = zones[1]
        assertEquals(screenWidth * 0.80f, rightZone.left, DELTA)
        assertEquals(0f, rightZone.top, DELTA)
        assertEquals(screenWidth.toFloat(), rightZone.right, DELTA)
        assertEquals(screenHeight.toFloat(), rightZone.bottom, DELTA)
        assertEquals(ScrollDirection.DOWN, rightZone.scrollDirection)
    }

    @Test
    fun `corner zones - calculates four corners`() {
        val config = ZoneConfig(
            zoneType = ZoneType.CORNERS,
            cornerZonePercent = 0.15f
        )

        val zones = calculator.calculateZones(screenWidth, screenHeight, config)

        assertEquals(4, zones.size)

        // Top-left
        val topLeft = zones[0]
        assertEquals(0f, topLeft.left, DELTA)
        assertEquals(0f, topLeft.top, DELTA)
        assertEquals(ScrollDirection.UP, topLeft.scrollDirection)

        // Top-right
        val topRight = zones[1]
        assertEquals(screenWidth * 0.85f, topRight.left, DELTA)
        assertEquals(0f, topRight.top, DELTA)
        assertEquals(ScrollDirection.UP, topRight.scrollDirection)

        // Bottom-left
        val bottomLeft = zones[2]
        assertEquals(0f, bottomLeft.left, DELTA)
        assertEquals(screenHeight * 0.85f, bottomLeft.top, DELTA)
        assertEquals(ScrollDirection.DOWN, bottomLeft.scrollDirection)

        // Bottom-right
        val bottomRight = zones[3]
        assertEquals(screenWidth * 0.85f, bottomRight.left, DELTA)
        assertEquals(screenHeight * 0.85f, bottomRight.top, DELTA)
        assertEquals(ScrollDirection.DOWN, bottomRight.scrollDirection)
    }

    @Test
    fun `findZoneAt - returns correct zone`() {
        val config = ZoneConfig(
            zoneType = ZoneType.EDGES,
            topZonePercent = 0.15f,
            bottomZonePercent = 0.15f
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
            topZonePercent = 0.15f,
            bottomZonePercent = 0.15f
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
    fun `custom zone percentages`() {
        val config = ZoneConfig(
            zoneType = ZoneType.EDGES,
            topZonePercent = 0.25f,  // 25% top
            bottomZonePercent = 0.10f // 10% bottom
        )

        val zones = calculator.calculateZones(screenWidth, screenHeight, config)

        assertEquals(screenHeight * 0.25f, zones[0].bottom, DELTA)
        assertEquals(screenHeight * 0.90f, zones[1].top, DELTA)
    }
}
