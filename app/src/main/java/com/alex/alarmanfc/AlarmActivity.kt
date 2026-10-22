package com.alex.alarmanfc

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Pantalla que aparece cuando suena la alarma (también con el móvil bloqueado). */
class AlarmActivity : AppCompatActivity() {

    companion object {
        fun intent(c: Context, alarmTime: Long): Intent =
            Intent(c, AlarmActivity::class.java)
                .putExtra(Scheduler.EXTRA_TIME, alarmTime)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handle(intent)
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        handle(newIntent)
    }

    private fun handle(i: Intent) {
        val t = i.getLongExtra(Scheduler.EXTRA_TIME, System.currentTimeMillis())
        render(t)
        AlarmPlayer.start(this, t)
    }

    private fun render(t: Long) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(Color.BLACK)
        }
        val clock = TextView(this).apply {
            text = Scheduler.hhmm(t)
            textSize = 80f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(clock)
        val song = Store.effectiveSong(this, Scheduler.dateOf(t))
        val title = TextView(this).apply {
            text = "🎵 " + (song?.title ?: "Alarma")
            textSize = 20f
            setTextColor(Color.parseColor("#1DB954"))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(32))
        }
        root.addView(title)
        setContentView(root)

        button(root, "Detener") {
            AlarmPlayer.stop(this)
            finish()
        }
        button(root, "Posponer 5 minutos", outlined = true) {
            AlarmPlayer.stop(this)
            Scheduler.snooze(this, 5)
            toast("Volverá a sonar en 5 minutos")
            finish()
        }
        button(root, "Abrir Spotify", outlined = true) { Spotify.openApp(this) }
    }
}
