package com.tapscroll.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tapscroll.TapScrollApplication
import com.tapscroll.data.*
import com.tapscroll.service.TapScrollService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferenceStore = (application as TapScrollApplication).preferenceStore

    // UI state
    val preferences: StateFlow<UserPreferences> = preferenceStore.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    // Service running state
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    // Installed apps for app selector
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    init {
        // Periodically check if service is running
        viewModelScope.launch {
            while (true) {
                _isServiceRunning.value = TapScrollService.isRunning()
                kotlinx.coroutines.delay(1000)
            }
        }

        // Load installed apps
        loadInstalledApps()
    }

    /**
     * Update scroll distance percentage
     */
    fun setScrollDistance(percent: Float) {
        viewModelScope.launch {
            preferenceStore.setScrollDistancePercent(percent)
        }
    }

    /**
     * Update scroll speed
     */
    fun setScrollSpeed(speed: ScrollSpeed) {
        viewModelScope.launch {
            preferenceStore.setScrollSpeed(speed)
        }
    }

    /**
     * Update zone type
     */
    fun setZoneType(zoneType: ZoneType) {
        viewModelScope.launch {
            preferenceStore.setZoneType(zoneType)
        }
    }

    /**
     * Update zone configuration
     */
    fun setZoneConfig(config: ZoneConfig) {
        viewModelScope.launch {
            preferenceStore.setZoneConfig(config)
        }
    }

    /**
     * Toggle invert direction
     */
    fun setInvertDirection(invert: Boolean) {
        viewModelScope.launch {
            preferenceStore.setInvertDirection(invert)
        }
    }

    /**
     * Toggle haptic feedback
     */
    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            preferenceStore.setHapticFeedback(enabled)
        }
    }

    /**
     * Toggle visual indicator
     */
    fun setVisualIndicator(enabled: Boolean) {
        viewModelScope.launch {
            preferenceStore.setVisualIndicator(enabled)
        }
    }

    /**
     * Toggle avoid interactive elements
     */
    fun setAvoidInteractiveElements(enabled: Boolean) {
        viewModelScope.launch {
            preferenceStore.setAvoidInteractiveElements(enabled)
        }
    }

    /**
     * Add an app to active apps list
     */
    fun addApp(packageName: String, appName: String) {
        viewModelScope.launch {
            preferenceStore.addActiveApp(AppConfig(packageName, appName, true))
        }
    }

    /**
     * Remove an app from active apps list
     */
    fun removeApp(packageName: String) {
        viewModelScope.launch {
            preferenceStore.removeActiveApp(packageName)
        }
    }

    /**
     * Toggle an app's enabled state
     */
    fun toggleAppEnabled(packageName: String) {
        viewModelScope.launch {
            preferenceStore.toggleAppEnabled(packageName)
        }
    }

    /**
     * Load list of installed apps that have launcher activities (browsers, etc.)
     */
    private fun loadInstalledApps() {
        viewModelScope.launch {
            val pm = getApplication<Application>().packageManager
            val apps = mutableListOf<InstalledAppInfo>()

            try {
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                
                for (appInfo in packages) {
                    // Skip system apps without launcher activity
                    val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent == null) continue

                    val appName = pm.getApplicationLabel(appInfo).toString()
                    apps.add(InstalledAppInfo(
                        packageName = appInfo.packageName,
                        appName = appName
                    ))
                }

                // Sort alphabetically
                apps.sortBy { it.appName.lowercase() }
                
            } catch (e: Exception) {
                // Handle any package manager errors
            }

            _installedApps.value = apps
        }
    }
}

/**
 * Info about an installed app
 */
data class InstalledAppInfo(
    val packageName: String,
    val appName: String
)
