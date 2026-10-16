## [0.4.5] Bug fixes + more testing of track imports

> The original [changelog in Russian](../CHANGELOG.md)

<h1>Added </h1>

- Import from Spotify, YouTube (Music), Yandex Music

    - Polished the "Import" UI a bit

    - Spotify: sign in with a Client_id

    - YouTube: sign in with Google (you can pull the track directly or find a similar one, from Russia you will need a VPN)

    - Yandex Music: sign in with a token, there are two modes, automatic and manual, the second one just in case

    - Import guide in the repo, links to it are in the settings

- SQ/HQ display logic is now the same as in the Desktop version, so nobody gets confused

- Downloaded tracks remember their quality and upgrade themselves to HQ in the background once HQ shows up (for Star)

- The shuffle icon of a playlist, artist or album lights up if that one is what is playing right now

- Playlists open offline

- Made it possible to turn effects off completely

- Artist stats

- Removing a download from the track menu

- Search through your own lists in "Me" and in playlists

- Shuffle and repeat states are now saved

- Double "back" to exit the app

- Added scenarios to SoundCloudAuto, and the queue no longer resets if there already is one

- Offline mode now decides on its own when to turn on

- HLS tracks now play. As for the DRM coffins, the situation is interesting: after the fact we have the key and the simplest AES, but decrypting needs a big dependency, which I do not want to pull in. Maybe if a lot of people ask, I will do it, for now it is good enough

<h1>Changed </h1>

- On the "Me" tab there is now a create playlist button instead of shuffle

- Import has its own icon in the settings

- Settings items no longer overlap each other, the background gradient is gone

- SQ/HQ labels in Full only show with "Stream tags" turned on

- Dependencies updated

<h1>Fixed </h1>

- The "Equalizer" label should fit on the screen

- With shuffle, tracks lost part of their data (Go+, quality)

- Offline: playlists showed "Network error", playback got stuck on tracks that were not downloaded

- Stats display

- The "Forget account" button in the import sections now also clears the built-in browser cache

- Crash when opening the same screen again

- Fixed the host rotation logic

- "Flow" keeps the queue

- If the node responsible for track previews died, the client goes to SoundCloud for the cover

**Something broke? - Issues are your friend**

---

## [0.4.4] Full release

> Full version, release it already

<h1>Full version </h1>

- Split the project into Full and Lite versions. 

    - Complete UI/UX rework

    - Improved the QR code, though it is not really needed anymore since fidni already fixed things, but let it stay

- Added sync between the two kinds of clients, it works very selectively, so it may not work (beta)

- Full is now "SoundCloud", and the old version is "SoundCloud Lite"

**Both versions will be supported. They share the same insides, so they are not going anywhere**

<h1>Added </h1>

- The APK now downloads itself and hands off to the system installer

- Import (Settings -> Import) - BETA!!!

    - YouTube / YouTube Music - you get the audio track and the cover straight from YouTube (you need a VPN if you are in Russia)

    - Yandex Music: can be parsed with a token or by signing in through the app, **it will look for similar tracks, not import them**

    - Spotify: Same thing - **it will look for similar tracks, not import them**

    - 500 track limit, I also recommend creating separate playlists

- Listening stats, like in the latest version of the Desktop app

- Artist cards, like in some version of the Desktop app

- Blocking an artist (like a dislike)

- Linked Discord is shown

- Added a menu for the track list

- Changing the order in local playlists

- You can now add your own covers for playlists, but only locally

- You can now create your own presets in the equalizer and delete the junk

- SoundCloud Auto: automatic scenarios for devices/cars (beta)

- Audio output selection

- Crossfade settings

- Vibe search is back (let the neural net keep learning, it really is trying)

- Local playlists: public/private, background sync and retry if the server went down

- Manual relay node selection with ping. Nodes are now checked on 64 KB - if your ISP cuts the connection after 13 KB, you see that node right away and auto will not pick it

- App icon: purple, blue or orange

- "About" now has support buttons for the Android app and the API: straight to a GitHub issue or a Discord ticket, the device info with uid and profile link is filled in automatically

- Background color presets

- Links to playlists, albums and profiles now open in the app

<h1>Fixed </h1>

- The "no internet/server is dead" banner now only pops up after 15 seconds of downtime

- Lite no longer kicks you to the home screen when the network drops

