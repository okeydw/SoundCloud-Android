<p align="center">
<a href="import.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="import.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="import.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="import.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="import.de.md"><img src="https://img.shields.io/badge/DE-0048FF?style=flat-square" alt="Deutsch"/></a>
<a href="import.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="import.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="import.tr.md"><img src="https://img.shields.io/badge/TR-777777?style=flat-square" alt="Türkçe"/></a>
<a href="import.ko.md"><img src="https://img.shields.io/badge/KO-777777?style=flat-square" alt="한국어"/></a>
</p>

# Musik importieren

Der Import befindet sich unter **Einstellungen -> Import (Beta)**.
So funktioniert es:

- **Spotify und Yandex Music.** Die App kopiert keine Originale, sondern sucht dieselben Tracks auf SoundCloud nach Titel und Künstler.
- **YouTube.** Du kannst den Track direkt von YouTube nehmen oder einen ähnlichen auf SoundCloud finden.
- Tracks und Playlists, die direkt von YouTube übernommen wurden, werden nicht mit SoundCloud synchronisiert
- Hat eine Playlist mehr als 500 Tracks, werden separate Playlists angelegt
- Tokens und Sitzungen werden nur auf dem Handy gespeichert.

## Per Link

1. Kopiere den Link zu einem Track, einer Playlist oder einem Album.
2. Füge ihn in das Feld "Per Link importieren" ein.
3. Wähle, wohin er hinzugefügt werden soll, und tippe auf "Importieren".

Öffentliche Links funktionieren ohne Anmeldung. Für private Playlists und Likes verbinde unten ein Konto.
> Spotify!
> Ohne Anmeldung werden aus einer Playlist nur die ersten 100 Tracks gelesen.


## Spotify

1. Öffne das [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) und melde dich mit deinem Konto an.
2. Tippe auf **Create app**. Name und Beschreibung sind egal.
3. Füge unter **Redirect URIs** die App-Versionen hinzu. Beide gehen, aber einzeln nacheinander:
   - Lite: `com.scd.android://spotify-auth`
   - Full: `com.scd.android.full://spotify-auth`
4. Wähle bei **Which API/SDKs are you planning to use?** die **Web API** aus und speichere.
5. Öffne **Settings -> User Management** und füge die E-Mail des Spotify-Kontos hinzu, aus dem du importieren willst.
6. Kopiere die **Client ID** (32 Buchstaben und Ziffern) und füge sie in der App ein: **Import -> Spotify -> Client ID**.
7. Der Button wird zu "App autorisieren". Tippe darauf und bestätige im Autorisierungsfenster.

Wenn alles stimmt, erscheinen dein Spotify-Name und dein Profilbild, danach kannst du Likes und private Playlists importieren.

Wichtig:

- Eine Spotify-App im Entwicklungsmodus funktioniert nur für Konten aus dem User Management. Der Besitzer der App im Dashboard braucht Spotify Premium, sonst antwortet Spotify mit 403.
- Lehnt Spotify die Client ID ab, erscheint unter dem Feld ein Fehler: prüfe die ID und die Redirect URI im Dashboard.

Ist das Feld leer, öffnet der Button "Bei Spotify anmelden" ein eingebautes Anmeldefenster. Das ist einfacher, klappt aber nicht immer.

## YouTube

1. Öffne **Import -> YouTube (Music)** und tippe auf **Konto hinzufügen**.
2. Melde dich im geöffneten Fenster bei Google an. Das Fenster schließt sich von selbst, und Name und Avatar des Kontos erscheinen.
3. **Likes importieren** sucht deine gelikten Videos auf SoundCloud und sammelt sie in einer Playlist.

Per Link lässt sich ein Track oder eine Playlist importieren. Es gibt zwei Modi:

- **Von YouTube nehmen.** Der Ton wird von YouTube heruntergeladen, die Playlist bleibt nur auf dem Handy.
- **Ähnliche auf SoundCloud finden.** Sucht dieselben Tracks auf SoundCloud.

Wenn Google meldet, dass der Browser nicht sicher ist, nutze ein VPN.

## Yandex Music

1. Öffne **Import -> Yandex Music** und tippe auf **Mit Yandex anmelden**.
2. Ein Browser öffnet sich. Wähle das gewünschte Konto.
3. Kopiere die Adresse der Seite, auf die du weitergeleitet wirst, geh zurück in die App und tippe auf "Link einfügen".
4. **Likes importieren** überträgt deine Lieblingstracks.

Es gibt auch eine automatische Anmeldung, du kannst sie ausprobieren.

Um das Konto zu wechseln, tippe auf **Konto vergessen** und melde dich neu an.
