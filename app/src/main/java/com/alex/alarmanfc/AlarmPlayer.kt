package com.alex.alarmanfc

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/** Sube el volumen al máximo, lanza la canción en Spotify y, si no suena nada, usa un sonido de respaldo. */
object AlarmPlayer {
    private var startedFor = -1L
    private var fallback: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private fun audio(c: Context): AudioManager = c.getSystemService(AudioManager::class.java)

    fun raiseVolume(c: Context) {
        val am = audio(c)
        for (stream in intArrayOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_ALARM)) {
            try {
                if (Store.prevVolume(c, stream) < 0) Store.setPrevVolume(c, stream, am.getStreamVolume(stream))
                am.setStreamVolume(stream, am.getStreamMaxVolume(stream), 0)
            } catch (e: Exception) {
                // p. ej. modo No molestar sin acceso: se ignora
            }
        }
    }

    private fun restoreVolume(c: Context) {
        val am = audio(c)
        for (stream in intArrayOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_ALARM)) {
            val prev = Store.prevVolume(c, stream)
            if (prev >= 0) {
                try { am.setStreamVolume(stream, prev, 0) } catch (e: Exception) { }
                Store.setPrevVolume(c, stream, -1)
            }
        }
    }

    fun start(activity: Activity, alarmTime: Long) {
        if (startedFor == alarmTime) return
        startedFor = alarmTime
        val app = activity.applicationContext

        raiseVolume(app)
        Notifier.showAlarm(app, alarmTime)

        val song = Store.effectiveSong(app, Scheduler.dateOf(alarmTime))
        val launched = song != null && Spotify.play(activity, song)

        if (launched) {
            // Si Spotify abrió la canción pero no arrancó, se le manda "play"
            handler.postDelayed({
                raiseVolume(app)
                if (!audio(app).isMusicActive) Spotify.sendKey(app, KeyEvent.KEYCODE_MEDIA_PLAY)
            }, 5000)
            // Si tras 12 s sigue sin sonar nada, sonido de alarma de respaldo
            handler.postDelayed({ if (!audio(app).isMusicActive) startFallback(app) }, 12000)
        } else {
            startFallback(app)
        }
    }

    private fun startFallback(c: Context) {
        if (fallback != null) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        try {
            fallback = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(c, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            fallback = null
        }
    }

    fun stop(c: Context) {
        handler.removeCallbacksAndMessages(null)
        fallback?.let {
            try { it.stop() } catch (e: Exception) { }
            it.release()
        }
        fallback = null
        if (audio(c).isMusicActive) Spotify.sendKey(c, KeyEvent.KEYCODE_MEDIA_PAUSE)
        restoreVolume(c)
        Notifier.cancel(c)
    }
}
