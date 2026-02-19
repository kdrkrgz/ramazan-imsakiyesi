package com.ramazan.imsakiyesi.data

import android.content.Context

data class SavedSettings(
    val cityName: String?,
    val prayerNotifications: Boolean,
    val imsakNotifications: Boolean,
    val iftarNotifications: Boolean
)

object AppPreferences {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_CITY = "selected_city"
    private const val KEY_PRAYER = "notif_prayer"
    private const val KEY_IMSAK = "notif_imsak"
    private const val KEY_IFTAR = "notif_iftar"

    fun load(context: Context): SavedSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return SavedSettings(
            cityName = prefs.getString(KEY_CITY, null),
            prayerNotifications = prefs.getBoolean(KEY_PRAYER, true),
            imsakNotifications = prefs.getBoolean(KEY_IMSAK, true),
            iftarNotifications = prefs.getBoolean(KEY_IFTAR, true)
        )
    }

    fun save(
        context: Context,
        cityName: String?,
        prayerNotifications: Boolean,
        imsakNotifications: Boolean,
        iftarNotifications: Boolean
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CITY, cityName)
            .putBoolean(KEY_PRAYER, prayerNotifications)
            .putBoolean(KEY_IMSAK, imsakNotifications)
            .putBoolean(KEY_IFTAR, iftarNotifications)
            .apply()
    }
}
