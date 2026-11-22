package com.falak.livewallpaper

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent user settings for Falak (selected manual location & prayer calculation source).
 * Shared directly between MainActivity and FalakWallpaperService with change notifications.
 */
object FalakPreferences {

    private const val PREFS_NAME = "falak_prefs"

    private const val KEY_METHOD_ID = "method_id"
    private const val KEY_LOC_ID = "loc_id"
    private const val KEY_LOC_NAME = "loc_name"
    private const val KEY_LOC_COUNTRY = "loc_country"
    private const val KEY_LOC_LAT = "loc_lat"
    private const val KEY_LOC_LON = "loc_lon"
    private const val KEY_LOC_ZONE = "loc_zone"
    private const val KEY_LOC_CUSTOM = "loc_custom"

    data class Settings(
        val location: PrayerLocation,
        val method: PrayerMethod
    )

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun load(context: Context): Settings {
        val sp = prefs(context)
        val method = PrayerMethod.fromId(sp.getString(KEY_METHOD_ID, null))

        val isCustom = sp.getBoolean(KEY_LOC_CUSTOM, false)
        val savedId = sp.getString(KEY_LOC_ID, null)
        val location = if (!isCustom && savedId != null) {
            WorldCities.findById(savedId) ?: WorldCities.DEFAULT
        } else if (isCustom) {
            val name = sp.getString(KEY_LOC_NAME, null)?.takeIf { it.isNotBlank() } ?: "Custom"
            val country = sp.getString(KEY_LOC_COUNTRY, "") ?: ""
            val lat = sp.getString(KEY_LOC_LAT, null)?.toDoubleOrNull()?.coerceIn(-85.0, 85.0)
                ?: WorldCities.DEFAULT.latitudeDeg
            val lon = sp.getString(KEY_LOC_LON, null)?.toDoubleOrNull()?.coerceIn(-180.0, 180.0)
                ?: WorldCities.DEFAULT.longitudeDeg
            val zone = sp.getString(KEY_LOC_ZONE, null)?.takeIf { it.isNotBlank() }
                ?: WorldCities.DEFAULT.zoneId
            PrayerLocation(
                id = "custom",
                name = name,
                country = country,
                latitudeDeg = lat,
                longitudeDeg = lon,
                zoneId = zone,
                isCustom = true
            )
        } else {
            WorldCities.DEFAULT
        }

        return Settings(location = location, method = method)
    }

    fun saveMethod(context: Context, method: PrayerMethod) {
        prefs(context).edit()
            .putString(KEY_METHOD_ID, method.id)
            .apply()
    }

    fun saveLocation(context: Context, location: PrayerLocation) {
        prefs(context).edit()
            .putString(KEY_LOC_ID, location.id)
            .putString(KEY_LOC_NAME, location.name)
            .putString(KEY_LOC_COUNTRY, location.country)
            .putString(KEY_LOC_LAT, location.latitudeDeg.toString())
            .putString(KEY_LOC_LON, location.longitudeDeg.toString())
            .putString(KEY_LOC_ZONE, location.zoneId)
            .putBoolean(KEY_LOC_CUSTOM, location.isCustom)
            .apply()
    }

    fun registerListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }
}
