package com.alex.alarmanfc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifier {
    private const val CHANNEL = "alarma"
    private const val ID = 42

    fun createChannel(c: Context) {
        val ch = NotificationChannel(CHANNEL, "Alarmas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aviso cuando suena la alarma"
            setSound(null, null) // el sonido lo pone Spotify
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun showAlarm(c: Context, alarmTime: Long) {
        createChannel(c)
        val open = PendingIntent.getActivity(
            c, 2, AlarmActivity.intent(c, alarmTime),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getBroadcast(
            c, 3, Intent(c, StopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val song = Store.effectiveSong(c, Scheduler.dateOf(alarmTime))
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("⏰ Alarma de las " + Scheduler.hhmm(alarmTime))
            .setContentText(song?.title ?: "Sonido de alarma")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "Detener", stop)
            .build()
        try {
            c.getSystemService(NotificationManager::class.java).notify(ID, n)
        } catch (e: SecurityException) {
            // sin permiso de notificaciones
        }
    }

    fun cancel(c: Context) = c.getSystemService(NotificationManager::class.java).cancel(ID)
}
