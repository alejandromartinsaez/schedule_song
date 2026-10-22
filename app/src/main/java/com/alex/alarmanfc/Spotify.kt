package com.alex.alarmanfc

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Integración con la app de Spotify mediante enlaces (no hace falta cuenta de desarrollador). */
object Spotify {
    const val PKG = "com.spotify.music"

    private val reWeb = Regex("""open\.spotify\.com/(?:intl-[A-Za-z-]+/)?(track|album|playlist|artist|episode|show)/([A-Za-z0-9]+)""")
    private val reUri = Regex("""spotify:(track|album|playlist|artist|episode|show):([A-Za-z0-9]+)""")
    private val reAny = Regex("""(https?://(?:open\.spotify\.com|spotify\.link|spoti\.fi)/\S+|spotify:[a-z]+:[A-Za-z0-9]+)""")

    fun isInstalled(c: Context): Boolean = try {
        c.packageManager.getPackageInfo(PKG, 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    /** Saca el enlace de Spotify de un texto (p. ej. lo que comparte Spotify). */
    fun extractLink(text: String?): String? = text?.let { reAny.find(it)?.value }

    /** https://open.spotify.com/track/ID?si=… -> spotify:track:ID */
    fun toUri(link: String): String =
        reWeb.find(link)?.let { "spotify:${it.groupValues[1]}:${it.groupValues[2]}" }
            ?: reUri.find(link)?.value
            ?: link

    private fun webUrl(uri: String): String? =
        reUri.find(uri)?.let { "https://open.spotify.com/${it.groupValues[1]}/${it.groupValues[2]}" }
            ?: if (uri.startsWith("http")) uri else null

    /** Resuelve enlaces cortos y obtiene el título. Llamar fuera del hilo principal. */
    fun resolve(link: String): Song {
        var url = link
        if (!url.startsWith("spotify:") && !reWeb.containsMatchIn(url)) {
            url = try { expand(url) } catch (e: Exception) { null } ?: url
        }
        val uri = toUri(url)
        val title = try { fetchTitle(uri) } catch (e: Exception) { null }
        return Song(uri, title ?: "Canción de Spotify")
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile")
        }

    private fun expand(short: String): String? {
        var current = short
        repeat(6) {
            val conn = open(current)
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return null
                    if (loc.startsWith("spotify:")) return loc
                    current = URL(URL(current), loc).toString()
                    if (reWeb.containsMatchIn(current)) return current
                } else {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    return reWeb.find(body)?.value?.let { "https://$it" }
                }
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun fetchTitle(uri: String): String? {
        val web = webUrl(uri) ?: return null
        val conn = open("https://open.spotify.com/oembed?url=" + URLEncoder.encode(web, "UTF-8"))
        try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body).optString("title", "").takeIf { it.isNotBlank() }
        } finally {
            conn.disconnect()
        }
    }

    /** Abre la canción en Spotify (que empieza a reproducirla). */
    fun play(c: Context, song: Song): Boolean {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(song.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (isInstalled(c)) i.setPackage(PKG)
        return try {
            c.startActivity(i); true
        } catch (e: Exception) {
            false
        }
    }

    fun sendKey(c: Context, keyCode: Int) {
        val am = c.getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    fun openApp(c: Context): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(PKG) ?: return false
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun openStore(c: Context) {
        try {
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PKG")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PKG")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
