package com.alex.alarmanfc

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Pantalla principal: horario semanal, etiqueta NFC, permisos y prueba. */
class MainActivity : AppCompatActivity() {
    private var nfcDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33 && !hasNotifPermission()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        Scheduler.schedule(this)
        render()
    }

    override fun onPause() {
        super.onPause()
        stopNfcLink()
    }

    private fun hasNotifPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun changed() {
        Scheduler.schedule(this)
        render()
    }

    private fun render() {
        val root = screen()
        label(root, "Alarma NFC", 28f, bold = true)
        label(root, "Despertador con tu canción de Spotify. Acerca la etiqueta NFC para elegir la canción del día.", 14f)

        // ---------- Próxima alarma ----------
        val next = card(root, "Próxima alarma")
        val n = Scheduler.nextAlarm(this)
        if (n == null) {
            label(next, "No hay ninguna hora en el horario. Añade alguna abajo.")
        } else {
            label(next, n.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM · HH:mm", ES)).cap(), 20f, bold = true)
            val song = Store.effectiveSong(this, n.toLocalDate())
            label(next, "🎵 " + (song?.title ?: "Sin canción elegida (sonará la alarma del sistema)"))
        }
        button(next, "Elegir canción de hoy") { startActivity(Intent(this, SongPickerActivity::class.java)) }
        button(next, "Probar la alarma ahora", outlined = true) {
            startActivity(AlarmActivity.intent(this, System.currentTimeMillis()))
        }

        // ---------- Horario ----------
        val sched = card(root, "Horario semanal")
        label(sched, "Pulsa + para añadir una hora. La ✕ la borra.", 13f)
        for (day in DayOfWeek.values()) {
            label(sched, day.getDisplayName(TextStyle.FULL, ES).cap(), 15f, bold = true)
            val group = ChipGroup(this)
            for (t in Store.getTimes(this, day)) {
                val chip = Chip(this)
                chip.text = t.toString()
                chip.isCloseIconVisible = true
                chip.setOnCloseIconClickListener {
                    Store.removeTime(this, day, t)
                    changed()
                }
                group.addView(chip)
            }
            val add = Chip(this)
            add.text = "+"
            add.setOnClickListener { pickTime(day) }
            group.addView(add)
            sched.addView(group, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        button(sched, "Copiar las horas del lunes de martes a viernes", outlined = true) {
            val mon = Store.getTimes(this, DayOfWeek.MONDAY)
            for (d in listOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
                Store.setTimes(this, d, mon)
            }
            changed()
        }

        // ---------- NFC ----------
        val nfc = card(root, "Etiqueta NFC")
        val adapter = NfcAdapter.getDefaultAdapter(this)
        val id = Store.tagId(this)
        when {
            adapter == null -> label(nfc, "⚠️ Este móvil no tiene NFC.")
            !adapter.isEnabled -> {
                label(nfc, "⚠️ El NFC está desactivado.")
                button(nfc, "Activar NFC", outlined = true) { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            }
        }
        label(nfc, if (id == null) "Ninguna etiqueta vinculada." else "✅ Vinculada: $id")
        if (adapter != null) {
            button(nfc, if (id == null) "Vincular etiqueta" else "Cambiar etiqueta") { startNfcLink(adapter) }
        }
        if (id != null) {
            button(nfc, "Desvincular", outlined = true) {
                Store.setTagId(this, null)
                render()
            }
        }

        // ---------- Permisos ----------
        val perm = card(root, "Permisos y ajustes")
        val pkgUri = Uri.parse("package:$packageName")
        permRow(perm, "Spotify instalado", Spotify.isInstalled(this)) { Spotify.openStore(this) }
        if (Build.VERSION.SDK_INT >= 33) {
            permRow(perm, "Notificaciones", hasNotifPermission()) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        permRow(perm, "Mostrar sobre otras apps (para abrir Spotify a la hora)", Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri))
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            permRow(perm, "Alarma a pantalla completa", nm.canUseFullScreenIntent()) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri))
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            permRow(perm, "Alarmas exactas", Scheduler.canExact(this)) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri))
            }
        }
        val pm = getSystemService(PowerManager::class.java)
        permRow(perm, "Sin restricciones de batería", pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
        }
    }

    private fun permRow(parent: LinearLayout, text: String, ok: Boolean, fix: () -> Unit) {
        label(parent, (if (ok) "✅ " else "⚠️ ") + text)
        if (!ok) {
            button(parent, "Conceder", outlined = true) {
                try { fix() } catch (e: Exception) { toast("No se pudo abrir ese ajuste") }
            }
        }
    }

    private fun pickTime(day: DayOfWeek) {
        val last = Store.getTimes(this, day).lastOrNull() ?: LocalTime.of(7, 0)
        TimePickerDialog(this, { _, h, m ->
            Store.addTime(this, day, LocalTime.of(h, m))
            changed()
        }, last.hour, last.minute, true).show()
    }

    // ---------- Vincular etiqueta ----------
    private fun startNfcLink(adapter: NfcAdapter) {
        if (!adapter.isEnabled) {
            toast("Activa primero el NFC")
            return
        }
        nfcDialog = AlertDialog.Builder(this)
            .setTitle("Vincular etiqueta")
            .setMessage("Acerca la etiqueta NFC a la parte trasera del móvil…")
            .setNegativeButton("Cancelar") { _, _ -> stopNfcLink() }
            .setOnCancelListener { stopNfcLink() }
            .show()
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        adapter.enableReaderMode(this, NfcAdapter.ReaderCallback { tag -> onTagLinked(tag) }, flags, null)
    }

    private fun stopNfcLink() {
        try { NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this) } catch (e: Exception) { }
        nfcDialog?.dismiss()
        nfcDialog = null
    }

    private fun onTagLinked(tag: Tag) {
        val id = Nfc.hex(tag.id) ?: return
        val written = Nfc.writeLaunchRecord(tag, packageName)
        Store.setTagId(this, id)
        runOnUiThread {
            stopNfcLink()
            AlertDialog.Builder(this)
                .setTitle("Etiqueta vinculada ✅")
                .setMessage(
                    if (written) "Listo. Desde ahora, al acercar esta etiqueta (con el móvil desbloqueado) se abrirá la app para elegir la canción."
                    else "Guardada. No se pudo grabar la etiqueta (puede estar bloqueada), así que Android quizá te pregunte con qué app abrirla: elige Alarma NFC y «Siempre»."
                )
                .setPositiveButton("OK", null)
                .show()
            render()
        }
    }
}