- Now if a track did not load, the notification shows it as not downloaded

- Lite: the mini player now follows the swipe as a whole

**Lotsa different updates, so there may be bugs, open a ticket and describe the problem**

---

## [0.4.3] Bug fixes

> tell me, are these bugs in the room with us?

<h1>Added </h1>

- Synced/static lyrics, and if there are none - well, there are none

- Equalizer, now you can play with the sound (careful, the native equalizer hurts the ears)

- There is now a separate playlist with dislikes, which you can remove

- Star subscribers can now switch between the paid and the free API

- If the API died, we now download straight from SoundCloud

- Added a placeholder as a grid of track covers in a playlist, because the API was being greedy and did not want to give me proper covers

- You can now like tracks offline, like on desktop

- Relay nodes are found automatically, if I go on a bender they will keep getting pulled in without my updates

- History is written only if the track was really listened to: 30 seconds or half its length (otherwise the recommendation system does crap)

- Disliked tracks are skipped

- Wanted to do some magic with "Wave" and it broke (I think the API died, if not, I will fix it next update)

- Self-repair of audio tracks.

- Improved the logic of API calls, delays are now bound to the API

- Improved the logs

- Improved the cache system (I have been fixing it for like 4 updates now and I am not going to stop)

- Improved security
  
    - Added CSRF so nobody steals your account, better now than later
  
    - When you log out, all your data gets wiped just to be safe

    - There were a couple more critical vulns - fucking closed them

<h1>Fixed </h1>

- Signing in with a link, also made it faster (I recommend signing in with QR if you can)

- If at least three hosts return a broken track, the track gets dropped (before, it cycled through the hosts in a loop)

- "Stubs" now finish downloading on their own (if not, clear the cache and storage)

- HQ and the regular version of a track no longer get mixed in one cache

- The HQ/SQ label in the player shows what is actually playing, not just whether you have Star (the crutch was cool, but now the logic is fine)

- The server check and the position timer no longer spin in the background when the app is minimized

- Track waveforms load through the proxy first. Images are no longer cached twice

- A track is written to history when it actually starts playing, not when you just scrolled past it

- Star status is now pulled right at startup. Before, it only updated if you opened the "Me" tab, and until then Go+ went the regular way

- If the server sent `null`, your device would explode (joke, it will just treat the response as invalid)

- "Delete all downloads" now asks for confirmation

- The host check at startup no longer hangs for a minute on a dead address

- Encrypted tracks showed 0:30 for Star. Now it updates and caches it

- A playlist loaded only as you scrolled, so "shuffle" only shuffled what you had scrolled to, fixed that, on desktop this bug is still there

- Playlists without a cover now get their own tile made of the first four track covers (2x2). If there are fewer than four - we show the first one, if it is totally empty - a note

- Fixed search, now it works faster

- The client now sends requests to the API "optimally", instead of +100500 requests at once

- The notification had a blue background (placeholder), it was bugged because of the cache, now it is fine
  
- Behind a VPN the app thought there was no internet, now it does not think so

- A track cut off in the middle of playback because it thought the stream had died, now it does not think so

- Links from "Share" did not open the right track, now they do

- The shuffle button in the notification could not be pressed, now it can

- Likes did not get removed under certain conditions, now they do

- Swiping to the previous track in the mini player used the same function as regular seeking, so it just restarted the track, now it does not do that

---

## [0.4.2] Aaaaand screw you, encryption

> The long-awaited Go+ (it plays now, but with a 10-30 second delay, then the cache loads and it will be fine)

<h1>Added </h1>

- If you are not being a nerd about it, a cache system and goplus tracks are done, and if you are being a nerd, then...

    - All tracks start the fastest way, without `hq`. The Star host, `hq` and storage kick in only if the fast way stays silent for 12 seconds or fails

    - Direct delivery from storage: `storage-star` (premium) -> `storage`, bypassing `/stream`

    - Fallback to `/download`: if storage is empty too

    - The source that worked is remembered - the second time the track starts right away

    - The logs now show which candidates the backend returned for a track

    - Cache data source: seeking now without lag

    - Covers, avatars and waveforms are cached too now

- A banner in the player "Track is encrypted - getting it another way" with a spinner.

- A "Play in HQ" toggle in Storage. On by default for Star, cannot be switched without Star

