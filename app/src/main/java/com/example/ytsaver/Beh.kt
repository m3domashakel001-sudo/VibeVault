package com.example.ytsaver

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every optional piece of the interface has an on/off switch in Settings -> Behavior. This is the one place that knows
 * the keys and their default values, so a screen only asks `Beh.on(ctx, Beh.CARD_FAV)`.
 */
object Beh {
    // ---- Home (VV)
    const val HOME_BOTTOM_BUTTONS = "bt_home"        // Favorites / Playlists buttons under the VV page
    // ---- Media tab
    const val MEDIA_BOTTOM_BUTTONS = "bt_media"      // the same buttons under the Media page
    const val MEDIA_FLT_ALL = "mflt_all"
    const val MEDIA_FLT_AUDIO = "mflt_audio"
    const val MEDIA_FLT_VIDEO = "mflt_video"
    const val MEDIA_FLT_PANEL = "mflt_panel"         // filters inside the collapsible panel (funnel icon)
    // ---- History tab
    const val HIST_FLT_PANEL = "hflt_panel"
    const val HIST_FLT_ALL = "hflt_all"
    const val HIST_FLT_AUDIO = "hflt_audio"
    const val HIST_FLT_VIDEO = "hflt_video"
    const val HIST_FLT_YOUTUBE = "hflt_youtube"
    const val HIST_FLT_FACEBOOK = "hflt_facebook"
    const val HIST_FLT_MORE = "hflt_more"
    const val TB_COUNT = "tb_count"                  // "9 Downloads"
    const val TB_SORT = "tb_sort"
    const val TB_VIEW = "tb_view"                    // list / grid / sliders switch
    const val TB_MORE_MENU = "tb_more"               // Select + Clear all live in the 3-dot menu
    // ---- cards
    const val CARD_THUMB90 = "card_thumb90"          // 90 x 90 picture on every card
    const val CARD_FAV = "card_fav"
    const val CARD_SOURCE = "card_src"
    const val CARD_QUALITY = "card_quality"
    const val CARD_DATE = "card_date"
    const val CARD_PLAY_BTN = "card_play"            // the separate play button on a card
    // ---- player
    const val MINI_NEW = "mini_new"
    // ---- style
    const val ROUND_UI = "ui_round"                  // rounded, curved buttons / filters everywhere
    const val QURAN_COVER = "quran_cover"
    // ---- radio
    const val RADIO_SCALE = "radio_scale"
    // ---- vault
    const val VAULT_RECOVERY_ASK = "vault_recovery_ask"

    private val defaults: Map<String, Boolean> = mapOf(
        HOME_BOTTOM_BUTTONS to true, MEDIA_BOTTOM_BUTTONS to true,
        MEDIA_FLT_ALL to true, MEDIA_FLT_AUDIO to true, MEDIA_FLT_VIDEO to true, MEDIA_FLT_PANEL to false,
        HIST_FLT_PANEL to true, HIST_FLT_ALL to true, HIST_FLT_AUDIO to true, HIST_FLT_VIDEO to true,
        HIST_FLT_YOUTUBE to true, HIST_FLT_FACEBOOK to true, HIST_FLT_MORE to true,
        TB_COUNT to true, TB_SORT to true, TB_VIEW to true, TB_MORE_MENU to true,
        CARD_THUMB90 to true, CARD_FAV to true, CARD_SOURCE to true, CARD_QUALITY to true, CARD_DATE to true, CARD_PLAY_BTN to false,
        MINI_NEW to true, ROUND_UI to true, QURAN_COVER to true, RADIO_SCALE to true, VAULT_RECOVERY_ASK to true,
    )

    fun on(ctx: Context, key: String): Boolean = Prefs.bool(ctx, key, defaults[key] ?: true)
    fun set(ctx: Context, key: String, v: Boolean) = Prefs.putBool(ctx, key, v)

    /** View mode of a page: 0 = list, 1 = grid, 2 = sliders (Audio + Video rows). Media starts as sliders, History as list. */
    fun viewMode(ctx: Context, history: Boolean): Int =
        Prefs.str(ctx, if (history) "view_history" else "view_media")?.toIntOrNull() ?: if (history) 0 else 2

    fun setViewMode(ctx: Context, history: Boolean, mode: Int) =
        Prefs.putStr(ctx, if (history) "view_history" else "view_media", mode.toString())

    /** Observed by the theme so that the rounded style changes the moment it is switched. */
    var roundUi by mutableStateOf(true)
}

