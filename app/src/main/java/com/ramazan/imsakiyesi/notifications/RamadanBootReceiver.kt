package com.ramazan.imsakiyesi.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ramazan.imsakiyesi.data.AppPreferences
import com.ramazan.imsakiyesi.data.AssetRepository

class RamadanBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appData = runCatching { AssetRepository.load(context) }.getOrNull() ?: return
        val saved = AppPreferences.load(context)
        val cityName = saved.cityName ?: return
        val cityEntries = appData.prayerTimesByCity[cityName].orEmpty()

        RamadanNotificationScheduler.rescheduleForCity(
            context = context,
            city = cityName,
            entries = cityEntries,
            toggles = NotificationToggles(
                prayer = saved.prayerNotifications,
                imsak = saved.imsakNotifications,
                iftar = saved.iftarNotifications
            )
        )
    }
}
