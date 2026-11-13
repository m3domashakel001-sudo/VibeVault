package com.example.ytsaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One recommended track or video. */
data class Rec(val url: String, val title: String, val artist: String, val thumbUrl: String?, val durationSec: Long, val music: Boolean)

/** What the VV page shows for one recommendation row. */
data class RecState(val items: List<Rec> = emptyList(), val loading: Boolean = false, val failed: Boolean = false, val basis: String = "")

/**
 * Recommendations for the VV page.
 *  - music (YouTube Music): songs related to the artist being played, searched, downloaded or played recently;
 *  - videos (YouTube): videos related to the channels / titles being watched, searched or downloaded.
 * What counts as "interest" is kept on the phone (a short list). Only the artist / channel / title WORDS are sent to YouTube,
 * and the rows update by themselves when that list changes (a new search, a new download, a new artist playing).
 */
object Recommender {
    private const val KEY = "rec_interests"
    private const val MAX_INTERESTS = 24
    private const val STALE_MS = 6 * 3600_000L

    private val _music = MutableStateFlow(RecState())
    val music: StateFlow<RecState> = _music.asStateFlow()
    private val _video = MutableStateFlow(RecState())
    val video: StateFlow<RecState> = _video.asStateFlow()

    /** Raised every time the interests change; the page reloads on it. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    @Volatile private var loadedSigMusic = ""
    @Volatile private var loadedSigVideo = ""
    @Volatile private var loadedAt = 0L

    private class Interest(val kind: String, val value: String, val music: Boolean, val at: Long)   // kind: artist | channel | title

    // ------------------------------------------------------------------ what the user is interested in

    @Synchronized
    private fun read(ctx: Context): MutableList<Interest> = try {
        val a = JSONArray(Prefs.str(ctx, KEY) ?: "[]")
        (0 until a.length()).map { val o = a.getJSONObject(it); Interest(o.getString("k"), o.getString("v"), o.getBoolean("m"), o.optLong("t")) }.toMutableList()
    } catch (e: Exception) {
        mutableListOf()
    }

    @Synchronized
    private fun write(ctx: Context, list: List<Interest>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("k", it.kind).put("v", it.value).put("m", it.music).put("t", it.at)) }
        Prefs.putStr(ctx, KEY, a.toString())
    }

    private fun note(ctx: Context, kind: String, value: String, music: Boolean) {
        val v = value.trim().take(80)
        if (v.length < 2) return
        val list = read(ctx)
        list.removeAll { it.kind == kind && it.value.equals(v, true) && it.music == music }
        list.add(0, Interest(kind, v, music, System.currentTimeMillis()))
        write(ctx, list.take(MAX_INTERESTS))
        _version.value = _version.value + 1
    }

    /** The user searched for something: a name is an artist (music) or a topic (videos). */
    fun noteSearch(ctx: Context, query: String) {
        if (query.startsWith("http", true)) return
        note(ctx, "title", query, music = false)
        note(ctx, "artist", query, music = true)
    }

    /** The user played, previewed or downloaded something from a channel / artist. */
    fun noteItem(ctx: Context, title: String, uploader: String, audio: Boolean) {
        if (uploader.isNotBlank()) note(ctx, if (audio) "artist" else "channel", uploader.removeSuffix(" - Topic"), audio)
        else TrackArt.artistFromName(title)?.let { note(ctx, "artist", it, audio) }
        if (!audio) note(ctx, "title", cleanQuery(title), false)
    }

    private fun cleanQuery(title: String): String =
        prettyName(title).replace(Regex("[(\\[].*?[)\\]]"), " ").replace(Regex("\\s+"), " ").trim().split(' ').take(7).joinToString(" ")

    // ------------------------------------------------------------------ seeds

    private fun musicSeeds(ctx: Context, history: List<HistoryItem>): List<String> {
        val out = LinkedHashSet<String>()
        // 1. what is playing now
        val p = PlayerHolder.peek()
        val now = if (p != null) PlayerHolder.entries.value.getOrNull(p.currentMediaItemIndex) else null
        now?.let { TrackArt.artistFromName(it.name)?.let(out::add) }
        // 2. searches / downloads / previews, newest first
        read(ctx).filter { it.music }.forEach { out.add(it.value) }
        // 3. recently played songs
        val byUri = history.associateBy { it.uri }
        PlayLog.recent(ctx, 10).mapNotNull { byUri[it] }.filter { it.format == "MP3" }.forEach { TrackArt.artistFromName(it.name)?.let(out::add) }
        // 4. the artists of the saved MP3 files, most frequent first
        history.filter { it.format == "MP3" }.mapNotNull { TrackArt.artistFromName(it.name) }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(4).forEach { out.add(it.key) }
        return out.filter { !QuranDetect.isQuran(it) }.take(3)
    }

    private fun videoSeeds(ctx: Context, history: List<HistoryItem>): List<String> {
        val out = LinkedHashSet<String>()
        val p = PlayerHolder.peek()
        val now = if (p != null) PlayerHolder.entries.value.getOrNull(p.currentMediaItemIndex) else null
        if (now != null && now.format == "MP4") out.add(cleanQuery(now.name))
        read(ctx).filter { !it.music }.forEach { out.add(it.value) }
        val byUri = history.associateBy { it.uri }
        PlayLog.recent(ctx, 10).mapNotNull { byUri[it] }.filter { it.format == "MP4" }.forEach { out.add(cleanQuery(it.name)) }
        history.filter { it.format == "MP4" }.sortedByDescending { it.timeMillis }.take(4).forEach { out.add(cleanQuery(it.name)) }
        return out.filter { it.length >= 3 }.take(3)
    }

