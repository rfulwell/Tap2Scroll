package com.tapscroll.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.tapscroll.data.ScrollDirection
import com.tapscroll.data.ScrollZone
import com.tapscroll.data.UserPreferences
import com.tapscroll.util.NodeTreeHelper
import com.tapscroll.util.ZoneCalculator

/**
 * Processes tap events and determines whether to trigger scroll gestures
 */
class GestureProcessor(
    private val service: AccessibilityService
) {
    companion object {
        private const val TAG = "GestureProcessor"
    }

    private val zoneCalculator = ZoneCalculator()
    private val nodeTreeHelper = NodeTreeHelper()

    // Cached zones - recalculated when screen dimensions or preferences change
    private var cachedZones: List<ScrollZone> = emptyList()
    private var cachedScreenWidth: Int = 0
    private var cachedScreenHeight: Int = 0

    /**
     * Update cached zones when screen dimensions or preferences change
     */
    fun updateZones(screenWidth: Int, screenHeight: Int, preferences: UserPreferences) {
        if (screenWidth != cachedScreenWidth || 
            screenHeight != cachedScreenHeight ||
            cachedZones.isEmpty()) {
            
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
    }

    /**
     * Process a tap event and potentially trigger a scroll
     * 
     * @param x Tap x coordinate
     * @param y Tap y coordinate
     * @param preferences Current user preferences
     * @param rootNode Root accessibility node for interactive element detection
     * @return true if scroll was triggered, false otherwise
     */
    fun processTap(
        x: Float,
        y: Float,
        preferences: UserPreferences,
        rootNode: AccessibilityNodeInfo?
    ): ProcessResult {
        // Find which zone the tap is in
        val zone = zoneCalculator.findZoneAt(cachedZones, x, y)
        
        if (zone == null) {
            Log.d(TAG, "Tap at ($x, $y) not in any scroll zone")
            return ProcessResult.NotInZone
        }

        Log.d(TAG, "Tap at ($x, $y) in zone: direction=${zone.scrollDirection}")

        // Check for interactive elements if enabled
        if (preferences.avoidInteractiveElements && rootNode != null) {
            val nodeAtTap = nodeTreeHelper.findNodeAtCoordinates(rootNode, x.toInt(), y.toInt())
            
            if (nodeAtTap != null && nodeTreeHelper.isInteractiveElement(nodeAtTap)) {
                Log.d(TAG, "Tap on interactive element: ${nodeTreeHelper.describeNode(nodeAtTap)}")
                nodeAtTap.recycle()
                return ProcessResult.InteractiveElement
            }
            
            nodeAtTap?.recycle()
        }

        // Calculate scroll parameters
        val scrollDistance = (cachedScreenHeight * preferences.scrollDistancePercent).toInt()
        val scrollDuration = preferences.scrollSpeed.durationMs

        // Perform scroll
        val success = performScroll(
            startX = x,
            startY = cachedScreenHeight / 2f,  // Start from middle of screen
            direction = zone.scrollDirection,
            distance = scrollDistance,
            duration = scrollDuration
        )

        return if (success) {
            ProcessResult.ScrollTriggered(zone.scrollDirection)
        } else {
            ProcessResult.ScrollFailed
        }
    }

    /**
     * Create and dispatch a scroll gesture
     */
    private fun performScroll(
        startX: Float,
        startY: Float,
        direction: ScrollDirection,
        distance: Int,
        duration: Long
    ): Boolean {
        // Calculate end position
        // For scrolling DOWN (content moves up), we swipe UP (negative Y delta)
        // For scrolling UP (content moves down), we swipe DOWN (positive Y delta)
        val yDelta = when (direction) {
            ScrollDirection.DOWN -> -distance  // Swipe up to scroll content down
            ScrollDirection.UP -> distance     // Swipe down to scroll content up
        }

        val endY = (startY + yDelta).coerceIn(0f, cachedScreenHeight.toFloat())

        Log.d(TAG, "Performing scroll: direction=$direction, from ($startX, $startY) to ($startX, $endY), duration=$duration")

        // Create gesture path
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        // Create stroke description
        val stroke = GestureDescription.StrokeDescription(
            path,
            0,          // Start time (immediate)
            duration    // Duration in ms
        )

        // Build gesture
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        // Dispatch gesture
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
            null  // Handler (null = use main thread)
        )
    }

    /**
     * Clear cached zones (call when service stops or screen changes significantly)
     */
    fun clearCache() {
        cachedZones = emptyList()
        cachedScreenWidth = 0
        cachedScreenHeight = 0
    }

    /**
     * Result of processing a tap event
     */
    sealed class ProcessResult {
        /** Tap was not in any scroll zone */
        object NotInZone : ProcessResult()
        
        /** Tap was on an interactive element, scroll not performed */
        object InteractiveElement : ProcessResult()
        
        /** Scroll gesture was triggered */
        data class ScrollTriggered(val direction: ScrollDirection) : ProcessResult()
        
        /** Scroll gesture failed to dispatch */
        object ScrollFailed : ProcessResult()
    }
}