- Cache settings in Storage: space limit (1/2/5/10/20 GB or no limit, 5 GB by default) and retention time (1/3/7/30/90 days or forever, a week by default).

- A "Clear cache" button right there, and the cache itself is now a separate segment in the storage bar - you can see how much it takes.

<h1>Visuals </h1> 

- You can set any picture

    - Dimming and blur strength sliders (0-60)

    - Blur wants Android 12. On 8-11 the toggle is locked

- Full RGB palette: three sliders and a HEX field, or you can paste your own color code.

- Theme export and import. You can share finished themes in Discord.

- Transparency of the header and the bottom bar - with two separate sliders

- All of this moved to "Advanced" inside Appearance, so it does not get in the way in the general settings list

<h1>Fixed </h1>

- Mostly the cache and goplus

  - The waveform no longer blinks when you reopen the player

  - If the track is not in storage, we try the `sq` cascade

  - Playlists and liked tracks loaded from the network again on every visit. Now they are cached (use the app a bit and it will fly)

- The Star badge now changes along with the color theme

- Made placeholders for tracks without a cover

- Seeking through a track now without pauses

**P.S. A whole lot was done there, so there will most likely be bugs, so write what you find - we will fix it**

---

## [0.4.1] Half-update

> I could not do it right now, but in 0.4.2 ...

<h1>Added </h1>

- Addressing across several hosts

- Added Storage, tracks now load really fast

- You can now pick another color besides orange, later I will add background selection and a full color palette

- Added your avatars

- Some debug features to trace errors and bugs, I am fucking tired of fixing things blind

- A "new version available on GitHub" banner

- QR sign-in, well, more like the option for it, later I will make a pull request to someone's repo, and then there will be a proper scanner

<h1>Fixed </h1>

- Display of the memory taken by tracks

### ATTENTION, THIS UPDATE DOES NOT ADDRESS THE PROBLEM WITH PLAYING GO+ TRACKS, THAT WILL BE DONE LATER

---

## [0.4.0] Open it - and everything is already in place

> I got off the bender, yay

<h1>Added </h1>

-  Improved the cache system

- The infinite feed is now truly infinite - both in Wave and in the tiles

- ~~Stole it from Spotify~~ The settings are now laid out properly

- Added a badge for Star subscribers, seems to work, but I do not have Star to test it, so hope the API returns the response I think it does

- Downloading moved to the background

- A "servers are temporarily dead" banner, so you know what is not working

<h1>Fixed </h1>

- Smooth transitions between tracks, turns out when I added it, it did not work

- Removed a whole one visual bug😎

- Removed vibe search (it does not work anyway)

## [0.3.6] Getting ready for the beta test

> I will put it into beta test today, I swear

<h1>Added </h1>

- Search by category: tracks / artists / playlists / albums - chips under the search bar

- Lists now load more on their own as you scroll down, threw out the "load more" button

- Liking an artist. Tap the heart on their page - and all their tracks are in the "Me" tab as a playlist

- Albums now differ from playlists - Album / EP / Single / Compilation labels everywhere they show up

- The "download all" button turns into a checkmark when everything is already downloaded - you can see the whole album/playlist is yours

- A playlist download can be canceled with a second tap, and tapping the notification opens the playlist being downloaded

- Reworked the mini player: swiping left/right switches the track with a preview (you can see what will play next), and on the right there is only like and pause/play - more room for the title

- In the notification, like moved to the right, and instead of dislike there is now shuffle (in sync with the player)

- AI wrote the language files again, because I did not learn 8 languages😎
 
<h1>Fixed </h1>

- A playlist/album did not show tracks (parsing crashed on a "raw" track from the backend) - fixed it

- Playlists loaded slowly - cached them, now they open instantly

- History: now all repeats of a track are removed, not only consecutive ones

- The player slid to the middle if a track had no cover - added a placeholder

- Creating a playlist: the window closes right on tap, so that backend lag does not spawn duplicates

- You could not open a playlist from an artist page in search - fixed it

- Search got more responsive (150ms instead of 300)

- Removed swipe-to-like from Wave, it got in the way of scrolling

## [0.3.5] A couple of small tweaks

> I want to do a pack of big updates, but I need to close the app's [gestalt](https://soundcloud.com/oxxxymiron/kto-ubil-marka)

<h1>Added </h1>

- Added CI for the repo

- Updated the README (AI updated the README👹, in structure)

