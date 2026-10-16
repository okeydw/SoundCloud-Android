<p align="center">
<a href="import.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="import.en.md"><img src="https://img.shields.io/badge/EN-0048FF?style=flat-square" alt="English"/></a>
<a href="import.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="import.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="import.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="import.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="import.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="import.tr.md"><img src="https://img.shields.io/badge/TR-777777?style=flat-square" alt="Türkçe"/></a>
<a href="import.ko.md"><img src="https://img.shields.io/badge/KO-777777?style=flat-square" alt="한국어"/></a>
</p>

# Importing music

Import lives in **Settings -> Import (beta)**.
How it works:

- **Spotify and Yandex Music.** The app does not copy the originals, it looks for the same tracks on SoundCloud by title and artist.
- **YouTube.** You can take the track itself from YouTube or find a similar one on SoundCloud.
- Tracks and playlists taken directly from YouTube will not be synced with SoundCloud
- If a playlist has more than 500 tracks, it will be split into separate playlists
- Tokens and sessions are stored only on the phone.

## By link

1. Copy the link to a track, playlist or album.
2. Paste it into the "Import by link" field.
3. Choose where to add it and tap "Import".

Public links work without signing in. For private playlists and likes, connect an account below.
> Spotify!
> Without signing in, only the first 100 tracks of a playlist can be read.


## Spotify

1. Open the [Spotify developer dashboard](https://developer.spotify.com/dashboard) and sign in with your account.
2. Tap **Create app**. Any name and description will do.
3. In **Redirect URIs** add the app versions. You can add both, just add them one at a time:
   - Lite: `com.scd.android://spotify-auth`
   - Full: `com.scd.android.full://spotify-auth`
4. In **Which API/SDKs are you planning to use?** tick **Web API** and save.
5. Open **Settings -> User Management** and add the email of the Spotify account you will import from.
6. Copy the **Client ID** (32 letters and digits) and paste it in the app: **Import -> Spotify -> Client ID**.
7. The button changes to "Authorize the app". Tap it, and in the authorization window tap confirm.

If everything is right, your Spotify name and profile picture will appear, and you can import likes and private playlists.

Important:

- A Spotify app in development mode works only for accounts listed in User Management. The app owner in the dashboard needs Spotify Premium, otherwise Spotify answers 403.
- If Spotify rejects the Client ID, an error appears under the field: check the ID and the Redirect URI in the dashboard.

If the field is empty, the "Sign in to Spotify" button opens a built-in sign-in window. It is simpler, but does not always work.

## YouTube

1. Open **Import -> YouTube (Music)** and tap **Add account**.
2. Sign in to Google in the window that opens. The window closes by itself, and the account name and avatar appear.
3. **Import likes** looks for your liked videos on SoundCloud and collects them into a playlist.

A track or playlist can be imported by link. There are two modes:

- **Take from YouTube.** The audio is downloaded from YouTube, the playlist stays only on the phone.
- **Find similar ones on SoundCloud.** Looks for the same tracks on SoundCloud.

If Google says the browser is not secure, use a VPN.

## Yandex Music

1. Open **Import -> Yandex Music** and tap **Sign in with Yandex**.
2. A browser opens. Pick the account you need.
3. Copy the address of the page you are redirected to, then go back to the app and tap "Paste link".
4. **Import likes** brings over your favorite tracks.

There is also automatic sign-in, you can give it a try.

To switch accounts, tap **Forget account** and sign in again.
