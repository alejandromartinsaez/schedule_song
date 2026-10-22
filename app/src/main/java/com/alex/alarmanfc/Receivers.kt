package com.alex.alarmanfc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.time.ZonedDateTime

/** Salta a la hora programada. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val t = intent.getLongExtra(Scheduler.EXTRA_TIME, 0L).takeIf { it > 0 } ?: now

        // programa la siguiente hora del horario
        val from: ZonedDateTime = Scheduler.zoned(maxOf(t, now)).plusSeconds(1)
        Scheduler.schedule(c, from)

        AlarmPlayer.raiseVolume(c)
        Notifier.showAlarm(c, t)

        // Con "Mostrar sobre otras apps" podemos abrir la pantalla de alarma directamente
        // (y desde ella Spotify) aunque el móvil esté desbloqueado.
        if (Settings.canDrawOverlays(c)) {
            try { c.startActivity(AlarmActivity.intent(c, t)) } catch (e: Exception) { }
        }
    }
}

/** Botón "Detener" de la notificación. */
class StopReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        AlarmPlayer.stop(c)
    }
}

/** Reprograma tras reiniciar, actualizar la app o cambiar la hora. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        try { Scheduler.schedule(c) } catch (e: Exception) { }
    }
}
