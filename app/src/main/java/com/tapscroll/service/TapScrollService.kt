package com.tapscroll.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import com.tapscroll.data.OverlayFeedbackMode
import com.tapscroll.data.PreferenceStore
import com.tapscroll.data.ScrollDirection
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

        // Zone colors
        private const val ZONE_COLOR_UP = 0x6200EE     // purple (no alpha)
        private const val ZONE_COLOR_DOWN = 0xEE6200    // orange (no alpha)
        private const val FLASH_COLOR_SCROLL = 0x00CC00  // green (no alpha)
        private const val FLASH_COLOR_INTERACTIVE = 0xCCCC00 // yellow (no alpha)
        private const val FLASH_DURATION_MS = 300L

        var instance: TapScrollService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    private lateinit var preferenceStore: PreferenceStore
    private lateinit var gestureProcessor: GestureProcessor
    private lateinit var windowManager: WindowManager
    private lateinit var keyguardManager: KeyguardManager
    private val nodeTreeHelper = NodeTreeHelper()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Hides overlays when the screen turns off or is locked. */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen off, removing overlays")
                    removeZoneOverlays()
                }
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "Device unlocked, restoring overlays")
                    updateOverlayVisibility()
                }
            }
        }
    }

    private val zoneOverlays = mutableListOf<Pair<View, ScrollZone>>()
    private var currentPreferences: UserPreferences = UserPreferences()
    private var currentPackageName: String? = null
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var overlaysVisible: Boolean = false
    private var swipeThresholdPx: Int = 0
    private var scrollInFlight: Boolean = false

    // Per-gesture tracking (reset on each ACTION_DOWN)
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var gesturePassedThrough: Boolean = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        preferenceStore = PreferenceStore(applicationContext)
        gestureProcessor = GestureProcessor(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        swipeThresholdPx = ViewConfiguration.get(this).scaledTouchSlop * 2

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, filter)

        updateScreenDimensions()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")

        instance = this

        serviceScope.launch {
            preferenceStore.preferencesFlow.collectLatest { preferences ->
                Log.d(TAG, "Preferences updated (feedback=${preferences.overlayFeedbackMode})")
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
        try { unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
        removeZoneOverlays()
        gestureProcessor.clearCache()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val focusedPackage = getFocusedAppPackage() ?: return
                if (focusedPackage == currentPackageName) return

                currentPackageName = focusedPackage
                Log.d(TAG, "Foreground app changed: $focusedPackage")
                updateOverlayVisibility()
            }
        }
    }

    private fun getFocusedAppPackage(): String? {
        try {
            for (window in windows) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused) {
                    val rootNode = window.root ?: continue
                    val pkg = rootNode.packageName?.toString()
                    rootNode.recycle()
                    if (pkg != null) return pkg
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting focused app", e)
        }
        return null
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

    private fun recreateZoneOverlays() {
        removeZoneOverlays()
        if (shouldShowOverlays()) {
            createZoneOverlays()
        }
    }

    private fun createZoneOverlays() {
        val zones = gestureProcessor.getZones()
        val feedbackMode = currentPreferences.overlayFeedbackMode
        val opacity = currentPreferences.overlayOpacity

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
                if (feedbackMode == OverlayFeedbackMode.DEBUG) {
                    val baseColor = if (zone.scrollDirection == ScrollDirection.UP)
                        ZONE_COLOR_UP else ZONE_COLOR_DOWN
                    setBackgroundColor(colorWithOpacity(baseColor, opacity))
                }
                setOnTouchListener { view, event ->
                    handleZoneTouchEvent(event, capturedZone, view)
                }
            }

            try {
                windowManager.addView(overlayView, params)
                zoneOverlays.add(Pair(overlayView, zone))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create zone overlay", e)
            }
        }

        overlaysVisible = zoneOverlays.isNotEmpty()
        Log.d(TAG, "Created ${zoneOverlays.size} zone overlays (feedback=$feedbackMode)")
    }

    private fun removeZoneOverlays() {
        for ((overlay, _) in zoneOverlays) {
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
        // Don't show on lock screen or when device is locked
        if (keyguardManager.isKeyguardLocked) return false

        return currentPreferences.serviceEnabled &&
            currentPackageName != null &&
            currentPreferences.activeApps.any {
                it.packageName == currentPackageName && it.enabled
            }
    }

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
     * Gesture-aware touch handler.
     *
     * Scroll is dispatched on ACTION_DOWN (dispatchGesture doesn't work
     * reliably when called during ACTION_UP because the system is still
     * finalising the current touch sequence).
     *
     * Swipe detection: if the finger moves beyond swipeThresholdPx the
     * overlay hides itself so subsequent MOVE/UP events reach the app.
     *
     * Interactive elements: detected on ACTION_DOWN; overlay hides and
     * returns false so the full gesture passes through to the app.
     */
    private fun handleZoneTouchEvent(event: MotionEvent, zone: ScrollZone, zoneView: View): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Guard: if a scroll gesture is already in flight its synthetic
                // swipe can sweep through zone overlays — ignore those events.
                if (scrollInFlight) return true

                val x = event.rawX
                val y = event.rawY
                Log.d(TAG, "Zone touch DOWN at ($x, $y), direction=${zone.scrollDirection}")

                // Check for interactive elements — pass entire gesture through
                if (currentPreferences.avoidInteractiveElements) {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        val nodeAtTap = nodeTreeHelper.findNodeAtCoordinates(rootNode, x.toInt(), y.toInt())
                        val isInteractive = nodeAtTap != null && nodeTreeHelper.isInteractiveElement(nodeAtTap)
                        nodeAtTap?.recycle()
                        rootNode.recycle()

                        if (isInteractive) {
                            Log.d(TAG, "Interactive element detected, passing through")
                            flashZone(zoneView, zone, isInteractive = true)
                            gesturePassedThrough = true
                            zoneView.visibility = View.GONE
                            mainHandler.postDelayed({
                                zoneView.visibility = View.VISIBLE
                            }, 500)
                            return false
                        }
                    }
                }

                // Not interactive — perform scroll immediately.
                // Hide ALL overlays so the dispatched gesture's synthetic swipe
                // doesn't hit another zone overlay and cascade.
                downX = x
                downY = y
                gesturePassedThrough = false
                scrollInFlight = true
                setAllOverlaysVisible(false)

                val scrollDistance = (screenHeight * currentPreferences.scrollDistancePercent).toInt()
                val scrollDuration = currentPreferences.scrollSpeed.durationMs

                // Show the tapped zone briefly for the flash, then hide it
                // while the gesture sweeps. Other overlays stay hidden.
                zoneView.visibility = View.VISIBLE
                flashZone(zoneView, zone, isInteractive = false)
                if (currentPreferences.hapticFeedback) {
                    triggerHapticFeedback()
                }

                gestureProcessor.performScrollFromCenter(
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    direction = zone.scrollDirection,
                    distance = scrollDistance,
                    duration = scrollDuration,
                    callback = object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription) {
                            Log.d(TAG, "Scroll gesture completed")
                            scrollInFlight = false
                            setAllOverlaysVisible(true)
                        }
                        override fun onCancelled(gestureDescription: GestureDescription) {
                            Log.w(TAG, "Scroll gesture cancelled")
                            scrollInFlight = false
                            setAllOverlaysVisible(true)
                        }
                    }
                )
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (gesturePassedThrough) return false

                val dx = event.rawX - downX
                val dy = event.rawY - downY
                val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                if (distance > swipeThresholdPx) {
                    Log.d(TAG, "Swipe detected (dist=$distance), passing through")
                    gesturePassedThrough = true
                    zoneView.visibility = View.GONE
                    mainHandler.postDelayed({
                        zoneView.visibility = View.VISIBLE
                    }, 500)
                    return false
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (gesturePassedThrough) return false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                gesturePassedThrough = false
                return true
            }

            else -> return gesturePassedThrough.not()
        }
    }

    /**
     * Show or hide all zone overlay views. Used to prevent the dispatched
     * scroll gesture from hitting zone overlays as it sweeps across the screen.
     */
    private fun setAllOverlaysVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.INVISIBLE
        for ((overlay, _) in zoneOverlays) {
            overlay.visibility = visibility
        }
    }

    /**
     * Flash the zone overlay to indicate a tap was received.
     * Behavior depends on overlayFeedbackMode:
     *   INVISIBLE: no flash
     *   FLASH_ON_TAP: briefly shows zone with flash color then hides
     *   DEBUG: flashes then restores debug background color
     */
    private fun flashZone(zoneView: View, zone: ScrollZone, isInteractive: Boolean) {
        val mode = currentPreferences.overlayFeedbackMode
        if (mode == OverlayFeedbackMode.INVISIBLE) return

        val opacity = currentPreferences.overlayOpacity
        val flashBase = if (isInteractive) FLASH_COLOR_INTERACTIVE else FLASH_COLOR_SCROLL
        zoneView.setBackgroundColor(colorWithOpacity(flashBase, opacity.coerceAtLeast(0.4f)))

        mainHandler.postDelayed({
            when (mode) {
                OverlayFeedbackMode.FLASH_ON_TAP -> {
                    // Return to transparent
                    zoneView.setBackgroundColor(Color.TRANSPARENT)
                }
                OverlayFeedbackMode.DEBUG -> {
                    // Return to debug zone color
                    val normalBase = if (zone.scrollDirection == ScrollDirection.UP)
                        ZONE_COLOR_UP else ZONE_COLOR_DOWN
                    zoneView.setBackgroundColor(colorWithOpacity(normalBase, opacity))
                }
                else -> {}
            }
        }, FLASH_DURATION_MS)
    }

    /**
     * Combine a 0xRRGGBB color with a 0.0-1.0 opacity into an ARGB int.
     */
    private fun colorWithOpacity(rgb: Int, opacity: Float): Int {
        val alpha = (opacity * 255).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (rgb and 0x00FFFFFF)
    }

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
