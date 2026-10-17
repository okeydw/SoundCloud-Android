<p align="center">
<a href="../../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/DE-0048FF?style=flat-square" alt="Deutsch"/></a>
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
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Desktop-Version</a>] · [<a href="https://github.com/okeydw/SoundCloud-Android/tree/ios">iOS-Version</a>]</b><br>
<b>Inoffizieller SoundCloud-Client für Android</b><br>
Ohne Werbung · Ohne Captcha · Ohne Zensur · In Russland verfügbar
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
<img src="https://img.shields.io/badge/APK_herunterladen-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"/>
</a>
</p>

---

## Was ist das?

**SoundCloud Android** ist ein nativer SoundCloud-Client fürs Handy. Er ist mit [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) synchronisiert.

Geschrieben in **Kotlin + Jetpack Compose + Media3**, läuft nativ und braucht nur minimale Ressourcen.

Den Client gibt es in zwei Versionen mit denselben Funktionen, nur das Aussehen ist anders:

> `SoundCloud Full` - Glass-Design im Stil der Desktop-Version, für alle, die Glanz und starke Handys mögen.

> `SoundCloud Lite` - die vereinfachte Oberfläche aus den ersten Versionen, für alle, die Minimalismus mögen oder schwächere Handys haben.

### [iOS-Version (Beta)](https://github.com/okeydw/SoundCloud-Android/tree/ios)

---

## Download

