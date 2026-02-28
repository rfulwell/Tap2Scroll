package com.tapscroll.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tap_scroll_preferences")

class PreferenceStore(private val context: Context) {

    companion object {
        private val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        private val SCROLL_DISTANCE_PERCENT = floatPreferencesKey("scroll_distance_percent")
        private val SCROLL_SPEED = stringPreferencesKey("scroll_speed")
        private val ZONE_TYPE = stringPreferencesKey("zone_type")
        private val SCROLL_UP_ZONE_START = floatPreferencesKey("scroll_up_zone_start")
        private val SCROLL_UP_ZONE_END = floatPreferencesKey("scroll_up_zone_end")
        private val SCROLL_DOWN_ZONE_START = floatPreferencesKey("scroll_down_zone_start")
        private val SCROLL_DOWN_ZONE_END = floatPreferencesKey("scroll_down_zone_end")
        private val INVERT_DIRECTION = booleanPreferencesKey("invert_direction")
        private val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        private val VISUAL_INDICATOR = booleanPreferencesKey("visual_indicator")
        private val AVOID_INTERACTIVE = booleanPreferencesKey("avoid_interactive")
        private val ACTIVE_APPS = stringPreferencesKey("active_apps")
        private val SCROLL_UP_ENABLED = booleanPreferencesKey("scroll_up_enabled")
        private val SCROLL_DOWN_ENABLED = booleanPreferencesKey("scroll_down_enabled")
        private val OVERLAY_FEEDBACK_MODE = stringPreferencesKey("overlay_feedback_mode")
        private val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            mapPreferences(preferences)
        }

    private fun mapPreferences(preferences: Preferences): UserPreferences {
        val zoneConfig = ZoneConfig(
            zoneType = preferences[ZONE_TYPE]?.let {
                try { ZoneType.valueOf(it) } catch (_: Exception) { ZoneType.EDGES }
            } ?: ZoneType.EDGES,
            scrollUpZoneStart = preferences[SCROLL_UP_ZONE_START] ?: 0.0f,
            scrollUpZoneEnd = preferences[SCROLL_UP_ZONE_END] ?: 0.15f,
            scrollDownZoneStart = preferences[SCROLL_DOWN_ZONE_START] ?: 0.85f,
            scrollDownZoneEnd = preferences[SCROLL_DOWN_ZONE_END] ?: 1.0f
        )

        val activeApps = preferences[ACTIVE_APPS]?.let { json ->
            parseActiveApps(json)
        } ?: listOf(AppConfig("com.brave.browser", "Brave Browser", true))

        return UserPreferences(
            serviceEnabled = preferences[SERVICE_ENABLED] ?: true,
            scrollDistancePercent = preferences[SCROLL_DISTANCE_PERCENT] ?: 0.75f,
            scrollSpeed = preferences[SCROLL_SPEED]?.let {
                ScrollSpeed.valueOf(it)
            } ?: ScrollSpeed.MEDIUM,
            zoneConfig = zoneConfig,
            invertDirection = preferences[INVERT_DIRECTION] ?: false,
            hapticFeedback = preferences[HAPTIC_FEEDBACK] ?: true,
            visualIndicator = preferences[VISUAL_INDICATOR] ?: true,
            avoidInteractiveElements = preferences[AVOID_INTERACTIVE] ?: true,
            scrollUpEnabled = preferences[SCROLL_UP_ENABLED] ?: true,
            scrollDownEnabled = preferences[SCROLL_DOWN_ENABLED] ?: true,
            overlayFeedbackMode = preferences[OVERLAY_FEEDBACK_MODE]?.let {
                try { OverlayFeedbackMode.valueOf(it) } catch (_: Exception) { OverlayFeedbackMode.FLASH_ON_TAP }
            } ?: OverlayFeedbackMode.FLASH_ON_TAP,
            overlayOpacity = preferences[OVERLAY_OPACITY] ?: 0.3f,
            activeApps = activeApps
        )
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setScrollDistancePercent(percent: Float) {
        context.dataStore.edit { preferences ->
            preferences[SCROLL_DISTANCE_PERCENT] = percent.coerceIn(0.1f, 1.0f)
        }
    }

    suspend fun setScrollSpeed(speed: ScrollSpeed) {
        context.dataStore.edit { preferences ->
            preferences[SCROLL_SPEED] = speed.name
        }
    }

    suspend fun setZoneType(zoneType: ZoneType) {
        context.dataStore.edit { preferences ->
            preferences[ZONE_TYPE] = zoneType.name
        }
    }

    suspend fun setZoneConfig(config: ZoneConfig) {
        context.dataStore.edit { preferences ->
            preferences[ZONE_TYPE] = config.zoneType.name
            preferences[SCROLL_UP_ZONE_START] = config.scrollUpZoneStart
            preferences[SCROLL_UP_ZONE_END] = config.scrollUpZoneEnd
            preferences[SCROLL_DOWN_ZONE_START] = config.scrollDownZoneStart
            preferences[SCROLL_DOWN_ZONE_END] = config.scrollDownZoneEnd
        }
    }

    suspend fun setInvertDirection(invert: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[INVERT_DIRECTION] = invert
        }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAPTIC_FEEDBACK] = enabled
        }
    }

    suspend fun setVisualIndicator(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VISUAL_INDICATOR] = enabled
        }
    }

    suspend fun setAvoidInteractiveElements(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AVOID_INTERACTIVE] = enabled
        }
    }

    suspend fun setScrollUpEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCROLL_UP_ENABLED] = enabled
        }
    }

    suspend fun setScrollDownEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCROLL_DOWN_ENABLED] = enabled
        }
    }

    suspend fun setOverlayFeedbackMode(mode: OverlayFeedbackMode) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_FEEDBACK_MODE] = mode.name
        }
    }

    suspend fun setOverlayOpacity(opacity: Float) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_OPACITY] = opacity.coerceIn(0.05f, 1.0f)
        }
    }

    suspend fun setActiveApps(apps: List<AppConfig>) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_APPS] = serializeActiveApps(apps)
        }
    }

    suspend fun addActiveApp(app: AppConfig) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[ACTIVE_APPS]?.let { parseActiveApps(it) } ?: emptyList()
            if (currentApps.none { it.packageName == app.packageName }) {
                preferences[ACTIVE_APPS] = serializeActiveApps(currentApps + app)
            }
        }
    }

    suspend fun removeActiveApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[ACTIVE_APPS]?.let { parseActiveApps(it) } ?: emptyList()
            preferences[ACTIVE_APPS] = serializeActiveApps(currentApps.filter { it.packageName != packageName })
        }
    }

    suspend fun toggleAppEnabled(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[ACTIVE_APPS]?.let { parseActiveApps(it) } ?: emptyList()
            val updatedApps = currentApps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(enabled = !app.enabled)
                } else {
                    app
                }
            }
            preferences[ACTIVE_APPS] = serializeActiveApps(updatedApps)
        }
    }

    private fun serializeActiveApps(apps: List<AppConfig>): String {
        return apps.joinToString("|") { app ->
            "${app.packageName}:${app.appName.replace(":", "_")}:${app.enabled}"
        }
    }

    private fun parseActiveApps(serialized: String): List<AppConfig> {
        if (serialized.isBlank()) return emptyList()

        return serialized.split("|").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size >= 3) {
                AppConfig(
                    packageName = parts[0],
                    appName = parts[1].replace("_", ":"),
                    enabled = parts[2].toBoolean()
                )
            } else null
        }
    }
}