    // ------------------------------------------------------------------ loading

    /** Reloads the rows when the seeds changed (or the data is old). Safe to call often; does the work off the main thread. */
    suspend fun ensureLoaded(ctx: Context, history: List<HistoryItem>, wantMusic: Boolean, wantVideo: Boolean, force: Boolean = false) {
        val app = ctx.applicationContext
        val mSeeds = if (wantMusic) musicSeeds(app, history) else emptyList()
        val vSeeds = if (wantVideo) videoSeeds(app, history) else emptyList()
        val mSig = mSeeds.joinToString("|")
        val vSig = vSeeds.joinToString("|")
        val stale = System.currentTimeMillis() - loadedAt > STALE_MS
        val doMusic = wantMusic && mSeeds.isNotEmpty() && (force || stale || mSig != loadedSigMusic)
        val doVideo = wantVideo && vSeeds.isNotEmpty() && (force || stale || vSig != loadedSigVideo)
        if (!doMusic && !doVideo) return

        val known = history.map { sourceId(it.sourceUrl) }.filter { it.isNotBlank() }.toSet()
        if (doMusic) _music.value = _music.value.copy(loading = true, failed = false, basis = mSeeds.joinToString(", "))
        if (doVideo) _video.value = _video.value.copy(loading = true, failed = false, basis = vSeeds.joinToString(", "))
        withContext(Dispatchers.IO) {
            // the download engine (yt-dlp) starts a moment after the app; wait for it, but not forever
            val ready = withTimeoutOrNull(30_000L) { DownloadEngine.state.first { it.engineReady } } != null
            if (!ready) {
                if (doMusic) _music.value = _music.value.copy(loading = false, failed = true)
                if (doVideo) _video.value = _video.value.copy(loading = false, failed = true)
                return@withContext
            }
            if (doMusic) {
                val r = collect(mSeeds, 8, known) { q, n -> FeedLoader.musicSearch(q, n) }.filter { it.durationSec in 60..900 || it.durationSec == 0L }
                _music.value = RecState(r.map { Rec(it.url, it.title, it.uploader, it.thumbUrl, it.durationSec, true) }.take(24), false, r.isEmpty(), mSeeds.joinToString(", "))
                if (r.isNotEmpty()) loadedSigMusic = mSig
            }
            if (doVideo) {
                val r = collect(vSeeds, 8, known) { q, n -> FeedLoader.youtubeSearch(q, n) }.filter { it.durationSec >= 60 || it.durationSec == 0L }
                _video.value = RecState(r.map { Rec(it.url, it.title, it.uploader, it.thumbUrl, it.durationSec, false) }.take(24), false, r.isEmpty(), vSeeds.joinToString(", "))
                if (r.isNotEmpty()) loadedSigVideo = vSig
            }
            loadedAt = System.currentTimeMillis()
        }
    }

    /** Results of every seed, interleaved so each artist / channel is represented; what is already saved is left out. */
    private fun collect(seeds: List<String>, per: Int, known: Set<String>, search: (String, Int) -> List<FeedItem>): List<FeedItem> {
        val lists = seeds.map { q ->
            try { search(q, per) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        }
        val out = ArrayList<FeedItem>()
        val seen = HashSet<String>()
        var i = 0
        while (lists.any { i < it.size }) {
            for (l in lists) {
                val it = l.getOrNull(i) ?: continue
                val id = sourceId(it.url)
                if (id.isNotBlank() && (id in known || !seen.add(id))) continue
                out += it
            }
            i++
        }
        return out
    }

    // ------------------------------------------------------------------ pictures

    private val bmpCache = android.util.LruCache<String, Bitmap>(60)

    /** Blocking. The picture at [url] (kept on the phone), or null. */
    fun picture(ctx: Context, url: String): Bitmap? {
        bmpCache.get(url)?.let { return it }
        val dir = File(ctx.cacheDir, "rec_art").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val f = File(dir, "$name.img")
        val bmp = if (f.isFile) runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() else try {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 8_000; c.readTimeout = 8_000
                if (c.responseCode !in 200..299) null
                else {
                    val bytes = c.inputStream.use { it.readBytes() }
                    if (bytes.size > 2_000_000) null else { f.writeBytes(bytes); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                }
            } finally { c.disconnect() }
        } catch (_: Exception) { null }
        if (bmp != null) bmpCache.put(url, bmp)
        return bmp
    }
}

/**
 * The picture of a recommendation: its own cover / thumbnail; when that cannot be loaded and it is a song, the latest picture of
 * the same artist (Deezer, see [TrackArt]); otherwise the branded tile.
 */
@Composable
fun RecArt(rec: Rec, modifier: Modifier, iconSize: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, rec.url) {
        value = withContext(Dispatchers.IO) {
            rec.thumbUrl?.let { Recommender.picture(ctx, it) }
                ?: if (rec.music && TrackArt.enabled(ctx)) TrackArt.artistPictureFor(ctx, rec.artist) else null
        }
    }
    val cs = MaterialTheme.colorScheme
    Box(modifier.background(Brush.linearGradient(listOf(cs.primary.copy(alpha = 0.55f), cs.secondary.copy(alpha = 0.45f)))), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Sym(if (rec.music) R.drawable.ic_queue_music else R.drawable.ic_play, iconSize, tint = cs.onPrimary)
    }
}
