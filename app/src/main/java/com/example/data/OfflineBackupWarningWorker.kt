package com.example.data

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.MainActivity

/** Independent status check. It reads device-only metadata and never opens Room or writes an archive. */
internal class OfflineBackupWarningWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val context = applicationContext
        return try {
            val state = OfflineBackupScheduleStore(context).load()
            val lastSuccess = OfflineBackupHistory(context).load().lastFullSuccess?.timeMillis
            val notifications = NotificationManagerCompat.from(context)
            if (state?.warningDue(lastSuccess, System.currentTimeMillis()) == true) {
                warn(context, notifications, state.config.warningDays)
            } else {
                notifications.cancel(NOTIFICATION_ID)
            }
            Result.success()
        } catch (_: OfflineBackupValidationException) {
            Result.retry()
        }
    }

    @SuppressLint("MissingPermission")
    private fun warn(context: Context, notifications: NotificationManagerCompat, days: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!notifications.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Ausbleibende Vollsicherung", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notifications.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Geprüfte Vollsicherung fehlt")
            .setContentText("Seit mindestens $days Tagen wurde keine Vollsicherung erfolgreich geprüft.")
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build())
    }

    companion object {
        private const val CHANNEL = "offline_backup_overdue"
        private const val NOTIFICATION_ID = 1203
    }
}
