package com.tapscroll.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import com.tapscroll.data.PreferenceStore
import com.tapscroll.data.ScrollZone
import com.tapscroll.data.UserPreferences
import com.tapscroll.util.NodeTreeHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Main accessibility service that handles tap-to-scroll functionality.
 * Uses per-zone overlay windows so touches outside zones pass through normally.
 */
class TapScrollService : AccessibilityService() {

    companion object {
        private const val TAG = "TapScrollService"

        var instance: TapScrollService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    private lateinit var preferenceStore: PreferenceStore
    private lateinit var gestureProcessor: GestureProcessor
    private lateinit var windowManager: WindowManager
    private val nodeTreeHelper = NodeTreeHelper()

    private val zoneOverlays = mutableListOf<View>()
    private var currentPreferences: UserPreferences = UserPreferences()
    private var currentPackageName: String? = null
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var overlaysVisible: Boolean = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        preferenceStore = PreferenceStore(applicationContext)
        gestureProcessor = GestureProcessor(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        updateScreenDimensions()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")

        instance = this

        serviceScope.launch {
            preferenceStore.preferencesFlow.collectLatest { preferences ->
                Log.d(TAG, "Preferences updated")
                currentPreferences = preferences
                gestureProcessor.updateZones(screenWidth, screenHeight, preferences)
                recreateZoneOverlays()
            }
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.d(TAG, "Service unbound")
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        instance = null
        serviceScope.cancel()
        removeZoneOverlays()
        gestureProcessor.clearCache()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString()
                if (packageName != null && packageName != currentPackageName) {
                    currentPackageName = packageName
                    Log.d(TAG, "Foreground app changed: $packageName")
                    updateOverlayVisibility()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed")

        updateScreenDimensions()
        gestureProcessor.updateZones(screenWidth, screenHeight, currentPreferences)
        recreateZoneOverlays()
    }

    private fun updateScreenDimensions() {
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        Log.d(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}")
    }

    /**
     * Recreate zone overlays (called when zones or app changes)
     */
    private fun recreateZoneOverlays() {
        removeZoneOverlays()
        if (shouldShowOverlays()) {
            createZoneOverlays()
        }
    }

    /**
     * Create individual overlay windows for each scroll zone.
     * Only zone areas get overlays; the rest of the screen passes touches through.
     */
    private fun createZoneOverlays() {
        val zones = gestureProcessor.getZones()

        for (zone in zones) {
            val width = (zone.right - zone.left).toInt()
            val height = (zone.bottom - zone.top).toInt()
            if (width <= 0 || height <= 0) continue

            val params = WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = zone.left.toInt()
                y = zone.top.toInt()
            }

            val capturedZone = zone
            val overlayView = FrameLayout(this).apply {
                isClickable = true
                setOnTouchListener { view, event ->
                    handleZoneTouchEvent(event, capturedZone, view)
                }
            }

            try {
                windowManager.addView(overlayView, params)
                zoneOverlays.add(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create zone overlay", e)
            }
        }

        overlaysVisible = zoneOverlays.isNotEmpty()
        Log.d(TAG, "Created ${zoneOverlays.size} zone overlays")
    }

    /**
     * Remove all zone overlay windows
     */
    private fun removeZoneOverlays() {
        for (overlay in zoneOverlays) {
            try {
                windowManager.removeView(overlay)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove zone overlay", e)
            }
        }
        zoneOverlays.clear()
        overlaysVisible = false
    }

    private fun shouldShowOverlays(): Boolean {
        return currentPackageName != null &&
            currentPreferences.activeApps.any {
                it.packageName == currentPackageName && it.enabled
            }
    }

    /**
     * Show or hide overlays based on current foreground app
     */
    private fun updateOverlayVisibility() {
        val shouldShow = shouldShowOverlays()
        if (shouldShow && !overlaysVisible) {
            createZoneOverlays()
        } else if (!shouldShow && overlaysVisible) {
            removeZoneOverlays()
        }
        Log.d(TAG, "Overlay visibility: $shouldShow (app: $currentPackageName)")
    }

    /**
     * Handle a touch event on a zone overlay
     */
    private fun handleZoneTouchEvent(event: MotionEvent, zone: ScrollZone, zoneView: View): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true

        val x = event.rawX
        val y = event.rawY

        Log.d(TAG, "Zone tap at ($x, $y), direction=${zone.scrollDirection}")

        // Check for interactive elements if enabled
        if (currentPreferences.avoidInteractiveElements) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val nodeAtTap = nodeTreeHelper.findNodeAtCoordinates(rootNode, x.toInt(), y.toInt())

                if (nodeAtTap != null && nodeTreeHelper.isInteractiveElement(nodeAtTap)) {
                    Log.d(TAG, "Tap on interactive element, replaying tap")
                    nodeAtTap.recycle()
                    rootNode.recycle()
                    replayTap(x, y, zoneView)
                    return true
                }

                nodeAtTap?.recycle()
                rootNode.recycle()
            }
        }

        // Perform scroll from center of screen (outside any zone overlay)
        val scrollDistance = (screenHeight * currentPreferences.scrollDistancePercent).toInt()
        val scrollDuration = currentPreferences.scrollSpeed.durationMs

        val success = gestureProcessor.performScrollFromCenter(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            direction = zone.scrollDirection,
            distance = scrollDistance,
            duration = scrollDuration
        )

        if (success && currentPreferences.hapticFeedback) {
            triggerHapticFeedback()
        }

        return true
    }

    /**
     * Replay a tap to the underlying app by temporarily hiding the zone overlay
     * so the dispatched tap gesture reaches the app beneath
     */
    private fun replayTap(x: Float, y: Float, zoneView: View) {
        zoneView.visibility = View.GONE

        gestureProcessor.performTap(x, y, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                zoneView.visibility = View.VISIBLE
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                zoneView.visibility = View.VISIBLE
            }
        })
    }

    /**
     * Trigger haptic feedback
     */
    private fun triggerHapticFeedback() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }
}
