package com.alex.alarmanfc

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Se abre al leer la etiqueta NFC: pregunta qué canción poner en las alarmas de hoy.
 * También recibe canciones compartidas desde Spotify (Compartir → Alarma NFC).
 */
class SongPickerActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var lastClip: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkTag(intent)) {
            finish()
            return
        }
        render()
        handleShare(intent)
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        if (checkTag(newIntent)) {
            render()
            handleShare(newIntent)
        }
    }

    /** Solo vale la etiqueta vinculada. Otras aperturas (app, compartir) pasan siempre. */
    private fun checkTag(i: Intent): Boolean {
        if (i.action !in Nfc.ACTIONS) return true
        val saved = Store.tagId(this)
        if (saved == null) {
            toast("Primero vincula tu etiqueta en la app")
            startActivity(Intent(this, MainActivity::class.java))
            return false
        }
        if (Nfc.idFromIntent(i) != saved) {
            toast("Etiqueta NFC no reconocida")
            return false
        }
        return true
    }

    private fun render() {
        val root = screen()
        val today = LocalDate.now()
        label(root, "🎵 ¿Qué canción quieres hoy?", 26f, bold = true)

        val info = card(root, today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)).cap())
        val times = Store.getTimes(this, today.dayOfWeek)
        val now = LocalTime.now()
        if (times.isEmpty()) {
            label(info, "Hoy no hay horas en el horario. La canción quedará como predeterminada para los próximos días.")
        } else {
            label(info, "Sonará a las: " + times.joinToString("  ·  ") { if (it.isBefore(now)) "$it (ya pasó)" else it.toString() })
        }
        val cur = Store.songFor(this, today)
        val fallback = Store.lastSong(this)
        status = label(
            info,
            when {
                cur != null -> "✅ Elegida: ${cur.title}"
                fallback != null -> "Aún no has elegido. Si no eliges, sonará: ${fallback.title}"
                else -> "Aún no has elegido canción."
            },
            15f, bold = true
        )

        val pick = card(root, "Canción de Spotify")
        label(pick, "En Spotify: ⋯ en la canción → Compartir → Alarma NFC.\nO «Copiar enlace» y vuelve aquí: se pega solo.", 13f)
        input = EditText(this).apply {
            hint = "https://open.spotify.com/track/…"
            isSingleLine = true
            inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT
        }
        pick.addView(input)
        button(pick, "Guardar para hoy") { saveLink(input.text?.toString()) }
        button(pick, "Abrir Spotify para buscarla", outlined = true) {
            if (Spotify.openApp(this)) toast("Busca la canción → ⋯ → Compartir → Alarma NFC")
            else Spotify.openStore(this)
        }
        button(pick, "Pegar del portapapeles", outlined = true) {
            val t = clipText()
            if (t.isNullOrBlank()) toast("El portapapeles está vacío") else input.setText(t)
        }

        val recents = Store.recents(this)
        if (recents.isNotEmpty()) {
            val rc = card(root, "Recientes")
            for (s in recents) button(rc, s.title, outlined = true) { saveSong(s) }
        }

        button(root, "Configurar horario y ajustes", outlined = true) {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun handleShare(i: Intent) {
        if (i.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        input.setText(text)
        saveLink(text)
    }

    private fun saveLink(text: String?) {
        val link = Spotify.extractLink(text)
        if (link == null) {
            toast("Eso no parece un enlace de Spotify")
            return
        }
        status.text = "Buscando la canción…"
        Thread {
            val song = try { Spotify.resolve(link) } catch (e: Exception) { Song(Spotify.toUri(link), "Canción de Spotify") }
            runOnUiThread { if (!isFinishing) saveSong(song) }
        }.start()
    }

    private fun saveSong(song: Song) {
        Store.setSongFor(this, LocalDate.now(), song)
        Scheduler.schedule(this)
        toast("✅ Hoy sonará: ${song.title}")
        render()
    }

    private fun clipText(): String? = try {
        val cm = getSystemService(ClipboardManager::class.java)
        val clip = cm.primaryClip
        if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString() else null
    } catch (e: Exception) { null }

    /** Al volver de Spotify con un enlace copiado, lo pega automáticamente. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !::input.isInitialized || !input.text.isNullOrBlank()) return
        val link = Spotify.extractLink(clipText()) ?: return
        if (link == lastClip) return
        lastClip = link
        input.setText(link)
        status.text = "📋 Enlace detectado en el portapapeles — pulsa «Guardar para hoy»"
    }
}