/** One line with a title, an optional explanation and a switch, bound to a [Beh] key. */
@Composable
fun BehSwitch(key: String, en: String, ar: String, descEn: String? = null, descAr: String? = null, onChanged: (Boolean) -> Unit = {}) {
    val ctx = LocalContext.current
    var on by remember(key) { mutableStateOf(Beh.on(ctx, key)) }
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(tr(en, ar), fontSize = 14.sp)
            if (descEn != null && descAr != null) Text(tr(descEn, descAr), color = Muted, fontSize = 12.sp)
        }
        Switch(checked = on, onCheckedChange = { on = it; Beh.set(ctx, key, it); onChanged(it) })
    }
}

/** Same, for the sections of the VV dashboard (HomePrefs). */
@Composable
fun HomeSectionSwitch(id: String, en: String, ar: String, descEn: String? = null, descAr: String? = null) {
    val ctx = LocalContext.current
    var on by remember(id) { mutableStateOf(HomePrefs.on(ctx, id)) }
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(tr(en, ar), fontSize = 14.sp)
            if (descEn != null && descAr != null) Text(tr(descEn, descAr), color = Muted, fontSize = 12.sp)
        }
        Switch(checked = on, onCheckedChange = { on = it; HomePrefs.set(ctx, id, it) })
    }
}

/** Choose the starting view of a page: List / Grid / Sliders. */
@Composable
fun ViewModeChoice(history: Boolean) {
    val ctx = LocalContext.current
    var mode by remember(history) { mutableStateOf(Beh.viewMode(ctx, history)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to tr("List", "قائمة"), 1 to tr("Grid", "شبكة"), 2 to tr("Sliders", "شرائح")).forEach { (m, label) ->
            VvChip(label, selected = mode == m, onClick = { mode = m; Beh.setViewMode(ctx, history, m) })
        }
    }
}

@Composable
fun BehGroupTitle(en: String, ar: String) {
    Text(
        tr(en, ar).uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
fun BehCard(en: String, ar: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr(en, ar), fontWeight = FontWeight.SemiBold)
            Spacer4()
            content()
        }
    }
}

@Composable
private fun Spacer4() = androidx.compose.foundation.layout.Spacer(Modifier.width(1.dp).heightIn(min = 4.dp))