- Now there are reels even here. Double tap a track in Wave - like and back

- Improved the playlist mechanics. Now you can like them and they will sit in the "Me" tab. They can be downloaded and deleted.

- Improved the UI a tiny bit

- The tile is now NowPlaying too (well, kind of a joke, did you not get it, huh?)

- A "downloaded" icon next to tracks in lists and playlists

- While a cover loads - a neutral placeholder instead of emptiness

- There are now labels on tracks that the backend does not load

<h1>Fixed </h1>

- Scrolling text in the mini player for long titles

- Duplicates in History

- A removed like "came back" in the liked list - the cache again

- The fade at the edges of the title in the player was there even on short ones - now only when it really does not fit

- The "Artist/Playlist" note in search overlapped long titles, so I removed it, no feature - no bug

- The name flashed as "SoundCloud" for a second - cached it, it loads instantly

- Updated the language files in all 8 languages with AI😎 (and the Russian one I just checked for mistakes, so 8, not 9)

## [0.3.4] Spring cleaning

bug fixes, not much new - but the old stuff works now

<h1>Fixed </h1>


- Duplicate covers EVERYWHERE. The cache was to blame: all images went through the proxy with the same URL, and the cache honestly handed the first one it got to everyone. Now each one has its own key

- The same issue could potentially break the waveform - waves could get mixed up between tracks, fixed too

- Player icons out of sync (one in the mini player, another in the expanded one) - all player state now lives in one place, the bug physically has nowhere to appear

- Likes/dislikes: if the server rejected the request, the heart still stayed filled. Now errors are not swallowed and the state rolls back

- Creating playlists: if the backend answers 502 (its favorite), the app no longer panics, it waits and checks - the playlist usually gets created anyway, just slowly

- Tile mosaic: because of a negative hashCode almost all tiles had the same proportion. Now the mosaic is a mosaic again

<h1>Improved </h1>

- The rename and delete playlist buttons are hidden in the three-dot menu, the header got cleaner

- Toasts like a human: "Playlist created", "Added", and on error - the real text from the server, not the eternal "no network"

- The playlist list refreshes bypassing the cache - a created playlist shows up right away, a deleted one disappears right away

<h1>And also... </h1>

- R8 is on: the release APK is smaller, the code is obfuscated

- App data backup is disabled - session_id no longer leaks into cloud backups

- Closed a path traversal in downloads (a crooked track name could write files where it should not)

- Removed races during parallel track downloads

- Filled in the translations in all 8 languages with AI😎

## [0.3.3] Low-budget Spotify

the MVP project became a minimalistic app 
overall, what got finished

<h1>Added </h1>

- Likes / Dislikes. The backend uses them to build track suggestions for you

- Basic playlist features, with one BUT - the backend should give me playlist previews, but sometimes it does not. Later I will ask NN to finish it

- A cache system, half-assed, but something got done, so the lags of the main API should not matter much

- Same goes for tracks, set the queue to preload, so it should not hang much if you listen in the queue

- What I did NOT nail down: there is a "Vibe" search, but its endpoint is no work both in the official client and in mine, left it in, maybe nn fixes it later

- Added 8 languages with AI😎 (EN/KO/TR/CN/BE/DE/ES/FR/), it detects the system one and sets it on its own

- Added a dark/light theme, there is also an option that sets the system one

- Offline mode, turns on in the settings or when you have no notification

- A notification with the current track, you can switch back and forth, like and dislike

- Downloading tracks for offline mode

- Icons, AI made them as a temporary solution, later I will make a full version and take references from lolyashka so it looks similar

- Logging out of the account

<h1>Improved </h1>

- There is now an immersive mode, you can turn it on in the settings

- There is a progress bar without the expanded player

- Replaced the "History" tab with "Me", now it has playlists, history, downloads and the settings in the top right corner

- Added shuffle and repeat for tracks (btw the queue loads on these triggers, if you spam them, the backend API may smack you, idk what security measures they have there)

- Added a download button

- Gesture handling

- Improved the Wave logic, I will not describe it, but trust me it is better than it was in 0.1.0

- Animations, but AI improved them, I have paws, there will be bugs, you are welcome [here](https://github.com/okeydw/SoundCloud-Android/issues/new/choose)

- History, before there were duplicates in it

- Added the waveform, but if the backend did not send it, I put in the old default
