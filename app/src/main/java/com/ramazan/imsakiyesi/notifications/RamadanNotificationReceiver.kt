package com.ramazan.imsakiyesi.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ramazan.imsakiyesi.R

class RamadanNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)

        val title = intent.getStringExtra("title").orEmpty()
        val subtitle = intent.getStringExtra("subtitle").orEmpty()
        val category = intent.getStringExtra("category").orEmpty()
        val city = intent.getStringExtra("city").orEmpty()
        val iconType = intent.getStringExtra("icon_type").orEmpty()
        val notificationId = intent.getIntExtra("notification_id", title.hashCode())

        val customView = RemoteViews(context.packageName, R.layout.notification_ramadan).apply {
            val nightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val titleColor = if (nightMode) Color.WHITE else Color.parseColor("#1E293B")
            val subtitleColor = if (nightMode) Color.parseColor("#E5E7EB") else Color.parseColor("#64748B")
            val metaColor = if (nightMode) Color.parseColor("#CBD5E1") else Color.parseColor("#475569")
            val timeColor = if (nightMode) Color.parseColor("#94A3B8") else Color.parseColor("#94A3B8")
            val iconColor = if (nightMode) Color.WHITE else Color.parseColor("#6366F1")

            setTextViewText(R.id.txt_category, category.ifBlank { "Ramazan İmsakiyesi" })
            setTextViewText(R.id.txt_time, "• şimdi")
            setTextViewText(R.id.txt_title, title)
            setTextViewText(R.id.txt_subtitle, if (city.isNotBlank()) "$city • $subtitle" else subtitle)
            setTextViewText(R.id.txt_right_icon, iconGlyph(iconType))
            setImageViewResource(R.id.img_app_icon, R.mipmap.ramadan_icon_round)

            setTextColor(R.id.txt_category, metaColor)
            setTextColor(R.id.txt_time, timeColor)
            setTextColor(R.id.txt_title, titleColor)
            setTextColor(R.id.txt_subtitle, subtitleColor)
            setTextColor(R.id.txt_right_icon, iconColor)
        }

        runCatching {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setCustomContentView(customView)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setContentTitle(title)
                .setContentText(subtitle)
                .setColor(0xFF6366F1.toInt())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ramazan Bildirimleri",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Namaz vakti, imsak ve iftar bildirimleri"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "ramadan_notifications"
    }

    private fun iconGlyph(iconType: String): String {
        return when (iconType) {
            "imsak" -> "\uD83C\uDF19"
            "sun" -> "\u2600\uFE0F"
            "cloud" -> "\u2601\uFE0F"
            "sunset" -> "\uD83C\uDF07"
            "night" -> "\uD83C\uDF19"
            else -> "\uD83D\uDD14"
        }
    }
}
