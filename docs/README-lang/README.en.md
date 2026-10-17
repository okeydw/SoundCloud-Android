<p align="center">
<a href="../../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
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
<img src="../icon.png" width="160px" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Desktop version</a>] · [<a href="https://github.com/okeydw/SoundCloud-Android/tree/ios">iOS version</a>]</b><br>
<b>Unofficial SoundCloud client for Android</b><br>
No ads · No captcha · No censorship · Works in Russia
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/github/v/release/okeydw/SoundCloud-Android?style=for-the-badge&logo=github&color=0048FF&label=VERSION" alt="Version"/>
</a>

<a href="https://github.com/okeydw/SoundCloud-Android/releases">
<img src="https://img.shields.io/github/downloads/okeydw/SoundCloud-Android/total?style=for-the-badge&logo=github&color=0048FF&label=Downloads" alt="Downloads"/>
</a>

<a href="../../LICENSE">
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

**SoundCloud Android** is a native SoundCloud client for your phone. It is synced with [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop).

Written in **Kotlin + Jetpack Compose + Media3**, it runs natively and uses minimal resources.

The client ships in two versions with the same features, only the look differs:

> `SoundCloud Full` - Glass design in the style of the desktop version, for those who love the shiny stuff and have powerful phones.

> `SoundCloud Lite` - the simplified interface from the first versions, for those who value minimalism and have weaker phones.

### [iOS version (beta)](https://github.com/okeydw/SoundCloud-Android/tree/ios)

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

Next comes sign-in, either through SoundCloud or with a QR code from the Desktop version.

1. **QR from desktop (recommended).** In [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) open Settings -> Account -> Transfer session to another device, then scan the QR code.
2. **QR from phone.** On the sign-in screen tap "Show QR to sign in" and scan it where you are already signed in.
3. **Through the browser.** The "Sign in with browser" button opens the regular SoundCloud sign-in. This session may drop if you sign in somewhere else later.

---

## Screenshots

> Background, accent and text colors can be changed with an RGB palette. You can also add a background image and effects.

### SoundCloud (Full)

<p align="center">
<img src="../screenshots/full/wave.png" width="24%" />
<img src="../screenshots/full/player.png" width="24%" />
<img src="../screenshots/full/equalizer.png" width="24%" />
<img src="../screenshots/full/search.png" width="24%" />
</p>
<p align="center">
<img src="../screenshots/full/suggestions.png" width="24%" />
<img src="../screenshots/full/library.png" width="24%" />
<img src="../screenshots/full/playlists.png" width="24%" />
<img src="../screenshots/full/settings.png" width="24%" />
</p>

### SoundCloud Lite

<p align="center">
<img src="../screenshots/wave.png" width="24%" />
<img src="../screenshots/player.png" width="24%" />
<img src="../screenshots/player_immersive.png" width="24%" />
<img src="../screenshots/search.png" width="24%" />
</p>
<p align="center">
<img src="../screenshots/suggestions.png" width="24%" />
<img src="../screenshots/library.png" width="24%" />
<img src="../screenshots/history.png" width="24%" />
<img src="../screenshots/settings.png" width="24%" />
</p>

---

## Features

### Music

- **Wave and recommendations** - our own recommendation system based on your likes and listening history
- **Search** - tracks, artists, playlists, albums and vibe search (a neural network picks tracks based on context)
- **Player** - shuffle, repeat, queue, lyrics, likes and dislikes
- **Equalizer** - ready-made presets and your own custom preset
- **Playlists** - your own local playlists synced to SoundCloud, public or private, track order changes by drag and drop and is saved
- **History and stats** - listening time, top tracks and artists, charts by day and by hour
- **Profiles** - follow and unfollow, links from the SoundCloud profile
- **SoundCloud Auto (beta)** - scenarios for connecting to Bluetooth devices

### Offline mode

- Download tracks and playlists, offline mode with cache
- Manual cache limit and cache reset time
- Shared downloads and playlists between Full and Lite if both are installed (beta, works selectively)

### Import (beta)

- **YouTube / YouTube Music** - likes, playlists and albums through a Google account
- **Yandex Music** - likes, playlists and albums by token
- **Spotify** - likes, playlists and albums through a client_id

###### [Learn more](../import-lang/import.en.md)

> Important!!! We search for similar tracks, we do not import the audio itself
> (but this lets you get around censorship, fck censor)

---

## Feedback
Community: [Discord](https://discord.gg/Au3ebtfYu3)

Found a bug or have an idea? The easiest way is "Settings -> About -> Support": open an issue on GitHub or a ticket in Discord, the device info is filled in automatically. You can also [open an issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) manually and attach the logs: "Settings -> Logs -> Share".

---

## Stack

| Layer | Technologies |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, Haze (glass in Full) |
| Audio | Media3 (ExoPlayer + MediaSession), background service with notification |
| Network | OkHttp (+ disk cache), kotlinx.serialization |
| Images | Coil (+ Palette for tinting to the cover) |
| Camera | CameraX + ML Kit (QR scanner in Full), ZXing |
| Storage | SharedPreferences (settings, session), JSON indexes for downloads and playlists |

Backend: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

Host status is [here](https://status.soundcloud-desktop.fun/)

**Compatibility:** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

The full list of changes is in [CHANGELOG.md](../CHANGELOG.en.md).

---

## For developers

**Build:** JDK 17, Android SDK 35.

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK in app/build/outputs/apk/<flavor>/debug/
./gradlew testLiteDebugUnitTest                 # unit tests
./gradlew lintLiteDebug                         # lint (with baseline: ./gradlew updateLintBaseline)
```

**Sources:**

| Folder | What is inside |
| --- | --- |
| `app/src/main` | logic: API, player, downloads, playlists, import |
| `app/src/shared` | shared screens and dialogs (settings and so on) |
| `app/src/lite` | Lite interface |
| `app/src/full` | Full Glass interface |

**Release:** a `vX.Y.Z` tag (must match `versionName`) builds and signs both versions with one key and publishes them with notes from `CHANGELOG.md`.

**Backend:**

| Domain | Purpose |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | audio |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | track storage |
| `images.scnative.space` | covers |
| `pay.scnative.space` | STAR payment |
| `health.scnative.space` | connection check |
| `call-*.scnative.space` | "User network" |
| `*.relay.scnative.space` | backup routes |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | direct track download from SoundCloud |

QR sign-in protocol: [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) in the desktop repository.

---


## License

MIT. See the [LICENSE](../../LICENSE) file for details.

_SoundCloud is a trademark of SoundCloud Ltd. This app is not affiliated with SoundCloud._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/Download_SoundCloud_Android-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Download SoundCloud Android"/>
</a>
</p>

<p align="center">
<code>soundcloud android</code> · <code>soundcloud apk</code> · <code>soundcloud client</code> · <code>soundcloud for android</code> · <code>soundcloud no ads</code> · <code>soundcloud blocked</code> · <code>soundcloud not working</code> · <code>soundcloud android app</code> · <code>soundcloud player</code> · <code>soundcloud apk download</code> · <code>soundcloud alternative client</code> · <code>soundcloud without captcha</code> · <code>soundcloud music player</code>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/Download_SoundCloud_Android-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Download SoundCloud Android"/>
</a>
</p>
