package com.tapscroll.service

import android.accessibilityservice.AccessibilityService
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
import com.tapscroll.data.ScrollDirection
import com.tapscroll.data.UserPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Main accessibility service that handles tap-to-scroll functionality
 */
class TapScrollService : AccessibilityService() {

    companion object {
        private const val TAG = "TapScrollService"
        
        // Service instance for checking if running
        var instance: TapScrollService? = null
            private set
        
        fun isRunning(): Boolean = instance != null
    }

    private lateinit var preferenceStore: PreferenceStore
    private lateinit var gestureProcessor: GestureProcessor
    private lateinit var windowManager: WindowManager
    
    private var overlayView: View? = null
    private var currentPreferences: UserPreferences = UserPreferences()
    private var currentPackageName: String? = null
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

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
        
        // Start collecting preference changes
        serviceScope.launch {
            preferenceStore.preferencesFlow.collectLatest { preferences ->
                Log.d(TAG, "Preferences updated")
                currentPreferences = preferences
                gestureProcessor.updateZones(screenWidth, screenHeight, preferences)
                updateOverlayVisibility()
            }
        }
        
        // Create the touch overlay
        createOverlay()
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
        removeOverlay()
        gestureProcessor.clearCache()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Track which app is in foreground
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
        
        // Recreate overlay to adjust to new dimensions
        removeOverlay()
        createOverlay()
    }

    /**
     * Update screen dimensions from display metrics
     */
    private fun updateScreenDimensions() {
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        Log.d(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}")
    }

    /**
     * Create the transparent touch overlay
     */
    private fun createOverlay() {
        if (overlayView != null) {
            Log.d(TAG, "Overlay already exists")
            return
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        overlayView = FrameLayout(this).apply {
            // Initially invisible
            visibility = View.GONE
            
            setOnTouchListener { _, event ->
                handleTouchEvent(event)
            }
        }

        try {
            windowManager.addView(overlayView, params)
            Log.d(TAG, "Overlay created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create overlay", e)
        }
    }

    /**
     * Remove the touch overlay
     */
    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
                Log.d(TAG, "Overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove overlay", e)
            }
        }
        overlayView = null
    }

    /**
     * Update overlay visibility based on current app and preferences
     */
    private fun updateOverlayVisibility() {
        val shouldShow = currentPackageName != null &&
            currentPreferences.activeApps.any { 
                it.packageName == currentPackageName && it.enabled 
            }

        overlayView?.visibility = if (shouldShow) View.VISIBLE else View.GONE
        Log.d(TAG, "Overlay visibility: $shouldShow (app: $currentPackageName)")
    }

    /**
     * Handle touch events on the overlay
     */
    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.rawX
                val y = event.rawY
                
                Log.d(TAG, "Touch at ($x, $y)")

                // Get root node for interactive element detection
                val rootNode = rootInActiveWindow

                // Process the tap
                val result = gestureProcessor.processTap(
                    x = x,
                    y = y,
                    preferences = currentPreferences,
                    rootNode = rootNode
                )

                rootNode?.recycle()

                when (result) {
                    is GestureProcessor.ProcessResult.ScrollTriggered -> {
                        // Provide feedback
                        if (currentPreferences.hapticFeedback) {
                            triggerHapticFeedback()
                        }
                        if (currentPreferences.visualIndicator) {
                            showScrollIndicator(result.direction)
                        }
                        // Consume the event - don't pass through
                        return true
                    }
                    is GestureProcessor.ProcessResult.InteractiveElement -> {
                        // Don't consume - let the tap go through to the element
                        return false
                    }
                    is GestureProcessor.ProcessResult.NotInZone -> {
                        // Don't consume - pass through
                        return false
                    }
                    is GestureProcessor.ProcessResult.ScrollFailed -> {
                        // Don't consume - let user retry
                        return false
                    }
                }
            }
        }
        
        // Pass through other touch events (move, up, etc.)
        return false
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

    /**
     * Show visual scroll indicator (placeholder - could be enhanced with animation)
     */
    private fun showScrollIndicator(direction: ScrollDirection) {
        // For now, just log it. Could add an animated arrow overlay here.
        Log.d(TAG, "Scroll indicator: $direction")
        
        // TODO: Implement visual indicator animation
        // Could add a temporary ImageView with an arrow that fades in/out
    }
}
