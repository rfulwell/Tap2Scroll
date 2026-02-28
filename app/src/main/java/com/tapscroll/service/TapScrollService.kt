package com.tapscroll.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
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
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
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

        // Debug colors
        private const val DEBUG_ZONE_UP = 0x306200EE    // semi-transparent blue
        private const val DEBUG_ZONE_DOWN = 0x30EE6200   // semi-transparent orange
        private const val DEBUG_FLASH_SCROLL = 0x8000CC00.toInt()  // green flash
        private const val DEBUG_FLASH_INTERACTIVE = 0x80CCCC00.toInt() // yellow flash
        private const val DEBUG_FLASH_DURATION_MS = 300L

        var instance: TapScrollService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    private lateinit var preferenceStore: PreferenceStore
    private lateinit var gestureProcessor: GestureProcessor
    private lateinit var windowManager: WindowManager
    private val nodeTreeHelper = NodeTreeHelper()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val zoneOverlays = mutableListOf<Pair<View, ScrollZone>>()
    private var currentPreferences: UserPreferences = UserPreferences()
    private var currentPackageName: String? = null
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var overlaysVisible: Boolean = false
    private val launchableAppCache = mutableMapOf<String, Boolean>()

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
                Log.d(TAG, "Preferences updated (debug=${preferences.debugMode})")
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
                val packageName = event.packageName?.toString() ?: return
                if (packageName == currentPackageName) return

                // Only track real user-facing apps; ignore system UI, keyboards,
                // status bar, etc. that fire TYPE_WINDOW_STATE_CHANGED during
                // app transitions and would cause overlays to flicker off.
                if (!isLaunchableApp(packageName)) {
                    Log.d(TAG, "Ignoring non-launchable window: $packageName")
                    return
                }

                currentPackageName = packageName
                Log.d(TAG, "Foreground app changed: $packageName")
                updateOverlayVisibility()
            }
        }
    }

    /**
     * Check if a package is a user-facing app (has a launcher activity).
     * Results are cached so PackageManager is only queried once per package.
     */
    private fun isLaunchableApp(packageName: String): Boolean {
        return launchableAppCache.getOrPut(packageName) {
            try {
                packageManager.getLaunchIntentForPackage(packageName) != null
            } catch (e: Exception) {
                false
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

    private fun recreateZoneOverlays() {
        removeZoneOverlays()
        if (shouldShowOverlays()) {
            createZoneOverlays()
        }
    }

    private fun createZoneOverlays() {
        val zones = gestureProcessor.getZones()
        val debug = currentPreferences.debugMode

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
                if (debug) {
                    val bgColor = if (zone.scrollDirection == ScrollDirection.UP)
                        DEBUG_ZONE_UP else DEBUG_ZONE_DOWN
                    setBackgroundColor(bgColor)
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
        Log.d(TAG, "Created ${zoneOverlays.size} zone overlays (debug=$debug)")
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
        return currentPackageName != null &&
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
                    debugFlash(zoneView, zone, isInteractive = true)
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

        if (success) {
            debugFlash(zoneView, zone, isInteractive = false)
            if (currentPreferences.hapticFeedback) {
                triggerHapticFeedback()
            }
        }

        return true
    }

    /**
     * Flash the zone overlay briefly to indicate a tap was received.
     * Green = scroll triggered, Yellow = interactive element (tap replayed).
     */
    private fun debugFlash(zoneView: View, zone: ScrollZone, isInteractive: Boolean) {
        if (!currentPreferences.debugMode) return

        val flashColor = if (isInteractive) DEBUG_FLASH_INTERACTIVE else DEBUG_FLASH_SCROLL
        zoneView.setBackgroundColor(flashColor)

        // Restore the normal debug background after the flash
        val normalColor = if (zone.scrollDirection == ScrollDirection.UP)
            DEBUG_ZONE_UP else DEBUG_ZONE_DOWN
        mainHandler.postDelayed({
            zoneView.setBackgroundColor(normalColor)
        }, DEBUG_FLASH_DURATION_MS)
    }

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
