<p align="center">
<a href="../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-0048FF?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="README.tr.md"><img src="https://img.shields.io/badge/TR-777777?style=flat-square" alt="Türkçe"/></a>
<a href="README.ko.md"><img src="https://img.shields.io/badge/KO-777777?style=flat-square" alt="한국어"/></a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="160px" style="border-radius: 50%;" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>Unofficial SoundCloud client for Android</b><br>
No ads · No captcha · No censorship
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/github/v/release/okeydw/SoundCloud-Android?style=for-the-badge&logo=github&color=0048FF&label=VERSION" alt="Version"/>
</a>

<a href="../LICENSE">
<img src="https://img.shields.io/badge/License-MIT-0048FF?style=for-the-badge" alt="License"/>
</a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/Download_APK-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"/>
</a>
</p>

---

> [!WARNING]
> ⚠️ **Because of the main API's and SoundCloud's security systems, signing in may log you out of another session.** If you're logged in on another device (e.g. the desktop client), signing in here may "unlink" that session, and vice versa. This is a backend limitation, not an app bug — just sign in again where you need to.

---

## What is it?

**SoundCloud Android** is a native SoundCloud client for your phone. It uses the same backend as [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop), so the whole catalog is available directly.

Written in **Kotlin + Jetpack Compose + Media3**, it runs natively and uses minimal resources.

For now it's a lightweight version without frills; I'll polish the design once I'm sure there are no bugs and the feature set is complete, then split it into two versions:

> `Full` — lots of pretty sparkles and a design close to the desktop version.

> `Lite` — a simplified design for those who value minimalism and weaker phones.

### There will be no iOS port!

---

## Features

- **Search & Wave** — search tracks/artists/playlists and a personal recommendations feed
- **Player** — background playback, notification controls, waveform, shuffle/repeat, gestures
- **Library** — likes/dislikes, playlists, history, artist profiles
- **Offline** — download tracks and an offline mode with cache
- **Appearance** — dark/light theme, immersive mode and 9 languages

---

## Download

Go to the [releases page](https://github.com/okeydw/SoundCloud-Android/releases/latest) and grab the `.apk`.

**Install:** open the downloaded file on your phone and allow installing from unknown sources.

**Requirements:** Android 8.0 (Oreo) or newer.

---

## Screenshots

<p align="center">
<img src="screenshots/wave.png" width="24%" />
<img src="screenshots/player.png" width="24%" />
<img src="screenshots/player_immersive.png" width="24%" />
<img src="screenshots/search.png" width="24%" />
</p>
<p align="center">
<img src="screenshots/suggestions.png" width="24%" />
<img src="screenshots/library.png" width="24%" />
<img src="screenshots/history.png" width="24%" />
<img src="screenshots/settings.png" width="24%" />
</p>

---

## Feedback

Found a bug or have an idea? — [open an issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose).

---

## Stack

| Layer | Tech |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Audio | Media3 (ExoPlayer + MediaSession), foreground service with a notification |
| Network | OkHttp (+ disk cache), kotlinx.serialization |
| Images | Coil (+ Palette for tinting to the cover) |
| Storage | SharedPreferences (settings, session), JSON index of downloaded tracks |

Same backend as [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) — the app acts as another client to it.

**Compatibility:** `minSdk 26` (Android 8.0) … `targetSdk 35`, `compileSdk 35`.

---

## Structure

```
app/src/main/java/com/scd/android/
  App.kt              - Application: session/cache/settings init, Coil cover loader via proxy (unique key per image)
  MainActivity.kt     - entry point, navigation (Search / Wave / Me), mosaic tiles, "no internet" banner, filtering unavailable tracks, deep links from notifications
  Api.kt              - API client: search, streaming, covers, track & playlist likes/dislikes, history, data models; multi-tier HTTP cache (cold data cached long)
  NetMonitor.kt       - network check (Wi-Fi / mobile) + offline fallback from cache
  Prefs.kt            - settings (theme, language, offline, immersive) + cached username
  LocaleHelper.kt     - app locale override (language switch)

  PlaybackService.kt  - MediaSessionService: background player, notification, like/shuffle in it, open player on tap
  Player.kt           - mini and full-screen player (waveform, gestures, shuffle/repeat, immersive, marquee, add to playlist)
  NowPlaying.kt       - global player state + navigation/playlist-refresh events (PlaylistEvents, NavEvents)

  WaveScreen.kt       - Wave: pager feed, swipes and double-tap = like with animation, refresh
  ArtistScreen.kt     - artist profile: tracks and playlists (incl. offline from downloads)
  PlaylistScreen.kt   - playlist screen: track list, like playlist, download all / cancel
  LibraryScreen.kt    - "Me" tab: greeting, likes, downloads, own & liked playlists, history + settings screen

  Likes.kt / Dislikes.kt - track like/dislike state (mutually exclusive, synced with backend)
  LikedPlaylists.kt   - liked-playlist state (synced with backend)
  Downloads.kt        - download tracks to a private folder, index, progress notification, cancel and deep link
  Genres.kt           - genre list for tiles on empty search

app/src/main/res/
  drawable/           - vector icons (Lucide style) + logo
  values/, values-*/  - strings in 9 languages + values-night (dark theme)

proguard-rules.pro    - R8 rules for the release build (serialization / OkHttp)
.github/workflows/    - CI: lint, tests, debug APK build
```

Full changelog is in [CHANGELOG.md](../CHANGELOG.md).

---

## License

MIT. Details in the [LICENSE](../LICENSE) file.

_SoundCloud is a trademark of SoundCloud Ltd. This app is not affiliated with SoundCloud._
