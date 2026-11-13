package com.example.ytsaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * The picture of a saved file.
 *  - video: a frame of the video;
 *  - audio: the cover that is inside the file; when there is none, a photo of the ARTIST (looked up once on Deezer's
 *    public search and then kept on the phone), and only when nothing is found the branded tile.
 * Only the artist's NAME is sent to the lookup; it can be switched off in Settings -> Behavior ("Artist pictures").
 */
object TrackArt {
    private val mem = LruCache<String, Bitmap>(80)
    private val noArt = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun enabled(ctx: Context) = Prefs.bool(ctx, "artist_art", true)

    /** Blocking: call from a background thread. */
    fun load(ctx: Context, item: HistoryItem): Bitmap? {
        // Quran verses and recitations always get the one standard cover (Settings -> Behavior -> Player)
        if (item.format == "MP3" && Beh.on(ctx, Beh.QURAN_COVER) && QuranDetect.isQuran(prettyName(item.name))) return quranCover(ctx)
        mem.get(item.uri)?.let { return it }
        if (item.uri in noArt) return null
        val bmp = if (item.format == "MP3") audioArt(ctx, item) else VideoThumbs.load(ctx, item.uri)
        if (bmp != null) mem.put(item.uri, bmp) else if (item.format == "MP3") noArt.add(item.uri)
        return bmp
    }

    private fun audioArt(ctx: Context, item: HistoryItem): Bitmap? {
        var tagArtist: String? = null
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, Uri.parse(item.uri))
            val genre = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).orEmpty()
            if (Beh.on(ctx, Beh.QURAN_COVER) && QuranDetect.isQuran(genre)) return quranCover(ctx)
            r.embeddedPicture?.let { bytes -> decode(bytes)?.let { return it } }
            tagArtist = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            // an unreadable file simply has no cover
        } finally {
            runCatching { r.release() }
        }
        if (!enabled(ctx)) return null
        val artist = (tagArtist ?: artistFromName(item.name)) ?: return null
        return artistPicture(ctx, artist)
    }

    @Volatile private var quran: Bitmap? = null

    /** The standard cover of Quran recitations (decoded once). */
    fun quranCover(ctx: Context): Bitmap? {
        quran?.let { return it }
        val b = runCatching { BitmapFactory.decodeResource(ctx.resources, R.drawable.quran_cover) }.getOrNull()
        quran = b
        return b
    }

    /** "Artist - Title.mp3" -> "Artist" (first artist only); null when the name has no such pattern. */
    fun artistFromName(fileName: String): String? {
        val base = prettyName(fileName)
        val i = base.indexOf(" - ")
        if (i <= 0) return null
        val first = base.substring(0, i).split(',', '&', ';').first()
        return LastFm.cleanArtist(first).trim().takeIf { it.length in 2..60 }
    }

    private fun decode(bytes: ByteArray, target: Int = 400): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (o.outWidth / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun key(artist: String): String =
        MessageDigest.getInstance("SHA-1").digest(artist.lowercase().trim().toByteArray()).joinToString("") { "%02x".format(it) }

    /** Blocking: the picture of an artist by name (kept on the phone). */
    fun artistPictureFor(ctx: Context, artist: String): Bitmap? = artistPicture(ctx, artist)

    private fun artistPicture(ctx: Context, artist: String): Bitmap? {
        val dir = File(ctx.cacheDir, "artist_art").apply { mkdirs() }
        val k = key(artist)
        val img = File(dir, "$k.jpg")
        val none = File(dir, "$k.none")
        if (img.isFile) return runCatching { BitmapFactory.decodeFile(img.path) }.getOrNull()
        // a failed lookup is not repeated for a week (offline, unknown artist)
        if (none.isFile && System.currentTimeMillis() - none.lastModified() < 7 * 24 * 3600_000L) return null
        return try {
            val url = fetchArtistUrl(artist)
            if (url == null) { none.writeText("x"); return null }
            val bytes = download(url) ?: return null
            img.writeBytes(bytes)
            decode(bytes)
        } catch (_: Exception) {
            null       // offline: tried again next time
        }
    }

    private fun get(url: String, maxBytes: Int): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            c.setRequestProperty("User-Agent", "VibeVault/7.7")
            if (c.responseCode !in 200..299) return null
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            c.inputStream.use { s ->
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    if (out.size() + n > maxBytes) return null
                    out.write(buf, 0, n)
                }
            }
            out.toByteArray()
        } finally {
            c.disconnect()
        }
    }

    private fun download(url: String): ByteArray? = get(url, 1_500_000)

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Deezer's public artist search (no key). The result must have the same name, so a wrong person is never shown. */
    private fun fetchArtistUrl(artist: String): String? {
        val bytes = get("https://api.deezer.com/search/artist?q=" + URLEncoder.encode(artist, "UTF-8") + "&limit=5", 200_000) ?: return null
        val arr = JSONObject(String(bytes, Charsets.UTF_8)).optJSONArray("data") ?: return null
        val want = norm(artist)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (norm(o.optString("name")) != want) continue
            val pic = o.optString("picture_medium").ifBlank { o.optString("picture") }
            // Deezer's "no picture" placeholder has an empty hash in its address
            if (pic.startsWith("https://") && !pic.contains("/artist//")) return pic
        }
        return null
    }
}

/** The picture of a file (see [TrackArt]); a branded tile with the app's colours while loading or when there is none. */
@Composable
fun TrackArtImage(item: HistoryItem, modifier: Modifier = Modifier, iconSize: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) { TrackArt.load(ctx, item) }
    }
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.background(Brush.linearGradient(listOf(cs.primary.copy(alpha = 0.55f), cs.secondary.copy(alpha = 0.45f)))),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Sym(if (item.format == "MP3") R.drawable.ic_queue_music else R.drawable.ic_play, iconSize, tint = cs.onPrimary)
        }
    }
}

/** Recognises Quran verses and recorded recitations from a title / file name / genre (Arabic and English words, common reciters). */
object QuranDetect {
    private val latin = Regex(
        "(?<![A-Za-z])(quran|qur'an|koran|surah|surat|tilawa|tilawah|recitation|tajweed|tajwid|tarteel|ayat al kursi|ayatul kursi|" +
            "mishary|alafasy|afasy|sudais|shuraim|minshawi|husary|hussary|abdulbasit|abdul basit|ghamdi|dossari|dosari|ajmi|qatami|muaiqly|hudhaify|tablawi|basfar)(?![A-Za-z])",
        RegexOption.IGNORE_CASE,
    )
    private val arabic = listOf(
        "القرآن", "قرآن", "القران", "سورة", "سوره", "تلاوة", "تلاوه", "ترتيل", "تجويد", "آية الكرسي", "اية الكرسي", "مصحف", "ختمة",
        "مشاري", "العفاسي", "السديس", "الشريم", "المنشاوي", "الحصري", "عبد الباسط", "سعد الغامدي", "الدوسري", "العجمي", "القطامي", "المعيقلي", "الحذيفي", "الطبلاوي",
    )

    fun isQuran(text: String): Boolean {
        if (text.isBlank()) return false
        return latin.containsMatchIn(text) || arabic.any { text.contains(it) }
    }
}
