package com.alex.alarmanfc

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Calcula la siguiente hora del horario y la programa con AlarmManager.setAlarmClock (exacta). */
object Scheduler {
    const val EXTRA_TIME = "alarm_time"
    private const val RC_NEXT = 100
    private const val RC_SNOOZE = 101

    fun nextAlarm(c: Context, from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        for (i in 0..7) {
            val date = from.toLocalDate().plusDays(i.toLong())
            for (t in Store.getTimes(c, date.dayOfWeek)) {
                val dt = ZonedDateTime.of(date, t, from.zone)
                if (dt.isAfter(from)) return dt
            }
        }
        return null
    }

    fun canExact(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun schedule(c: Context, from: ZonedDateTime = ZonedDateTime.now()) {
        val am = c.getSystemService(AlarmManager::class.java)
        val next = nextAlarm(c, from)
        if (next == null) {
            am.cancel(firePending(c, RC_NEXT, 0L))
            return
        }
        set(c, RC_NEXT, next.toInstant().toEpochMilli())
    }

    fun snooze(c: Context, minutes: Int) {
        set(c, RC_SNOOZE, System.currentTimeMillis() + minutes * 60_000L)
    }

    private fun set(c: Context, rc: Int, millis: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val fire = firePending(c, rc, millis)
        val show = PendingIntent.getActivity(
            c, 1, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), fire)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, fire)
        }
    }

    private fun firePending(c: Context, rc: Int, millis: Long): PendingIntent =
        PendingIntent.getBroadcast(
            c, rc,
            Intent(c, AlarmReceiver::class.java).putExtra(EXTRA_TIME, millis),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun zoned(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

    fun dateOf(millis: Long): LocalDate = zoned(millis).toLocalDate()

    fun hhmm(millis: Long): String = zoned(millis).format(DateTimeFormatter.ofPattern("HH:mm"))
}
