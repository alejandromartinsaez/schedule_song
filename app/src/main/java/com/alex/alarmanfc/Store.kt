package com.alex.alarmanfc

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

val ES: Locale = Locale("es", "ES")

fun String.cap(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase(ES) else it.toString() }

data class Song(val uri: String, val title: String) {
    fun toJson(): JSONObject = JSONObject().put("uri", uri).put("title", title)

    companion object {
        fun fromJson(o: JSONObject?): Song? {
            if (o == null) return null
            val uri = o.optString("uri", "")
            if (uri.isBlank()) return null
            return Song(uri, o.optString("title", uri))
        }

        fun fromString(s: String?): Song? {
            if (s.isNullOrBlank()) return null
            return try { fromJson(JSONObject(s)) } catch (e: Exception) { null }
        }
    }
}

/** Todo lo que guarda la app (horario, canciones, etiqueta NFC, volumen previo). */
object Store {
    private fun p(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("alarma_nfc", Context.MODE_PRIVATE)

    // ---------- Horario semanal ----------
    fun getTimes(c: Context, day: DayOfWeek): List<LocalTime> =
        (p(c).getString("sched_${day.name}", "") ?: "")
            .split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }
            .distinct()
            .sorted()

    fun setTimes(c: Context, day: DayOfWeek, times: List<LocalTime>) {
        val s = times.distinct().sorted().joinToString(",") { "%02d:%02d".format(it.hour, it.minute) }
        p(c).edit().putString("sched_${day.name}", s).apply()
    }

    fun addTime(c: Context, day: DayOfWeek, t: LocalTime) = setTimes(c, day, getTimes(c, day) + t)

    fun removeTime(c: Context, day: DayOfWeek, t: LocalTime) = setTimes(c, day, getTimes(c, day) - t)

    // ---------- Canciones ----------
    fun songFor(c: Context, date: LocalDate): Song? = Song.fromString(p(c).getString("song_$date", null))

    fun lastSong(c: Context): Song? = Song.fromString(p(c).getString("last_song", null))

    /** Canción del día; si ese día no se eligió, la última elegida. */
    fun effectiveSong(c: Context, date: LocalDate): Song? = songFor(c, date) ?: lastSong(c)

    fun setSongFor(c: Context, date: LocalDate, song: Song) {
        val e = p(c).edit()
        // borrar canciones de días pasados
        for (k in p(c).all.keys) {
            if (k.startsWith("song_")) {
                val d = runCatching { LocalDate.parse(k.removePrefix("song_")) }.getOrNull()
                if (d != null && d.isBefore(LocalDate.now())) e.remove(k)
            }
        }
        val json = song.toJson().toString()
        e.putString("song_$date", json)
        e.putString("last_song", json)
        val list = listOf(song) + recents(c).filter { it.uri != song.uri }
        val arr = JSONArray()
        list.take(6).forEach { arr.put(it.toJson()) }
        e.putString("recents", arr.toString())
        e.apply()
    }

    fun recents(c: Context): List<Song> {
        val s = p(c).getString("recents", null) ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).mapNotNull { Song.fromJson(arr.optJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    // ---------- Etiqueta NFC ----------
    fun tagId(c: Context): String? = p(c).getString("tag_id", null)

    fun setTagId(c: Context, id: String?) = p(c).edit().putString("tag_id", id).apply()

    // ---------- Volumen previo (para restaurarlo al parar) ----------
    fun prevVolume(c: Context, stream: Int): Int = p(c).getInt("prev_vol_$stream", -1)

    fun setPrevVolume(c: Context, stream: Int, v: Int) = p(c).edit().putInt("prev_vol_$stream", v).apply()
}
