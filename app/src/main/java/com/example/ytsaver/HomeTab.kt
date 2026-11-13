package com.example.ytsaver

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Which sections of the Home dashboard are shown. Stored on the phone; the user can change them with "Customize". */
object HomePrefs {
    val SECTIONS = listOf("shortcuts", "recmusic", "recvideo", "played", "recent", "videos", "smart", "library")
    /** The downloaded-videos row is off unless the user turns it on (the online recommendations took its place). */
    private val OFF_BY_DEFAULT = setOf("videos")
    fun on(ctx: android.content.Context, id: String) = Prefs.bool(ctx, "home_$id", id !in OFF_BY_DEFAULT)
    fun set(ctx: android.content.Context, id: String, v: Boolean) = Prefs.putBool(ctx, "home_$id", v)
}

private fun greeting(): Pair<String, String> {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "Good night" to "طاب مساؤك"
        h < 12 -> "Good morning" to "صباح الخير"
        h < 18 -> "Good afternoon" to "مساء الخير"
        else -> "Good evening" to "مساء الخير"
    }
}

/** The dashboard: what the app can do and what the user was doing, at a glance. */
@Composable
fun HomeTab(vm: DownloaderViewModel) {
    val ctx = LocalContext.current
    val history by vm.history.collectAsStateWithLifecycle()
    val job by vm.state.collectAsStateWithLifecycle()
    var customize by remember { mutableStateOf(false) }
    var recDialog by remember { mutableStateOf<Rec?>(null) }
    // the shown sections are read again after the dialog closes
    var version by remember { mutableStateOf(0) }
    val show = remember(version) { HomePrefs.SECTIONS.associateWith { HomePrefs.on(ctx, it) } }

    val recent = remember(history) { history.sortedByDescending { it.timeMillis }.take(12) }
    // every video saved or imported (newest first)
    val videos = remember(history) { history.filter { it.format == "MP4" }.sortedByDescending { it.timeMillis }.take(30) }

    fun play(item: HistoryItem) {
        if (fileExists(ctx, item)) vm.playNow(listOf(item))
        else toast(ctx, L.t("File not found. It may have been moved or deleted.", "الملف غير موجود. ربما تم نقله أو حذفه."))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Vv.lg, vertical = Vv.md), verticalArrangement = Arrangement.spacedBy(Vv.lg)) {
        // ---- greeting
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vv.md)) {
            VvLogo(44.dp)
            Column(Modifier.weight(1f)) {
                Text(tr(greeting().first, greeting().second), color = Muted, fontSize = VvText.caption)
                Text(tr("What are we playing?", "ماذا نشغّل اليوم؟"), fontSize = VvText.title, fontWeight = FontWeight.Bold)
            }
            androidx.compose.material3.IconButton(onClick = { customize = true }, modifier = Modifier.size(Vv.touch)) {
                Sym(R.drawable.ic_more, 22.dp, tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        // ---- search bar: opens the Search tab
        Row(
            Modifier.fillMaxWidth().height(Vv.touch + 4.dp).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(role = Role.Button) { vm.setTab(0) }
                .padding(horizontal = Vv.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Vv.md),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Muted)
            Text(tr("Search songs, artists, videos…", "ابحث عن أغنية أو فنان أو فيديو…"), color = Muted, fontSize = VvText.body)
        }

        // ---- a download that is running (tap for details)
        if (job.phase == Phase.Working) {
            VvCard(Modifier.fillMaxWidth(), onClick = { vm.setTab(1) }) {
                Text(tr("Downloading", "جارٍ التنزيل"), fontWeight = FontWeight.SemiBold)
                Text(job.message.replace("\n", " "), color = Muted, fontSize = VvText.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Vv.sm))
                LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
            }
        }

        // ---- shortcuts
        if (show["shortcuts"] == true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                VvIconTile(R.drawable.ic_music_search, tr("Radar", "رادار"), vm::openDetector, Modifier.weight(1f))
                VvIconTile(R.drawable.ic_radio, tr("Radio", "الراديو"), vm::openRadio, Modifier.weight(1f), accent = MaterialTheme.colorScheme.secondary)
                VvIconTile(R.drawable.ic_download, tr("Download", "التنزيل"), { vm.setTab(1) }, Modifier.weight(1f))
                VvIconTile(R.drawable.ic_folder, tr("Media", "الوسائط"), { vm.setTab(2) }, Modifier.weight(1f), accent = MaterialTheme.colorScheme.secondary)
                VvIconTile(R.drawable.ic_history, tr("History", "السجل"), { vm.setTab(3) }, Modifier.weight(1f))
            }
        }

        // ---- online recommendations (YouTube Music tracks, YouTube videos), refreshed when the artist / channel changes
        val wantMusic = show["recmusic"] == true
        val wantVideo = show["recvideo"] == true
        val recVersion by Recommender.version.collectAsStateWithLifecycle()
        LaunchedEffect(recVersion, history.size, wantMusic, wantVideo) { Recommender.ensureLoaded(ctx, history, wantMusic, wantVideo) }
        val recM by Recommender.music.collectAsStateWithLifecycle()
        val recV by Recommender.video.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        if (wantMusic) RecRow(
            title = tr("Recommended music", "موسيقى مقترحة"), source = "YouTube Music", state = recM, video = false,
            onRefresh = { scope.launch { Recommender.ensureLoaded(ctx, history, true, false, force = true) } },
            onPick = { recDialog = it },
        )
        if (wantVideo) RecRow(
            title = tr("Recommended videos", "فيديوهات مقترحة"), source = "YouTube", state = recV, video = true,
            onRefresh = { scope.launch { Recommender.ensureLoaded(ctx, history, false, true, force = true) } },
            onPick = { recDialog = it },
        )

        // ---- videos downloaded / imported
        if (show["videos"] == true && videos.isNotEmpty()) {
            VvSectionHeader(tr("Videos", "الفيديوهات"), tr("See all", "عرض الكل"), onAction = { FilterMemory.media.kind = 2; vm.setTab(2) })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Vv.md)) {
                items(videos, key = { it.uri }) { item ->
                    MediaCard(item, subtitle = "${item.format} • ${formatSize(item.sizeBytes)}") { play(item) }
                }
            }
        }

        // ---- recently played (the last files that were started)
        val playedItems = remember(history, version) {
            val byUri = history.associateBy { it.uri }
            PlayLog.recent(ctx, 12).mapNotNull { byUri[it] }
        }
        if (show["played"] == true && playedItems.isNotEmpty()) {
            VvSectionHeader(tr("Recently played", "تم تشغيله مؤخرًا"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Vv.md)) {
                items(playedItems, key = { it.uri }) { item ->
                    MediaCard(item, subtitle = "${item.format} • ${formatSize(item.sizeBytes)}") { play(item) }
                }
            }
        }

        // ---- recently downloaded
        if (show["recent"] == true) {
            VvSectionHeader(tr("Recently downloaded", "التنزيلات الأخيرة"), tr("See all", "عرض الكل"), onAction = { vm.setTab(2) })
            if (recent.isEmpty()) {
                VvCard(Modifier.fillMaxWidth()) {
                    VvEmptyState(
                        title = tr("Nothing saved yet", "لا يوجد شيء محفوظ بعد"),
                        message = tr("Find a song or a video, and save it. It will show up here.", "ابحث عن أغنية أو فيديو واحفظه. سيظهر هنا."),
                        actionLabel = tr("Start searching", "ابدأ البحث"),
                        onAction = { vm.setTab(0) },
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Vv.md)) {
                    items(recent, key = { it.uri }) { item ->
                        MediaCard(item, subtitle = "${item.format} • ${formatSize(item.sizeBytes)}") { play(item) }
                    }
                }
            }
        }

        // ---- smart playlists: built by the app from what is saved
        val smart = remember(history) { SmartPlaylists.build(history) }
        if (show["smart"] == true && smart.isNotEmpty()) {
            VvSectionHeader(tr("Smart playlists", "قوائم تشغيل ذكية"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Vv.sm)) {
                items(smart, key = { it.id }) { sp ->
                    VvChip(tr(sp.en, sp.ar) + " (${sp.items.size})", selected = false, onClick = {
                        val playable = sp.items.filter { fileExists(ctx, it) }
                        if (playable.isEmpty()) toast(ctx, L.t("File not found. It may have been moved or deleted.", "الملف غير موجود. ربما تم نقله أو حذفه."))
                        else vm.playNow(playable)
                    })
                }
            }
        }

        // ---- library shortcuts
        if (show["library"] == true) {
            VvSectionHeader(tr("Your library", "مكتبتك"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Vv.md)) {
                LibraryTile(tr("Favorites", "المفضلة"), R.drawable.ic_heart, Modifier.weight(1f), vm::openFavorites)
                LibraryTile(tr("Playlists", "قوائم التشغيل"), R.drawable.ic_queue_music, Modifier.weight(1f), vm::openPlaylists)
            }
        }
        Spacer(Modifier.height(Vv.lg))
    }

    recDialog?.let { r ->
        AlertDialog(
            onDismissRequest = { recDialog = null },
            title = { Text(r.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            text = { Text(listOf(r.artist, if (r.music) "YouTube Music" else "YouTube").filter { it.isNotBlank() }.joinToString(" • "), color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    Recommender.noteItem(ctx, r.title, r.artist, r.music)
                    vm.sendToDownload(r.url, asMp3 = r.music)
                    recDialog = null
                }) { Text(if (r.music) tr("Download as MP3", "تنزيل MP3") else tr("Download", "تنزيل")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        Recommender.noteItem(ctx, r.title, r.artist, r.music)
                        vm.setFeedQuery(r.title); vm.setTab(0); vm.searchFeed(); recDialog = null
                    }) { Text(tr("Find in Search", "بحث عنه")) }
                    TextButton(onClick = { recDialog = null }) { Text(tr("Close", "إغلاق")) }
                }
            },
        )
    }

    if (customize) {
        AlertDialog(
            onDismissRequest = { customize = false },
            title = { Text(tr("Customize Home", "تخصيص الرئيسية")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Vv.sm)) {
                    HomePrefs.SECTIONS.forEach { id ->
                        var on by remember(id) { mutableStateOf(HomePrefs.on(ctx, id)) }
                        Row(Modifier.fillMaxWidth().height(Vv.touch), verticalAlignment = Alignment.CenterVertically) {
                            Text(sectionName(id), Modifier.weight(1f))
                            Switch(checked = on, onCheckedChange = { on = it; HomePrefs.set(ctx, id, it) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { customize = false; version++ }) { Text(tr("Done", "تم")) } },
        )
    }
}

private fun sectionName(id: String) = when (id) {
    "recmusic" -> L.t("Recommended music", "موسيقى مقترحة")
    "recvideo" -> L.t("Recommended videos", "فيديوهات مقترحة")
    "videos" -> L.t("Downloaded videos", "الفيديوهات المنزّلة")
    "shortcuts" -> L.t("Shortcuts", "الاختصارات")
    "recent" -> L.t("Recently downloaded", "التنزيلات الأخيرة")
    "played" -> L.t("Recently played", "تم تشغيله مؤخرًا")
    "smart" -> L.t("Smart playlists", "قوائم تشغيل ذكية")
    else -> L.t("Your library", "مكتبتك")
}


/** A card with the picture of the file (video frame, album cover or the artist's photo), its name and one line of detail. */
@Composable
internal fun MediaCard(item: HistoryItem, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.width(150.dp).clip(RoundedCornerShape(Vv.rMedium)).clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Vv.xs),
    ) {
        TrackArtImage(item, Modifier.size(150.dp).clip(RoundedCornerShape(Vv.rMedium)), iconSize = 40.dp)
        Text(prettyName(item.name), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = VvText.body)
        Text(subtitle, color = Muted, fontSize = VvText.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LibraryTile(label: String, iconRes: Int, modifier: Modifier, onClick: () -> Unit) {
    VvCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vv.md)) {
            Sym(iconRes, 24.dp, tint = MaterialTheme.colorScheme.primary)
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** One online recommendation row: a header with the basis ("Based on …"), then covers and titles that scroll sideways. */
@Composable
private fun RecRow(title: String, source: String, state: RecState, video: Boolean, onRefresh: () -> Unit, onPick: (Rec) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Vv.sm)) {
        VvSectionHeader(title, tr("Refresh", "تحديث"), onAction = onRefresh)
        Text(
            source + if (state.basis.isNotBlank()) " • " + tr("based on ", "بناءً على ") + state.basis else "",
            color = Muted, fontSize = VvText.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        when {
            state.loading && state.items.isEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(Vv.md)) {
                items(4) { VvSkeleton(Modifier.width(if (video) 220.dp else 150.dp).height(if (video) 124.dp else 150.dp), Vv.rMedium) }
            }
            state.items.isEmpty() -> VvCard(Modifier.fillMaxWidth()) {
                Text(
                    if (state.failed) tr("Couldn't load recommendations. Check your connection and try again.", "تعذّر تحميل المقترحات. تحقق من الاتصال وحاول مجددًا.")
                    else tr("Play, search or download something and recommendations will appear here.", "شغّل أو ابحث أو نزّل شيئًا وستظهر المقترحات هنا."),
                    color = Muted, fontSize = VvText.body,
                )
            }
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(Vv.md)) {
                items(state.items, key = { it.url }) { r ->
                    val w = if (video) 220.dp else 150.dp
                    Column(
                        Modifier.width(w).clip(RoundedCornerShape(Vv.rMedium)).clickable(role = Role.Button) { onPick(r) },
                        verticalArrangement = Arrangement.spacedBy(Vv.xs),
                    ) {
                        RecArt(r, Modifier.width(w).height(if (video) 124.dp else 150.dp).clip(RoundedCornerShape(Vv.rMedium)), iconSize = 40.dp)
                        Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = VvText.body)
                        if (r.artist.isNotBlank()) Text(r.artist, color = Muted, fontSize = VvText.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
