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

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferenceStore = (application as TapScrollApplication).preferenceStore

    val preferences: StateFlow<UserPreferences> = preferenceStore.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _isServiceRunning.value = TapScrollService.isRunning()
                kotlinx.coroutines.delay(1000)
            }
        }
        loadInstalledApps()
    }

    fun setServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceStore.setServiceEnabled(enabled)
        }
    }

    fun setScrollDistance(percent: Float) {
        viewModelScope.launch {
            preferenceStore.setScrollDistancePercent(percent)
        }
    }

    fun setScrollSpeed(speed: ScrollSpeed) {
        viewModelScope.launch {
            preferenceStore.setScrollSpeed(speed)
        }
    }

    fun setZoneType(zoneType: ZoneType) {
        viewModelScope.launch {
            // Reset to sensible defaults for the new zone type
            val config = when (zoneType) {
                ZoneType.EDGES -> ZoneConfig(
                    zoneType = ZoneType.EDGES,
                    scrollUpZoneStart = 0.0f,
                    scrollUpZoneEnd = 0.15f,
                    scrollDownZoneStart = 0.85f,
                    scrollDownZoneEnd = 1.0f
                )
                ZoneType.SIDES -> ZoneConfig(
                    zoneType = ZoneType.SIDES,
                    scrollUpZoneStart = 0.0f,
                    scrollUpZoneEnd = 0.20f,
                    scrollDownZoneStart = 0.80f,
                    scrollDownZoneEnd = 1.0f
                )
            }
            preferenceStore.setZoneConfig(config)
        }
    }

    fun setZoneConfig(config: ZoneConfig) {
        viewModelScope.launch {
            preferenceStore.setZoneConfig(config)
        }
    }

    fun setInvertDirection(invert: Boolean) {
        viewModelScope.launch {
            preferenceStore.setInvertDirection(invert)
        }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            preferenceStore.setHapticFeedback(enabled)
        }
    }

    fun setVisualIndicator(enabled: Boolean) {
        viewModelScope.launch {
            preferenceStore.setVisualIndicator(enabled)
        }
    }

    fun setAvoidInteractiveElements(enabled: Boolean) {
        viewModelScope.launch {
            preferenceStore.setAvoidInteractiveElements(enabled)
        }
    }

    fun setOverlayFeedbackMode(mode: OverlayFeedbackMode) {
        viewModelScope.launch {
            preferenceStore.setOverlayFeedbackMode(mode)
        }
    }

    fun setOverlayOpacity(opacity: Float) {
        viewModelScope.launch {
            preferenceStore.setOverlayOpacity(opacity)
        }
    }

    fun addApp(packageName: String, appName: String) {
        viewModelScope.launch {
            preferenceStore.addActiveApp(AppConfig(packageName, appName, true))
        }
    }

    fun removeApp(packageName: String) {
        viewModelScope.launch {
            preferenceStore.removeActiveApp(packageName)
        }
    }

    fun toggleAppEnabled(packageName: String) {
        viewModelScope.launch {
            preferenceStore.toggleAppEnabled(packageName)
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val pm = getApplication<Application>().packageManager
            val apps = mutableListOf<InstalledAppInfo>()

            try {
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

                for (appInfo in packages) {
                    val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent == null) continue

                    val appName = pm.getApplicationLabel(appInfo).toString()
                    apps.add(InstalledAppInfo(
                        packageName = appInfo.packageName,
                        appName = appName
                    ))
                }

                apps.sortBy { it.appName.lowercase() }

            } catch (_: Exception) {
            }

            _installedApps.value = apps
        }
    }
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String
)
