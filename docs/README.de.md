<p align="center">
<a href="../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
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
<img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="160px" style="border-radius: 50%;" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Desktop-Version</a>]</b><br>
<b>Inoffizieller SoundCloud-Client für Android</b><br>
Keine Werbung · Kein Captcha · Keine Zensur
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
<img src="https://img.shields.io/badge/APK_herunterladen-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="APK herunterladen"/>
</a>
</p>

---

## Was ist das?

**SoundCloud Android** ist ein nativer SoundCloud-Client fürs Handy. Er nutzt dasselbe Backend wie [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop), sodass der gesamte Katalog direkt verfügbar ist.

Geschrieben in **Kotlin + Jetpack Compose + Media3**, läuft nativ und verbraucht minimale Ressourcen.

Derzeit ist es eine leichtgewichtige Version ohne Schnickschnack; das Design mache ich fertig, sobald ich sicher bin, dass es keine Bugs gibt und der Funktionsumfang vollständig ist. Danach teile ich es in zwei Versionen:

> `Full` — viele hübsche Effekte und ein Design nahe an der Desktop-Version.

> `Lite` — ein vereinfachtes Design für Minimalisten und schwächere Geräte.

### Es wird keine iOS-Portierung geben!

---

## Funktionen

- **Suche & Wave** — Suche nach Tracks/Künstlern/Playlists und ein persönlicher Empfehlungs-Feed
- **Player** — Hintergrundwiedergabe, Steuerung über die Benachrichtigung, Waveform, Shuffle/Repeat, Gesten
- **Bibliothek** — Likes/Dislikes, Playlists, Verlauf, Künstlerprofile
- **Offline** — Tracks herunterladen und Offline-Modus mit Cache
- **Darstellung** — helles/dunkles Design, immersiver Modus und 9 Sprachen

---

## Herunterladen

Geh zur [Releases-Seite](https://github.com/okeydw/SoundCloud-Android/releases/latest) und lade die `.apk` herunter.

**Installation:** Öffne die heruntergeladene Datei auf deinem Handy und erlaube die Installation aus unbekannten Quellen.

**Voraussetzungen:** Android 8.0 (Oreo) oder neuer.

---

## Screenshots

> Hintergrund, Textfarbe und Design lassen sich anpassen — eigenes Hintergrundbild, RGB-Palette und fertige Themes.

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

Bug gefunden oder eine Idee? — [öffne ein Issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose).

---

## Stack

| Schicht | Technologie |
| --- | --- |
| Sprache | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Audio | Media3 (ExoPlayer + MediaSession), Vordergrunddienst mit Benachrichtigung |
| Netzwerk | OkHttp (+ Disk-Cache), kotlinx.serialization |
| Bilder | Coil (+ Palette für die Färbung nach Cover) |
| Speicher | SharedPreferences (Einstellungen, Sitzung), JSON-Index heruntergeladener Tracks |

Dasselbe Backend wie [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) — die App ist ein weiterer Client dazu.

**Kompatibilität:** `minSdk 26` (Android 8.0) … `targetSdk 35`, `compileSdk 35`.

---

Vollständiges Änderungsprotokoll in [CHANGELOG.md](../CHANGELOG.md).

---

## Lizenz

MIT. Details in der Datei [LICENSE](../LICENSE).

_SoundCloud ist eine Marke von SoundCloud Ltd. Diese App ist nicht mit SoundCloud verbunden._
