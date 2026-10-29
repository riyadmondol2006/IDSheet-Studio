package io.github.riyadmondol2006.idsheetstudio.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.riyadmondol2006.idsheetstudio.MainActivity
import io.github.riyadmondol2006.idsheetstudio.R

/** Posts the "new version available" notification; tapping it opens the update popup. */
object UpdateNotifier {
    const val CHANNEL_ID = "updates"
    const val NOTIFICATION_ID = 1001
    const val EXTRA_SHOW_UPDATE = "io.github.riyadmondol2006.idsheetstudio.SHOW_UPDATE"

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when a new version of IDSheet Studio is released"
                },
            )
        }
    }

    /** Returns true when the notification was posted. */
    fun notify(context: Context, info: UpdateInfo): Boolean {
        if (!canNotify(context)) return false
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SHOW_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val firstLine = info.notes.lineSequence().map { it.trim().trimStart('#', '-', '*', ' ') }.firstOrNull { it.isNotBlank() }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("IDSheet Studio ${info.version} is available")
            .setContentText(firstLine ?: "Tap to see what's new and update.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.notes.take(400).ifBlank { "Tap to see what's new and update." }))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
}
