package com.ramazan.imsakiyesi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.composables.icons.lucide.R as LucideR
import com.ramazan.imsakiyesi.MainActivity
import com.ramazan.imsakiyesi.R
import com.ramazan.imsakiyesi.data.AppPreferences
import com.ramazan.imsakiyesi.data.AssetRepository
import com.ramazan.imsakiyesi.data.PrayerTimesEntry
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class RamadanWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> updateAll(context)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, RamadanWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            ids.forEach { id ->
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_ramadan)
            val appData = AssetRepository.load(context)
            val selectedCity = AppPreferences.load(context).cityName

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            if (selectedCity == null) {
                views.setTextViewText(R.id.txt_next_name, "Şehir seçiniz")
                views.setTextViewText(R.id.txt_next_time, "Vakit bilgisi için uygulamayı açın")
                views.setTextViewText(R.id.txt_countdown_label, "KALAN SÜRE")
                views.setChronometer(R.id.txt_countdown_value, SystemClock.elapsedRealtime(), null, false)
                views.setTextViewText(R.id.txt_countdown_value, "--:--:--")
                views.setImageViewResource(R.id.img_next_icon, LucideR.drawable.lucide_ic_map_pin)
                manager.updateAppWidget(appWidgetId, views)
                return
            }

            val entries = appData.prayerTimesByCity[selectedCity].orEmpty()
            val today = LocalDate.now()
            val todayEntry = entries.firstOrNull { it.date == today } ?: entries.minByOrNull { it.date }
            val tomorrowEntry = entries.firstOrNull { it.date == today.plusDays(1) } ?: todayEntry

            if (todayEntry == null || tomorrowEntry == null) {
                views.setTextViewText(R.id.txt_next_name, "Vakit yok")
                views.setTextViewText(R.id.txt_next_time, selectedCity)
                views.setTextViewText(R.id.txt_countdown_label, "KALAN SÜRE")
                views.setChronometer(R.id.txt_countdown_value, SystemClock.elapsedRealtime(), null, false)
                views.setTextViewText(R.id.txt_countdown_value, "--:--:--")
                views.setImageViewResource(R.id.img_next_icon, LucideR.drawable.lucide_ic_clock_3)
                manager.updateAppWidget(appWidgetId, views)
                return
            }

            val now = LocalTime.now()
            val nextPrayer = nextPrayer(todayEntry, tomorrowEntry, now)
            val countdown = iftarOrSahurCountdown(todayEntry, tomorrowEntry, now, today)

            views.setImageViewResource(R.id.img_next_icon, nextPrayer.iconRes)
            views.setTextViewText(R.id.txt_next_name, nextPrayer.name)
            views.setTextViewText(R.id.txt_next_time, nextPrayer.time)
            views.setTextViewText(R.id.txt_countdown_label, countdown.label)
            val base = SystemClock.elapsedRealtime() + countdown.remainingMillis
            views.setChronometerCountDown(R.id.txt_countdown_value, true)
            views.setChronometer(R.id.txt_countdown_value, base, null, true)

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun nextPrayer(
            todayEntry: PrayerTimesEntry,
            tomorrowEntry: PrayerTimesEntry,
            now: LocalTime
        ): WidgetPrayer {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val todayPrayers = listOf(
                WidgetPrayer("İMSAK", todayEntry.fajr, LucideR.drawable.lucide_ic_sunrise),
                WidgetPrayer("GÜNEŞ", todayEntry.sunrise, LucideR.drawable.lucide_ic_sun),
                WidgetPrayer("ÖĞLE", todayEntry.dhuhr, LucideR.drawable.lucide_ic_sun_medium),
                WidgetPrayer("İKİNDİ", todayEntry.asr, LucideR.drawable.lucide_ic_cloud_sun),
                WidgetPrayer("AKŞAM", todayEntry.maghrib, LucideR.drawable.lucide_ic_sunset),
                WidgetPrayer("YATSI", todayEntry.isha, LucideR.drawable.lucide_ic_moon)
            )

            return todayPrayers.firstOrNull { prayer ->
                runCatching { LocalTime.parse(prayer.time, formatter) }.getOrNull()?.isAfter(now) == true
            } ?: WidgetPrayer("İMSAK", tomorrowEntry.fajr, LucideR.drawable.lucide_ic_sunrise)
        }

        private fun iftarOrSahurCountdown(
            todayEntry: PrayerTimesEntry,
            tomorrowEntry: PrayerTimesEntry,
            now: LocalTime,
            today: LocalDate
        ): WidgetCountdown {
            val nowDateTime = LocalDateTime.of(today, now)
            val fajrToday = LocalTime.parse(todayEntry.fajr)
            val maghribToday = LocalTime.parse(todayEntry.maghrib)

            val (label, targetDateTime) = when {
                now.isBefore(fajrToday) -> {
                    "SAHURA KALAN" to LocalDateTime.of(today, fajrToday)
                }

                now.isBefore(maghribToday) -> {
                    "İFTARA KALAN" to LocalDateTime.of(today, maghribToday)
                }

                else -> {
                    "SAHURA KALAN" to LocalDateTime.of(today.plusDays(1), LocalTime.parse(tomorrowEntry.fajr))
                }
            }

            val remaining = Duration.between(nowDateTime, targetDateTime)
            val millis = if (remaining.isNegative) 0L else remaining.toMillis()
            return WidgetCountdown(label, millis)
        }
    }
}

private data class WidgetPrayer(
    val name: String,
    val time: String,
    val iconRes: Int
)

private data class WidgetCountdown(
    val label: String,
    val remainingMillis: Long
)