/** The new, clearly grouped switches of Settings -> Behavior. */
@Composable
fun NewBehaviorSections() {
    BehGroupTitle("Home (VV)", "الرئيسية (VV)")
    BehCard("VV dashboard sections", "أقسام لوحة VV") {
        HomeSectionSwitch("shortcuts", "Shortcuts row", "صف الاختصارات")
        HomeSectionSwitch("recmusic", "Recommended music (YouTube Music)", "موسيقى مقترحة (YouTube Music)",
            "Tracks related to what you play and download. Only artist names are sent to YouTube.", "مقاطع مرتبطة بما تشغّله وتنزّله. يُرسل اسم الفنان فقط إلى YouTube.")
        HomeSectionSwitch("recvideo", "Recommended videos (YouTube)", "فيديوهات مقترحة (YouTube)",
            "Videos related to what you watch and download.", "فيديوهات مرتبطة بما تشاهده وتنزّله.")
        HomeSectionSwitch("played", "Recently played", "تم تشغيله مؤخرًا")
        HomeSectionSwitch("recent", "Recently downloaded", "التنزيلات الأخيرة")
        HomeSectionSwitch("videos", "Downloaded videos", "الفيديوهات المنزّلة", "Your saved and imported videos (off by default).", "فيديوهاتك المحفوظة والمستوردة (متوقف افتراضيًا).")
        HomeSectionSwitch("smart", "Smart playlists", "قوائم تشغيل ذكية")
        HomeSectionSwitch("library", "Your library tiles", "بطاقات مكتبتك")
        BehSwitch(Beh.HOME_BOTTOM_BUTTONS, "Favorites / Playlists buttons on this page", "أزرار المفضلة / القوائم في هذه الصفحة")
    }

    BehGroupTitle("Media tab", "تبويب الوسائط")
    BehCard("Media page", "صفحة الوسائط") {
        Text(tr("Starting view", "العرض الابتدائي"), color = Muted, fontSize = 12.sp)
        ViewModeChoice(history = false)
        BehSwitch(Beh.MEDIA_BOTTOM_BUTTONS, "Favorites / Playlists buttons on this page", "أزرار المفضلة / القوائم في هذه الصفحة")
        BehSwitch(Beh.MEDIA_FLT_PANEL, "Put the filters in a collapsible panel", "وضع الفلاتر في لوحة قابلة للطي", "Opened with the funnel icon.", "تُفتح بأيقونة القمع.")
        BehSwitch(Beh.MEDIA_FLT_ALL, "Filter: All", "فلتر: الكل")
        BehSwitch(Beh.MEDIA_FLT_AUDIO, "Filter: Audio", "فلتر: صوت")
        BehSwitch(Beh.MEDIA_FLT_VIDEO, "Filter: Video", "فلتر: فيديو")
    }

    BehGroupTitle("History tab", "تبويب السجل")
    BehCard("History page", "صفحة السجل") {
        Text(tr("Starting view", "العرض الابتدائي"), color = Muted, fontSize = 12.sp)
        ViewModeChoice(history = true)
        BehSwitch(Beh.HIST_FLT_PANEL, "Put the filters in a collapsible panel", "وضع الفلاتر في لوحة قابلة للطي", "Opened with the funnel icon; closed by default.", "تُفتح بأيقونة القمع؛ مغلقة افتراضيًا.")
        BehSwitch(Beh.HIST_FLT_ALL, "Filter: All", "فلتر: الكل")
        BehSwitch(Beh.HIST_FLT_AUDIO, "Filter: Audio", "فلتر: صوت")
        BehSwitch(Beh.HIST_FLT_VIDEO, "Filter: Video", "فلتر: فيديو")
        BehSwitch(Beh.HIST_FLT_YOUTUBE, "Filter: YouTube", "فلتر: YouTube")
        BehSwitch(Beh.HIST_FLT_FACEBOOK, "Filter: Facebook", "فلتر: Facebook")
        BehSwitch(Beh.HIST_FLT_MORE, "Filter: More sites", "فلتر: مواقع أخرى")
    }
    BehCard("Toolbar", "شريط الأدوات") {
        BehSwitch(Beh.TB_COUNT, "Downloads count", "عدد التنزيلات")
        BehSwitch(Beh.TB_SORT, "Sort button", "زر الترتيب")
        BehSwitch(Beh.TB_VIEW, "List / Grid / Sliders switch", "مبدّل القائمة / الشبكة / الشرائح")
        BehSwitch(Beh.TB_MORE_MENU, "Select and Clear all inside the 3-dot menu", "التحديد ومسح الكل داخل قائمة النقاط الثلاث", "Off = they are separate buttons in the toolbar.", "إيقاف = أزرار منفصلة في الشريط.")
    }
    BehCard("File cards", "بطاقات الملفات") {
        BehSwitch(Beh.CARD_THUMB90, "Picture on every card (90 × 90)", "صورة على كل بطاقة (90 × 90)")
        BehSwitch(Beh.CARD_FAV, "Favorite heart", "قلب المفضلة")
        BehSwitch(Beh.CARD_SOURCE, "Source (website)", "المصدر (الموقع)")
        BehSwitch(Beh.CARD_QUALITY, "Quality", "الجودة")
        BehSwitch(Beh.CARD_DATE, "Date", "التاريخ")
        BehSwitch(Beh.CARD_PLAY_BTN, "Separate play button", "زر تشغيل منفصل")
    }

    BehGroupTitle("Player", "المشغّل")
    BehCard("Mini player", "المشغّل المصغّر") {
        BehSwitch(Beh.MINI_NEW, "Compact bottom player with progress bar", "مشغّل سفلي مصغّر مع شريط التقدم")
        BehSwitch(Beh.QURAN_COVER, "Standard cover for Quran recitations", "غلاف موحّد لتلاوات القرآن")
    }

    BehGroupTitle("Radar & Radio", "الرادار والراديو")
    BehCard("FM Radio", "راديو FM") {
        BehSwitch(Beh.RADIO_SCALE, "Horizontal frequency scale with glowing indicator", "مقياس تردد أفقي بمؤشر متوهج")
    }

    BehGroupTitle("Style", "المظهر")
    BehCard("Buttons and filters", "الأزرار والفلاتر") {
        BehSwitch(Beh.ROUND_UI, "Rounded, curved button style everywhere", "نمط الأزرار الدائرية المنحنية في كل التطبيق") { Beh.roundUi = it }
    }

    BehGroupTitle("Vault", "الخزنة")
    BehCard("Vault PIN recovery", "استعادة رقم الخزنة") {
        BehSwitch(Beh.VAULT_RECOVERY_ASK, "Ask me to set a recovery email", "اطلب مني تعيين بريد استعادة", "After the first use of the vault.", "بعد أول استخدام للخزنة.")
    }
}
