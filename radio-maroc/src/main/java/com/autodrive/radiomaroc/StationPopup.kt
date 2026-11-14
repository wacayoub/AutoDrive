package com.autodrive.radiomaroc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

internal class StationPopup(private val context: Context) {
    private val notifications = context.getSystemService(NotificationManager::class.java)

    fun show(stationName: CharSequence) {
        if (!notifications.areNotificationsEnabled()) return
        ensureChannel(context)
        if (notifications.getNotificationChannel(CHANNEL_ID).importance == NotificationManager.IMPORTANCE_NONE) return
        val banner = RemoteViews(context.packageName, R.layout.station_popup).apply {
            setTextViewText(R.id.popup_station_name, stationName)
        }
        val openRadio = PendingIntent.getActivity(
            context, 0, Intent(context, RadioActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tab_radio)
            .setContentTitle(stationName)
            .setCustomContentView(banner)
            .setCustomBigContentView(banner)
            .setCustomHeadsUpContentView(banner)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setContentIntent(openRadio)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setAutoCancel(true)
            .setTimeoutAfter(DURATION_MS)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
    }

    fun dismiss() = notifications.cancel(NOTIFICATION_ID)

    companion object {
        const val CHANNEL_ID = "station_popup"
        private const val NOTIFICATION_ID = 310
        private const val DURATION_MS = 3_500L

        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Changements de station", NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
