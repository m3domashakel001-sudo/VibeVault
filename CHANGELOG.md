# 7.7.2

Every new option below has an on/off switch in **Settings -> Behavior**, in clearly named groups
(Home (VV), Media tab, History tab, Toolbar, File cards, Player, Radar & Radio, Style, Vault, then the older groups).

## History / Media
- History redesigned after the reference: new search bar, **collapsible filter panel** (funnel icon in the title bar; closed by
  default; choices are kept when it is closed), toolbar with count | sort, List / Grid / Sliders switch and a 3-dot menu that holds
  Select and Clear all, uniform cards (90 x 90 picture on every file, name up to 2 lines, small details row with source, format,
  quality, size and date, heart and vertical menu on the right).
- Filters: All / Audio / Video / YouTube / Facebook / More. Media has its own All / Audio / Video buttons. Each button can be switched
  off per page.
- **View mode per page**: Media starts as Sliders (Audio + Video), History as List; changing one never changes the other.
- Compact fixed bottom mini player: artwork, title, time, progress bar, play/pause, next (switchable).
- Favorites / Playlists buttons now also on the Media page; both pages' buttons are separate switches.

## VV page
- The downloaded-videos row is off by default (switch kept). New **Recommended music (YouTube Music)** and **Recommended videos
  (YouTube)** rows, based on what you play, search and download; they refresh by themselves when the artist / channel changes.
  Covers: the video thumbnail; for songs without one, the artist's latest picture.

## Style
- App-wide rounded, curved button / filter / tab style (one shape system; switchable). "Detector" is now **Radar** everywhere.
- Quran verses and recitations always get one standard cover.
- FM Radio redesigned after the reference: 88-108 scale, glowing purple indicator that glides, chevron / knob controls (classic dial switchable).

## Vault
- First use: asks for a **recovery e-mail**. "Forgot the PIN?" now offers **Erase the vault** and **Reset PIN**: confirm the e-mail (shown
  masked, eg********@gmail.com) -> a one-time PIN is e-mailed -> a new PIN is compulsory inside the vault. Needs the mail service in
  backend/vault-mail (see its README); without it the app says so and never creates a temporary PIN.

## Settings / About
- "Send email" button removed from Settings. About: "Offered By SunEye-Soft"; "Testers info" card is now "Release info".

versionName 7.7.2 (versionCode 101). applicationId unchanged.

---

# 7.7.1

## Navigation
- The old top tab bar is gone. **VV (Home) is the only main screen.** Search (the search bar), Download, Media and History open
  as their own pages from VV (History is the 5th shortcut after Identify, Radio, Download, Media). Back (button or gesture)
  returns to VV from any of them; Identify, Radio and Settings also return to VV.

## VV dashboard
- "Continue listening" is replaced by **Videos** (every saved/imported video).
- Audio cards show the **cover inside the file; if there is none, the artist's photo** (looked up once on Deezer by artist
  name, cached on the phone, can be switched off in Settings > Behavior > Artist pictures).

## Media / History
- New third view, **Sliders**: Audio and Video on ONE page as two swipeable rows (View menu: List / Grid / Sliders), in both
  Media and History. The old grid on/off choice is migrated.

## Radio / Detector / Settings
- Radio redesigned in the brand style (gradient station card with pulsing ON AIR dot, round controls, pill play button,
  card station list). Radio, Detector and Settings now show the faint logo watermark like the other pages.

## Player
- The logo is now a faint watermark with no frame; it can no longer cover the play button. The song name sits at the top.

## Widgets (4 shapes)
Wide bar (4x1) · Square card (2x2) · Round play button (1x1) · Quick dashboard (4x2: now playing + Identify / Radio /
Search / Media / History shortcuts that open that page).

versionName 7.7.1 (versionCode 100). applicationId unchanged.

---

# 7.7.0

## Security & stability (Phase 1)
See SECURITY_AUDIT.md and RELEASE.md. Signing passwords removed from source; CI uses secrets; MediaStore saves are
transactional (no orphan/pending files); 8 unsafe `!!` removed; WebView bridge restricted to the current https page; the
exported playback service no longer opens arbitrary `file://` paths for other apps; BootReceiver not exported;
storage is checked before a download starts and before a file is copied to Downloads.

## Design (Phase 2)
- New VibeVault identity: VV emblem (adaptive, monochrome, notification, splash, in-app) + brand palette theme (default for new users).
- `DesignSystem.kt`: spacing/radius/type tokens and reusable components (card, section header, buttons, chips, icon tile,
  empty / loading / error states, skeleton, logo).
- New **Home** dashboard (first tab): greeting, search, running-download card, shortcuts, Continue listening, Recently played,
  Recently downloaded, Smart playlists, Library shortcuts; every section can be switched off ("Customize").
- Player: new artwork card; Sleep timer, Equalizer and Lyrics buttons.
- Tablets / landscape: content is centred in a readable column (max 840 dp).

## New features (Phase 4)
Sleep timer (minutes or end of file) · Equalizer with the device's presets and per-band sliders · Recently played ·
Continue listening on Home · Smart playlists · Duplicate-media detection (Storage screen) · Backup & restore (merging JSON file) ·
Playlist sharing (titles + web links) · Home-screen playback widget · "Report a problem" (prefilled email, no personal data) ·
Lyrics shortcut (web search in the in-app browser; lyrics are not copied into the app).

## Detector (earlier in this release cycle)
Apple Music / Anghami lookup rewritten (parallel tracks, regional stores, length-based matching for Arabic titles).

versionName 7.7.0 (versionCode 99). applicationId unchanged (com.example.ytsaver).
