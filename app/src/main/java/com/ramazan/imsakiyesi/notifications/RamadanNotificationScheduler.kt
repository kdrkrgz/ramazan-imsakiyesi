package com.ramazan.imsakiyesi.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ramazan.imsakiyesi.data.PrayerTimesEntry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class NotificationToggles(
    val prayer: Boolean,
    val imsak: Boolean,
    val iftar: Boolean
)

object RamadanNotificationScheduler {
    private const val PREFS_NAME = "notif_schedule_state"
    private const val KEY_IDS = "ids"

    fun rescheduleForCity(
        context: Context,
        city: String,
        entries: List<PrayerTimesEntry>,
        toggles: NotificationToggles
    ) {
        cancelAll(context)
        if (!toggles.prayer && !toggles.imsak && !toggles.iftar) return

        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()
        val scheduledIds = mutableListOf<Int>()

        entries.filter { it.date >= LocalDate.now() }.forEach { entry ->
            buildEventsForDay(entry, toggles).forEach { event ->
                val trigger = LocalDateTime.of(entry.date, event.time)
                if (trigger.isAfter(now)) {
                    val requestCode = eventRequestCode(entry.date, event.type)
                    val intent = Intent(context, RamadanNotificationReceiver::class.java).apply {
                        putExtra("title", event.title)
                        putExtra("subtitle", event.subtitle)
                        putExtra("category", "Ramazan İmsakiyesi")
                        putExtra("icon_type", event.iconType)
                        putExtra("notification_id", requestCode)
                        putExtra("city", city)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val triggerAtMillis = trigger.atZone(zoneId).toInstant().toEpochMilli()
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    scheduleAlarm(alarmManager, triggerAtMillis, pendingIntent)
                    scheduledIds += requestCode
                }
            }
        }
        saveScheduledIds(context, scheduledIds)
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        loadScheduledIds(context).forEach { requestCode ->
            val intent = Intent(context, RamadanNotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
        saveScheduledIds(context, emptyList())
    }

    fun triggerAllTestNotifications(context: Context, city: String) {
        val now = System.currentTimeMillis()
        val receiver = RamadanNotificationReceiver()
        val testEvents = listOf(
            NotificationEvent("sahur_vakti_test", "Sahur Vakti", "Bereket sahurdadır.", "imsak", LocalTime.now()),
            NotificationEvent("sabah_namazi_test", "Sabah Namazı Vakti", "Namaz uykudan hayırlıdır.", "imsak", LocalTime.now()),
            NotificationEvent("ogle_namazi_test", "Öğle Namazı Vakti", "Günün bereketi namazla başlar.", "sun", LocalTime.now()),
            NotificationEvent("ikindi_namazi_test", "İkindi Namazı Vakti", "Namaz müminin miracıdır.", "cloud", LocalTime.now()),
            NotificationEvent("aksam_namazi_test", "Akşam Namazı Vakti", "Akşamın huzuru namazla tamamlanır.", "sunset", LocalTime.now()),
            NotificationEvent("iftar_vakti_test", "İftar Vakti Geldi", "Allah kabul etsin. Hayırlı iftarlar dileriz.", "sunset", LocalTime.now()),
            NotificationEvent("yatsi_namazi_test", "Yatsı Namazı Vakti", "Günün son huzur vakti.", "night", LocalTime.now())
        )

        testEvents.forEachIndexed { index, event ->
            val requestCode = ("test_${event.type}_$now").hashCode() + index
            val intent = Intent(context, RamadanNotificationReceiver::class.java).apply {
                putExtra("title", event.title)
                putExtra("subtitle", event.subtitle)
                putExtra("category", "Ramazan İmsakiyesi")
                putExtra("icon_type", event.iconType)
                putExtra("notification_id", requestCode)
                putExtra("city", city)
            }
            receiver.onReceive(context, intent)
        }
    }

    private fun buildEventsForDay(entry: PrayerTimesEntry, toggles: NotificationToggles): List<NotificationEvent> {
        val events = mutableListOf<NotificationEvent>()

        if (toggles.prayer) {
            events += NotificationEvent(
                type = "sabah_namazi",
                title = "Sabah Namazı Vakti",
                subtitle = "Namaz uykudan hayırlıdır.",
                iconType = "imsak",
                time = LocalTime.parse(entry.fajr)
            )
            events += NotificationEvent(
                type = "ogle_namazi",
                title = "Öğle Namazı Vakti",
                subtitle = "Günün bereketi namazla başlar.",
                iconType = "sun",
                time = LocalTime.parse(entry.dhuhr)
            )
            events += NotificationEvent(
                type = "ikindi_namazi",
                title = "İkindi Namazı Vakti",
                subtitle = "Namaz müminin miracıdır.",
                iconType = "cloud",
                time = LocalTime.parse(entry.asr)
            )
            events += NotificationEvent(
                type = "aksam_namazi",
                title = "Akşam Namazı Vakti",
                subtitle = "Akşamın huzuru namazla tamamlanır.",
                iconType = "sunset",
                time = LocalTime.parse(entry.maghrib)
            )
            events += NotificationEvent(
                type = "yatsi_namazi",
                title = "Yatsı Namazı Vakti",
                subtitle = "Günün son huzur vakti.",
                iconType = "night",
                time = LocalTime.parse(entry.isha)
            )
        }

        if (toggles.imsak) {
            if (!events.any { it.type == "sabah_namazi" }) {
                events += NotificationEvent(
                    type = "sabah_namazi",
                    title = "Sabah Namazı Vakti",
                    subtitle = "Namaz uykudan hayırlıdır.",
                    iconType = "imsak",
                    time = LocalTime.parse(entry.fajr)
                )
            }
            events += NotificationEvent(
                type = "sahur_vakti",
                title = "Sahur Vakti",
                subtitle = "Bereket sahurdadır.",
                iconType = "imsak",
                time = LocalTime.parse(entry.fajr)
            )
        }

        if (toggles.iftar) {
            if (!events.any { it.type == "aksam_namazi" }) {
                events += NotificationEvent(
                    type = "aksam_namazi",
                    title = "Akşam Namazı Vakti",
                    subtitle = "Akşamın huzuru namazla tamamlanır.",
                    iconType = "sunset",
                    time = LocalTime.parse(entry.maghrib)
                )
            }
            events += NotificationEvent(
                type = "iftar_vakti",
                title = "İftar Vakti Geldi",
                subtitle = "Allah kabul etsin. Hayırlı iftarlar dileriz.",
                iconType = "sunset",
                time = LocalTime.parse(entry.maghrib)
            )
        }

        return events
    }

    private fun eventRequestCode(date: LocalDate, type: String): Int {
        return (date.toString() + "_" + type).hashCode()
    }

    private fun saveScheduledIds(context: Context, ids: List<Int>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IDS, ids.joinToString(","))
            .apply()
    }

    private fun loadScheduledIds(context: Context): List<Int> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_IDS, "")
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.toIntOrNull() }
    }

    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                runCatching {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }.getOrElse {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }

            else -> {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }
}

private data class NotificationEvent(
    val type: String,
    val title: String,
    val subtitle: String,
    val iconType: String,
    val time: LocalTime
)
