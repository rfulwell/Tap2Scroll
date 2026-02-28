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
        // Keys for preferences
        private val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        private val SCROLL_DISTANCE_PERCENT = floatPreferencesKey("scroll_distance_percent")
        private val SCROLL_SPEED = stringPreferencesKey("scroll_speed")
        private val ZONE_TYPE = stringPreferencesKey("zone_type")
        private val TOP_ZONE_PERCENT = floatPreferencesKey("top_zone_percent")
        private val BOTTOM_ZONE_PERCENT = floatPreferencesKey("bottom_zone_percent")
        private val LEFT_ZONE_PERCENT = floatPreferencesKey("left_zone_percent")
        private val RIGHT_ZONE_PERCENT = floatPreferencesKey("right_zone_percent")
        private val CORNER_ZONE_PERCENT = floatPreferencesKey("corner_zone_percent")
        private val INVERT_DIRECTION = booleanPreferencesKey("invert_direction")
        private val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        private val VISUAL_INDICATOR = booleanPreferencesKey("visual_indicator")
        private val AVOID_INTERACTIVE = booleanPreferencesKey("avoid_interactive")
        private val ACTIVE_APPS = stringPreferencesKey("active_apps")
    }

    /**
     * Flow of user preferences that updates automatically when preferences change
     */
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

    /**
     * Map raw preferences to UserPreferences data class
     */
    private fun mapPreferences(preferences: Preferences): UserPreferences {
        val zoneConfig = ZoneConfig(
            zoneType = preferences[ZONE_TYPE]?.let { 
                ZoneType.valueOf(it) 
            } ?: ZoneType.EDGES,
            topZonePercent = preferences[TOP_ZONE_PERCENT] ?: 0.15f,
            bottomZonePercent = preferences[BOTTOM_ZONE_PERCENT] ?: 0.15f,
            leftZonePercent = preferences[LEFT_ZONE_PERCENT] ?: 0.20f,
            rightZonePercent = preferences[RIGHT_ZONE_PERCENT] ?: 0.20f,
            cornerZonePercent = preferences[CORNER_ZONE_PERCENT] ?: 0.15f
        )

        val activeApps = preferences[ACTIVE_APPS]?.let { json ->
            parseActiveApps(json)
        } ?: listOf(AppConfig("com.brave.browser", "Brave Browser", true))

        return UserPreferences(
            serviceEnabled = preferences[SERVICE_ENABLED] ?: false,
            scrollDistancePercent = preferences[SCROLL_DISTANCE_PERCENT] ?: 0.75f,
            scrollSpeed = preferences[SCROLL_SPEED]?.let { 
                ScrollSpeed.valueOf(it) 
            } ?: ScrollSpeed.MEDIUM,
            zoneConfig = zoneConfig,
            invertDirection = preferences[INVERT_DIRECTION] ?: false,
            hapticFeedback = preferences[HAPTIC_FEEDBACK] ?: true,
            visualIndicator = preferences[VISUAL_INDICATOR] ?: true,
            avoidInteractiveElements = preferences[AVOID_INTERACTIVE] ?: true,
            activeApps = activeApps
        )
    }

    /**
     * Update service enabled state
     */
    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SERVICE_ENABLED] = enabled
        }
    }

    /**
     * Update scroll distance percentage
     */
    suspend fun setScrollDistancePercent(percent: Float) {
        context.dataStore.edit { preferences ->
            preferences[SCROLL_DISTANCE_PERCENT] = percent.coerceIn(0.1f, 1.0f)
        }
    }

    /**
     * Update scroll speed
     */
    suspend fun setScrollSpeed(speed: ScrollSpeed) {
        context.dataStore.edit { preferences ->
            preferences[SCROLL_SPEED] = speed.name
        }
    }

    /**
     * Update zone type
     */
    suspend fun setZoneType(zoneType: ZoneType) {
        context.dataStore.edit { preferences ->
            preferences[ZONE_TYPE] = zoneType.name
        }
    }

    /**
     * Update zone configuration
     */
    suspend fun setZoneConfig(config: ZoneConfig) {
        context.dataStore.edit { preferences ->
            preferences[ZONE_TYPE] = config.zoneType.name
            preferences[TOP_ZONE_PERCENT] = config.topZonePercent
            preferences[BOTTOM_ZONE_PERCENT] = config.bottomZonePercent
            preferences[LEFT_ZONE_PERCENT] = config.leftZonePercent
            preferences[RIGHT_ZONE_PERCENT] = config.rightZonePercent
            preferences[CORNER_ZONE_PERCENT] = config.cornerZonePercent
        }
    }

    /**
     * Update invert direction setting
     */
    suspend fun setInvertDirection(invert: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[INVERT_DIRECTION] = invert
        }
    }

    /**
     * Update haptic feedback setting
     */
    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAPTIC_FEEDBACK] = enabled
        }
    }

    /**
     * Update visual indicator setting
     */
    suspend fun setVisualIndicator(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VISUAL_INDICATOR] = enabled
        }
    }

    /**
     * Update avoid interactive elements setting
     */
    suspend fun setAvoidInteractiveElements(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AVOID_INTERACTIVE] = enabled
        }
    }

    /**
     * Update active apps list
     */
    suspend fun setActiveApps(apps: List<AppConfig>) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_APPS] = serializeActiveApps(apps)
        }
    }

    /**
     * Add an app to the active apps list
     */
    suspend fun addActiveApp(app: AppConfig) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[ACTIVE_APPS]?.let { parseActiveApps(it) } ?: emptyList()
            if (currentApps.none { it.packageName == app.packageName }) {
                preferences[ACTIVE_APPS] = serializeActiveApps(currentApps + app)
            }
        }
    }

    /**
     * Remove an app from the active apps list
     */
    suspend fun removeActiveApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[ACTIVE_APPS]?.let { parseActiveApps(it) } ?: emptyList()
            preferences[ACTIVE_APPS] = serializeActiveApps(currentApps.filter { it.packageName != packageName })
        }
    }

    /**
     * Toggle an app's enabled state
     */
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

    /**
     * Simple serialization for active apps (package:name:enabled format)
     */
    private fun serializeActiveApps(apps: List<AppConfig>): String {
        return apps.joinToString("|") { app ->
            "${app.packageName}:${app.appName.replace(":", "_")}:${app.enabled}"
        }
    }

    /**
     * Parse serialized active apps string
     */
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