Geh auf die [Release-Seite](https://github.com/okeydw/SoundCloud-Android/releases/latest) und lade die passende `.apk` herunter:

| Datei | Version |
| --- | --- |
| `SoundCloud-Full-vX.Y.Z.apk` | SoundCloud, Glass-Design |
| `SoundCloud-Lite-vX.Y.Z.apk` | SoundCloud Lite, einfache Oberfläche |

**Installation:** öffne die heruntergeladene Datei auf dem Handy und erlaube die Installation aus unbekannten Quellen. Updates kommen danach direkt aus der App.

**Voraussetzungen:** Android 8.0 (Oreo) oder neuer.

---

## Anmelden

Danach folgt die Anmeldung, entweder über SoundCloud oder per QR-Code aus der Desktop-Version.

1. **QR vom Desktop (empfohlen).** Öffne in [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) Einstellungen -> Konto -> Sitzung auf ein anderes Gerät übertragen und scanne dann den QR-Code.
2. **QR vom Handy.** Tippe auf dem Anmeldebildschirm auf "QR zum Anmelden zeigen" und scanne ihn dort, wo du schon angemeldet bist.
3. **Über den Browser.** Der Button "Mit Browser anmelden" öffnet die normale SoundCloud-Anmeldung. Diese Sitzung kann verloren gehen, wenn du dich später woanders anmeldest.

---

## Screenshots

> Hintergrund-, Akzent- und Textfarbe lassen sich per RGB-Palette ändern. Außerdem kannst du ein Hintergrundbild und Effekte hinzufügen.

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

## Funktionen

### Musik

- **Welle und Empfehlungen** - eigenes Empfehlungssystem auf Basis deiner Likes und deines Hörverlaufs
- **Suche** - Tracks, Künstler, Playlists, Alben und Vibe-Suche (ein neuronales Netz wählt Tracks passend zum Kontext)
- **Player** - Shuffle, Wiederholen, Warteschlange, Songtexte, Likes und Dislikes
- **Equalizer** - fertige Presets und eigene Presets
- **Playlists** - eigene lokale Playlists mit Synchronisierung zu SoundCloud, öffentlich oder privat, die Reihenfolge lässt sich per Drag and Drop ändern und wird gespeichert
- **Verlauf und Statistik** - Hörzeit, Top-Tracks und Top-Künstler, Diagramme nach Tagen und Stunden
- **Profile** - folgen und entfolgen, Links aus dem SoundCloud-Profil
- **SoundCloud Auto (Beta)** - Szenarien für die Verbindung mit Bluetooth-Geräten

### Offline-Modus

- Tracks und Playlists herunterladen, Offline-Modus mit Cache
- Cache-Limit und Zeitpunkt zum Leeren manuell einstellbar
- Gemeinsame Downloads und Playlists zwischen Full und Lite, wenn beide installiert sind (Beta, funktioniert teilweise)

### Import (Beta)

- **YouTube / YouTube Music** - Likes, Playlists und Alben über ein Google-Konto
- **Yandex Music** - Likes, Playlists und Alben per Token
- **Spotify** - Likes, Playlists und Alben über eine client_id

###### [Mehr dazu](../import-lang/import.de.md)

> Wichtig!!! Wir suchen ähnliche Tracks und importieren nicht das Audio selbst
> (aber so lässt sich Zensur umgehen, fck censor)

---

## Feedback
Community: [Discord](https://discord.gg/Au3ebtfYu3)

Einen Bug gefunden oder eine Idee? Am einfachsten über "Einstellungen -> Über das System -> Support": öffne ein Issue auf GitHub oder ein Ticket in Discord, die Geräteinfos werden automatisch eingefügt. Du kannst auch direkt [ein Issue öffnen](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) und die Logs anhängen: "Einstellungen -> Logs -> Teilen".

---

## Stack

| Ebene | Technologien |
| --- | --- |
| Sprache | Kotlin |
| UI | Jetpack Compose, Material 3, Haze (Glas in Full) |
| Audio | Media3 (ExoPlayer + MediaSession), Hintergrunddienst mit Benachrichtigung |
| Netzwerk | OkHttp (+ Festplatten-Cache), kotlinx.serialization |
| Bilder | Coil (+ Palette zum Einfärben passend zum Cover) |
| Kamera | CameraX + ML Kit (QR-Scanner in Full), ZXing |
| Speicher | SharedPreferences (Einstellungen, Sitzung), JSON-Indizes für Downloads und Playlists |

Backend: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

Status der Hosts [hier](https://status.soundcloud-desktop.fun/)

**Kompatibilität:** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

Alle Änderungen stehen in [CHANGELOG.md](../../CHANGELOG.md).

---

## Für Entwickler

**Build:** JDK 17, Android SDK 35.

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK in app/build/outputs/apk/<flavor>/debug/
./gradlew testLiteDebugUnitTest                 # Unit-Tests
./gradlew lintLiteDebug                         # Lint (mit Baseline: ./gradlew updateLintBaseline)
```

**Quellcode:**

| Ordner | Inhalt |
| --- | --- |
| `app/src/main` | Logik: API, Player, Downloads, Playlists, Import |
| `app/src/shared` | gemeinsame Screens und Dialoge (Einstellungen usw.) |
| `app/src/lite` | Lite-Oberfläche |
| `app/src/full` | Glass-Oberfläche von Full |

**Release:** ein Tag `vX.Y.Z` (muss zu `versionName` passen) baut und signiert beide Versionen mit einem Schlüssel und veröffentlicht sie mit dem Text aus `CHANGELOG.md`.

**Backend:**

| Domain | Wofür |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | Audio |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | Track-Speicher |
| `images.scnative.space` | Cover |
| `pay.scnative.space` | STAR-Zahlung |
| `health.scnative.space` | Verbindungsprüfung |
| `call-*.scnative.space` | „Nutzernetz“ |
| `*.relay.scnative.space` | Ausweichrouten |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | direkter Track-Download von SoundCloud |

QR-Anmeldeprotokoll: [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) im Desktop-Repository.

---


## Lizenz

MIT. Details in der Datei [LICENSE](../../LICENSE).

_SoundCloud ist eine Marke von SoundCloud Ltd. Diese App steht in keiner Verbindung zu SoundCloud._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/SoundCloud_Android_herunterladen-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="SoundCloud Android herunterladen"/>
</a>
</p>

<p align="center">
<code>soundcloud android</code> · <code>soundcloud apk</code> · <code>soundcloud client</code> · <code>soundcloud für android</code> · <code>soundcloud ohne werbung</code> · <code>soundcloud gesperrt</code> · <code>soundcloud funktioniert nicht</code> · <code>soundcloud android app</code> · <code>soundcloud player</code> · <code>soundcloud apk download</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code> · <code>soundcloud musikplayer</code>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/SoundCloud_Android_herunterladen-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="SoundCloud Android herunterladen"/>
</a>
</p>
