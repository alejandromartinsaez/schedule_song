package com.alex.alarmanfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

object Nfc {
    const val URI = "alarmanfc://abrir"

    val ACTIONS = setOf(
        NfcAdapter.ACTION_NDEF_DISCOVERED,
        NfcAdapter.ACTION_TECH_DISCOVERED,
        NfcAdapter.ACTION_TAG_DISCOVERED
    )

    fun hex(b: ByteArray?): String? = b?.takeIf { it.isNotEmpty() }?.joinToString(":") { "%02X".format(it) }

    fun idFromIntent(i: Intent): String? = hex(i.getByteArrayExtra(NfcAdapter.EXTRA_ID))

    /**
     * Graba en la etiqueta un enlace propio + el registro de la app, para que
     * al acercarla Android abra directamente esta app sin preguntar.
     */
    fun writeLaunchRecord(tag: Tag, pkg: String): Boolean {
        val msg = NdefMessage(arrayOf(NdefRecord.createUri(URI), NdefRecord.createApplicationRecord(pkg)))
        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                try {
                    if (!ndef.isWritable || ndef.maxSize < msg.byteArrayLength) false
                    else { ndef.writeNdefMessage(msg); true }
                } finally {
                    try { ndef.close() } catch (e: Exception) { }
                }
            } else {
                val f = NdefFormatable.get(tag) ?: return false
                f.connect()
                try { f.format(msg); true } finally {
                    try { f.close() } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
