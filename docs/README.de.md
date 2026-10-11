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
<img src="icon.png" width="160px" />
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

<a href="https://github.com/okeydw/SoundCloud-Android/releases">
<img src="https://img.shields.io/github/downloads/okeydw/SoundCloud-Android/total?style=for-the-badge&logo=github&color=0048FF&label=Downloads" alt="Downloads"/>
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

**SoundCloud Android** ist ein nativer SoundCloud-Client fürs Handy. Er nutzt dasselbe Backend wie [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop), daher ist der ganze Katalog direkt verfügbar.

Geschrieben in **Kotlin + Jetpack Compose + Media3**, läuft nativ und braucht kaum Ressourcen.

Den Client gibt es in zwei Versionen mit denselben Funktionen, nur das Aussehen unterscheidet sich:

> `SoundCloud Full` - Glass-Design im Stil der Desktop-Version: Glas, Auren, Animationen, eigene Bildschirme und eigener Player.

> `SoundCloud Lite` - die gewohnte schlichte Oberfläche, für alle, die Minimalismus und schwächere Handys schätzen.

### Eine iOS-Version ist in Planung, vielleicht kommt sie bald

---

## Download

Geh auf die [Release-Seite](https://github.com/okeydw/SoundCloud-Android/releases/latest) und lade die passende `.apk`:

| Datei | Version |
| --- | --- |
| `SoundCloud-Full-vX.Y.Z.apk` | SoundCloud, Glass-Design |
| `SoundCloud-Lite-vX.Y.Z.apk` | SoundCloud Lite, schlichte Oberfläche |

**Installation:** Öffne die geladene Datei auf dem Handy und erlaube die Installation aus unbekannten Quellen. Danach kommen Updates direkt aus der App.

**Voraussetzungen:** Android 8.0 (Oreo) oder neuer.

---

## Anmeldung

Ohne Anmeldung kommt man nicht über den Login-Bildschirm hinaus (außer im Offline-Modus mit bereits geladenen Titeln). Möglichkeiten:

1. **QR vom Desktop (empfohlen).** In [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) "Sitzung auf anderes Gerät übertragen" öffnen, am Handy "Kamera öffnen" tippen und den Code mit dem eingebauten Scanner scannen.
2. **QR vom Handy.** Auf dem Login-Bildschirm "QR zur Anmeldung zeigen" tippen und dort scannen, wo du bereits angemeldet bist.
3. **Im Browser.** Die Schaltfläche "Im Browser anmelden" öffnet den normalen SoundCloud-Login. Diese Sitzung kann abbrechen, wenn du dich später woanders anmeldest.

---

## Screenshots

> Hintergrund, Textfarbe und Theme lassen sich anpassen: eigenes Hintergrundbild, RGB-Palette und fertige Themes.

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

## Funktionen

Alles unten gibt es in beiden Versionen, sofern nicht anders angegeben.

### Musik

- **Welle und Empfehlungen** - persönlicher Feed, Regale und Sammlungen, Radio zu einem Titel oder Künstler
- **Suche** - Titel, Künstler, Playlists, Alben und Vibe-Suche (Stimmung in Worten beschreiben, Beta)
- **Player** - Wiedergabe im Hintergrund, Steuerung in der Benachrichtigung, Waveform, Shuffle/Repeat, Gesten, Songtexte (synchron, wenn vorhanden)
- **Weiche Übergänge** - zwischen Titeln, Länge von 1 bis 30 Sekunden
- **Equalizer** - System- und eigene Presets, unnötige Standard-Presets lassen sich ausblenden
- **Audioausgabe** - Lautsprecher, Kabelkopfhörer, Bluetooth oder USB wählbar
- **Warteschlange** - "Als Nächstes" im Menü jedes Titels

### Mediathek

- **Likes und Dislikes** - Titel mit Dislike werden übersprungen, es gibt eine eigene Liste
- **Playlists** - eigene lokale Playlists mit Sync zu SoundCloud, öffentlich oder privat, eigene Cover, die Reihenfolge lässt sich per Ziehen ändern und bleibt gespeichert
- **Verlauf und Statistik** - Hörzeit, Top-Titel und -Künstler, Diagramme nach Tag und Stunde
- **Künstlerkarten** - Biografie, soziale Netzwerke, Alben und ähnliche Künstler
- **Profile** - Folgen und Entfolgen, Links aus dem SoundCloud-Profil
- **Künstler blockieren** - wie ein Dislike, aber für den ganzen Künstler: taucht nicht in Welle, Radio und Empfehlungen auf, die Liste ist mit Desktop geteilt

### Offline und Downloads

- Titel und ganze Playlists herunterladen, Offline-Modus mit Cache
- Limit und Aufbewahrungsdauer des Caches, Qualitäts-Upgrade für bereits Geladenes (für Star)
- Geteilte Downloads und Playlists zwischen Full und Lite, wenn beide installiert sind (Beta, funktioniert nicht immer)

### Import (Beta)

- **YouTube / YouTube Music** - Titel oder Playlist per Link, Audio und Cover kommen direkt von YouTube, eine Playlist kann sofort erstellt werden (in Russland ist ein VPN nötig)
- **Yandex Music** - Likes, Playlists und Alben per Token oder per Yandex-Anmeldung direkt in der App
- **Spotify** - Playlists, Alben und Titel per Link

Für Yandex Music und Spotify **sucht die App ähnliche Titel auf SoundCloud, die Originale werden nicht übertragen**. Gefundenes kann sofort heruntergeladen werden.
(dafür schlägt es die Zensur, fck censoor)
### SoundCloud Auto (Beta)

- Musik startet automatisch, wenn sich das Handy per Bluetooth mit dem Auto verbindet
- Quelle (Likes, Welle, Downloads, Playlist), Geräte und Shuffle wählbar
- Eigenes Equalizer-Preset fürs Auto, nach dem Trennen kommt das vorige zurück

### Netzwerk

- Mehrere Hosts und Relay-Knoten, Knoten werden automatisch gefunden
- Manuelle Knotenwahl mit Ping und 64-KB-Test: Man sieht, wenn der Provider Verbindungen nach 13 KB kappt, und der Auto-Modus wählt so einen Knoten nicht
- Das Banner "kein Netz/Server nicht erreichbar" erscheint erst nach 15 Sekunden ohne Verbindung

### Aussehen

- 9 Sprachen
- Themes, eigener Hintergrund (Bild mit Unschärfe und Abdunklung), Akzent- und Textfarbe, RGB-Palette, Export und Import von Themes
- App-Symbol: violett, blau oder orange (ein eigenes Bild kommt als separate Verknüpfung, eine Android-Einschränkung)
- **Nur Full:** Glass-Oberfläche, Profil-Aura für Star (mit Desktop synchronisiert), immersiver Player, weiches Öffnen der Einstellungen

### Sonstiges

- Auto-Update: Auf das Banner zur neuen Version tippen, das APK lädt und der Installer öffnet sich
- Anmeldung per QR (mit der Kamera direkt in der App), per Link oder im Browser
- Das verknüpfte Discord wird im Konto angezeigt
- Support unter "Über": GitHub-Issue oder Discord-Ticket, Gerätemetriken werden automatisch eingefügt

---

## Feedback

Fehler gefunden oder eine Idee? Am einfachsten über "Einstellungen -> Über -> Support": Es öffnet sich ein GitHub-Issue oder ein Discord-Ticket mit Metriken. Oder [öffne ein Issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) manuell und hänge die Logs an: "Einstellungen -> Logs -> Teilen".

---

## Stack

| Schicht | Technologien |
| --- | --- |
| Sprache | Kotlin |
| UI | Jetpack Compose, Material 3, Haze (Glas in Full) |
| Audio | Media3 (ExoPlayer + MediaSession), Hintergrunddienst mit Benachrichtigung |
| Netzwerk | OkHttp (+ Festplatten-Cache), kotlinx.serialization |
| Bilder | Coil (+ Palette für coverbasierte Einfärbung) |
| Kamera | CameraX + ML Kit (QR-Scanner in Full), ZXing |
| Speicher | SharedPreferences (Einstellungen, Sitzung), JSON-Indizes für Downloads und Playlists |

Dasselbe Backend wie [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop): Die App ist einfach ein weiterer Client dafür.

**Kompatibilität:** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

Alle Änderungen stehen in [CHANGELOG.md](../CHANGELOG.md).

---

## Für Entwickler

**Build:** JDK 17, Android SDK 35.

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK in app/build/outputs/apk/<flavor>/debug/
./gradlew testLiteDebugUnitTest                 # Unit-Tests
./gradlew lintLiteDebug                         # Lint (Baseline: ./gradlew updateLintBaseline)
```

**Quellcode:**

| Ordner | Inhalt |
| --- | --- |
| `app/src/main` | Logik: API, Player, Downloads, Playlists, Import |
| `app/src/shared` | gemeinsame Bildschirme und Dialoge (Einstellungen usw.) |
| `app/src/lite` | Lite-Oberfläche |
| `app/src/full` | Glass-Oberfläche von Full |

**Release:** ein Tag `vX.Y.Z` (muss zu `versionName` passen) baut und signiert beide Versionen mit einem Schlüssel und veröffentlicht sie mit dem Text aus `CHANGELOG.md`.

**Backend:**

| Zweck | Hosts |
| --- | --- |
| API | `api.scnative.space`, `api-star.scnative.space` (Star) |
| Streaming | `stream.scnative.space`, `stream-star.scnative.space` |
| Speicher | `storage.scnative.space`, `storage-star.scnative.space` |
| Bilder | `images.scnative.space` |
| Relay-Knoten | `*.rN.relay.scnative.space`, Liste: `health.scnative.space/topology` |

Protokoll der QR-Anmeldung: [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) im Desktop-Repository.

---

## Daten und Vertrauen

- Die App arbeitet über das Backend `*.scnative.space` (dasselbe wie Desktop). **Sitzung und SoundCloud-Tokens liegen auf diesem Backend**, auf dem Handy liegt nur die Sitzungs-ID.
- "Abmelden" löscht die Sitzung im Backend (`POST /auth/logout`) und alle Daten auf dem Handy.
- Der direkte Ausweichweg zu SoundCloud (in den Einstellungen abschaltbar) geht ohne deine Sitzung zu soundcloud.com.
- Der Yandex-Music-Token für den Import bleibt nur auf dem Handy. Dein Yandex-Passwort sieht die App nie: Die Anmeldung läuft auf der Seite von Yandex selbst.

---

## Lizenz

MIT. Details in der Datei [LICENSE](../LICENSE).

_SoundCloud ist eine Marke der SoundCloud Ltd. Diese App steht in keiner Verbindung zu SoundCloud._

---

<p align="center">
<code>soundcloud android</code> · <code>soundcloud apk</code> · <code>soundcloud client</code> · <code>soundcloud für android</code> · <code>soundcloud ohne werbung</code> · <code>soundcloud gesperrt</code> · <code>soundcloud funktioniert nicht</code> · <code>soundcloud android app</code> · <code>soundcloud player</code> · <code>soundcloud apk download</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code> · <code>soundcloud musikplayer</code>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/SoundCloud_Android_herunterladen-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="SoundCloud Android herunterladen"/>
</a>
</p>
