package com.tapscroll.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import com.tapscroll.data.ScrollDirection
import com.tapscroll.data.ScrollZone
import com.tapscroll.data.UserPreferences
import com.tapscroll.util.ZoneCalculator

/**
 * Calculates scroll zones and dispatches scroll/tap gestures
 */
class GestureProcessor(
    private val service: AccessibilityService
) {
    companion object {
        private const val TAG = "GestureProcessor"
    }

    private val zoneCalculator = ZoneCalculator()

    private var cachedZones: List<ScrollZone> = emptyList()
    private var cachedScreenWidth: Int = 0
    private var cachedScreenHeight: Int = 0

    /**
     * Recalculate zones for current screen dimensions and preferences
     */
    fun updateZones(screenWidth: Int, screenHeight: Int, preferences: UserPreferences) {
        cachedScreenWidth = screenWidth
        cachedScreenHeight = screenHeight
        cachedZones = zoneCalculator.calculateZones(
            screenWidth,
            screenHeight,
            preferences.zoneConfig,
            preferences.invertDirection
        )
        Log.d(TAG, "Zones updated: ${cachedZones.size} zones for ${screenWidth}x${screenHeight}")
    }

    /**
     * Get the current calculated zones
     */
    fun getZones(): List<ScrollZone> = cachedZones

    /**
     * Dispatch a scroll gesture from the center of the screen
     */
    fun performScrollFromCenter(
        screenWidth: Int,
        screenHeight: Int,
        direction: ScrollDirection,
        distance: Int,
        duration: Long
    ): Boolean {
        val startX = screenWidth / 2f
        val startY = screenHeight / 2f

        val yDelta = when (direction) {
            ScrollDirection.DOWN -> -distance  // Swipe up to scroll content down
            ScrollDirection.UP -> distance     // Swipe down to scroll content up
        }

        val endY = (startY + yDelta).coerceIn(0f, screenHeight.toFloat())

        Log.d(TAG, "Scroll: direction=$direction, ($startX, $startY) -> ($startX, $endY), ${duration}ms")

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    Log.d(TAG, "Scroll gesture completed")
                }
                override fun onCancelled(gestureDescription: GestureDescription) {
                    Log.w(TAG, "Scroll gesture cancelled")
                }
            },
            null
        )
    }

    /**
     * Dispatch a tap gesture at the given coordinates (used to replay taps
     * on interactive elements back to the underlying app)
     */
    fun performTap(
        x: Float,
        y: Float,
        callback: AccessibilityService.GestureResultCallback? = null
    ): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return service.dispatchGesture(gesture, callback, null)
    }

    /**
     * Clear cached zones
     */
    fun clearCache() {
        cachedZones = emptyList()
        cachedScreenWidth = 0
        cachedScreenHeight = 0
    }
}
