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
<img src="icon.png" width="160px" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Desktop version</a>]</b><br>
<b>Unofficial SoundCloud client for Android</b><br>
No ads · No captcha · No censorship
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/github/v/release/okeydw/SoundCloud-Android?style=for-the-badge&logo=github&color=0048FF&label=VERSION" alt="Version"/>
</a>

<a href="https://github.com/okeydw/SoundCloud-Android/releases">
<img src="https://img.shields.io/github/downloads/okeydw/SoundCloud-Android/total?style=for-the-badge&logo=github&color=0048FF&label=Downloads" alt="Downloads"/>
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

## What is it?

**SoundCloud Android** is a native SoundCloud client for your phone. It uses the same backend as [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop), so the whole catalog is available directly.

Written in **Kotlin + Jetpack Compose + Media3**, it runs natively and uses minimal resources.

The client ships in two versions with the same features, only the look differs:

> `SoundCloud Full` - Glass design in the style of the desktop version: glass, auras, animations, its own screens and player.

> `SoundCloud Lite` - the familiar simple interface, for those who value minimalism and weaker phones.

### iOS is being planned, it may come soon

---

## Download

Go to the [releases page](https://github.com/okeydw/SoundCloud-Android/releases/latest) and download the `.apk` you need:

| File | Version |
| --- | --- |
| `SoundCloud-Full-vX.Y.Z.apk` | SoundCloud, Glass design |
| `SoundCloud-Lite-vX.Y.Z.apk` | SoundCloud Lite, simple interface |

**Install:** open the downloaded file on your phone and allow installing from unknown sources. After that, updates install from the app itself.

**Requirements:** Android 8.0 (Oreo) or newer.

---

## How to sign in

Without signing in the app won't go past the login screen (except offline mode with already downloaded tracks). Options:

1. **QR from desktop (recommended).** In [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) open "Transfer session to another device", then on your phone tap "Open camera" and scan the code with the built-in scanner.
2. **QR from your phone.** On the login screen tap "Show QR to sign in" and scan it where you're already signed in.
3. **In the browser.** The "Sign in with browser" button opens the regular SoundCloud login. Such a session may drop if you sign in somewhere else later.

---

## Screenshots

> Background, text color and theme can be customized: your own background picture, RGB palette and ready-made themes.

### SoundCloud (Full)

<p align="center">
<img src="screenshots/full/wave.png" width="24%" />
<img src="screenshots/full/player.png" width="24%" />
<img src="screenshots/full/equalizer.png" width="24%" />
<img src="screenshots/full/search.png" width="24%" />
</p>
<p align="center">
<img src="screenshots/full/suggestions.png" width="24%" />
<img src="screenshots/full/library.png" width="24%" />
<img src="screenshots/full/playlists.png" width="24%" />
<img src="screenshots/full/settings.png" width="24%" />
</p>

### SoundCloud Lite

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

## Features

Everything below is in both versions unless stated otherwise.

### Music

- **Wave and recommendations** - personal feed, shelves and picks, radio from a track or an artist
- **Search** - tracks, artists, playlists, albums and vibe search (describe a mood in words, beta)
- **Player** - background playback, notification controls, waveform, shuffle/repeat, gestures, lyrics (synced when available)
- **Crossfade** - between tracks, from 1 to 30 seconds
- **Equalizer** - system and custom presets, unwanted standard ones can be hidden
- **Audio output** - pick the speaker, wired headphones, Bluetooth or USB
- **Queue** - "play next" from the menu of any track

### Library

- **Likes and dislikes** - disliked tracks are skipped, there is a separate list
- **Playlists** - your own local playlists synced to SoundCloud, public or private, custom covers, track order changes by dragging and is saved
- **History and stats** - listening time, top tracks and artists, charts by day and by hour
- **Artist cards** - bio, socials, albums and similar artists
- **Profiles** - follow and unfollow, links from the SoundCloud profile
- **Artist blocking** - like a dislike, but for the whole artist: never shows up in the wave, radio and recommendations, the list is shared with Desktop

### Offline and downloads

- Download tracks and whole playlists, offline mode with cache
- Cache size and retention limits, upgrading the quality of downloaded tracks (for Star)
- Shared downloads and playlists between Full and Lite when both are installed (beta, works selectively)

### Import (beta)

- **YouTube / YouTube Music** - a track or playlist by link, the audio and cover come straight from YouTube, a playlist can be created right away (a VPN is needed in Russia)
- **Yandex Music** - likes, playlists and albums by token or by signing in to Yandex right in the app
- **Spotify** - playlists, albums and tracks by link

For Yandex Music and Spotify the app **looks for similar tracks on SoundCloud, it doesn't transfer the originals**. Found tracks can be queued for download right away.
(but it beats censorship, fck censoor)
### SoundCloud Auto (beta)

- Music starts on its own when the phone connects to your car over Bluetooth
- Pick the source (likes, wave, downloads, playlist), the devices and shuffle
- A separate equalizer preset for the car, the previous one comes back after disconnecting

### Network

- Several hosts and relay nodes, nodes are discovered automatically
- Manual node choice with ping and a 64 KB check: you can see if your provider cuts connections after 13 KB, and auto mode won't pick such a node
- The "no network/server down" banner shows up only after 15 seconds without connection

### Appearance

- 9 languages
- Themes, custom background (an image with blur and dimming), accent and text colors, RGB palette, theme export and import
- App icon: violet, blue or orange (a custom picture is added as a separate shortcut, an Android limitation)
- **Full only:** Glass interface, profile aura for Star (synced with Desktop), immersive player, smooth settings opening

### Other

- Auto-update: tap the new version banner, the APK downloads and the installer opens
- Sign in with QR (with the camera right in the app), by link or in the browser
- Your linked Discord is shown in the account
- Support in "About": a GitHub issue or a Discord ticket, device metrics are filled in automatically

---

## Feedback

Found a bug or have an idea? The easiest way is "Settings -> About -> Support": it opens a GitHub issue or a Discord ticket with metrics filled in. Or [open an issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) manually and attach logs: "Settings -> Logs -> Share".

---

## Stack

| Layer | Technologies |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, Haze (glass in Full) |
| Audio | Media3 (ExoPlayer + MediaSession), background service with notification |
| Network | OkHttp (+ disk cache), kotlinx.serialization |
| Images | Coil (+ Palette for cover-based tinting) |
| Camera | CameraX + ML Kit (QR scanner in Full), ZXing |
| Storage | SharedPreferences (settings, session), JSON indexes for downloads and playlists |

Same backend as [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop): the app works as one more client for it.

**Compatibility:** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

The full list of changes is in [CHANGELOG.md](../CHANGELOG.md).

---

## For developers

**Build:** JDK 17, Android SDK 35.

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK in app/build/outputs/apk/<flavor>/debug/
./gradlew testLiteDebugUnitTest                 # unit tests
./gradlew lintLiteDebug                         # lint (baseline: ./gradlew updateLintBaseline)
```

**Sources:**

| Folder | What's inside |
| --- | --- |
| `app/src/main` | logic: API, player, downloads, playlists, import |
| `app/src/shared` | shared screens and dialogs (settings etc.) |
| `app/src/lite` | Lite interface |
| `app/src/full` | Full Glass interface |

**Release:** a `vX.Y.Z` tag (must match `versionName`) builds and signs both versions with one key and publishes them with notes from `CHANGELOG.md`.

**Backend:**

| Purpose | Hosts |
| --- | --- |
| API | `api.scnative.space`, `api-star.scnative.space` (Star) |
| Streaming | `stream.scnative.space`, `stream-star.scnative.space` |
| Storage | `storage.scnative.space`, `storage-star.scnative.space` |
| Images | `images.scnative.space` |
| Relay nodes | `*.rN.relay.scnative.space`, list: `health.scnative.space/topology` |

QR sign-in protocol: [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) in the desktop repository.

---

## Data and trust

- The app works through the `*.scnative.space` backend (the same one as desktop). **The SoundCloud session and tokens are stored on that backend**, the phone keeps only the session ID.
- "Sign out" deletes the session both on the backend (`POST /auth/logout`) and all data on the phone.
- The direct fallback to SoundCloud (can be turned off in settings) goes to soundcloud.com without your session.
- The Yandex Music token for import is stored only on the phone. The app never sees your Yandex password: you sign in on Yandex's own page.

---

## License

MIT. Details in the [LICENSE](../LICENSE) file.

_SoundCloud is a trademark of SoundCloud Ltd. This app is not affiliated with SoundCloud._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/Download_SoundCloud_Android-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Download SoundCloud Android"/>
</a>
</p>
