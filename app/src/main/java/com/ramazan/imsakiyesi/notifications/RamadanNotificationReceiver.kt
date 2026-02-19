package com.ramazan.imsakiyesi.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.composables.icons.lucide.R as LucideR
import com.ramazan.imsakiyesi.MainActivity
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
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
            setImageViewResource(R.id.img_app_icon, R.mipmap.ramadan_icon_round)
            setImageViewResource(R.id.img_right_icon, iconRes(iconType))

            setTextColor(R.id.txt_category, metaColor)
            setTextColor(R.id.txt_time, timeColor)
            setTextColor(R.id.txt_title, titleColor)
            setTextColor(R.id.txt_subtitle, subtitleColor)
            setInt(R.id.img_right_icon, "setColorFilter", iconColor)
        }

        runCatching {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setCustomContentView(customView)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setContentTitle(title)
                .setContentText(subtitle)
                .setContentIntent(contentIntent)
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

    private fun iconRes(iconType: String): Int {
        return when (iconType) {
            "imsak" -> LucideR.drawable.lucide_ic_sunrise
            "sun" -> LucideR.drawable.lucide_ic_sun
            "cloud" -> LucideR.drawable.lucide_ic_cloud_sun
            "sunset" -> LucideR.drawable.lucide_ic_sunset
            "night" -> LucideR.drawable.lucide_ic_moon
            else -> LucideR.drawable.lucide_ic_bell
        }
    }
}
