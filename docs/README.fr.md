<p align="center">
<a href="../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/FR-0048FF?style=flat-square" alt="Français"/></a>
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
<b>Client SoundCloud non officiel pour Android</b><br>
Sans publicité · Sans captcha · Sans censure
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
<img src="https://img.shields.io/badge/Télécharger_l'APK-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Télécharger l'APK"/>
</a>
</p>

---

> [!WARNING]
> ⚠️ **À cause des systèmes de sécurité de l'API principale et de SoundCloud, la connexion peut vous déconnecter d'une autre session.** Si vous êtes connecté sur un autre appareil (par ex. le client desktop), vous connecter ici peut « dissocier » cette session, et inversement. C'est une limite du backend, pas un bug de l'application — reconnectez-vous simplement là où c'est nécessaire.

---

## Qu'est-ce que c'est ?

**SoundCloud Android** est un client SoundCloud natif pour téléphone. Il utilise le même backend que [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop), donc tout le catalogue est accessible directement.

Écrit en **Kotlin + Jetpack Compose + Media3**, il est natif et consomme un minimum de ressources.

Pour l'instant c'est une version légère sans fioritures ; je peaufinerai le design une fois sûr qu'il n'y a pas de bugs et que les fonctionnalités sont complètes, puis je le diviserai en deux versions :

> `Full` — plein de jolis effets et un design proche de la version desktop.

> `Lite` — un design simplifié pour ceux qui aiment le minimalisme et les téléphones modestes.

### Il n'y aura pas de portage iOS !

---

## Fonctionnalités

- **Recherche & Wave** — recherche de titres/artistes/playlists et un fil de recommandations personnalisé
- **Lecteur** — lecture en arrière-plan, contrôle depuis la notification, waveform, aléatoire/répétition, gestes
- **Bibliothèque** — likes/dislikes, playlists, historique, profils d'artistes
- **Hors ligne** — téléchargement de titres et mode hors ligne avec cache
- **Apparence** — thème clair/sombre, mode immersif et 9 langues

---

## Télécharger

Rendez-vous sur la [page des releases](https://github.com/okeydw/SoundCloud-Android/releases/latest) et récupérez le `.apk`.

**Installation :** ouvrez le fichier téléchargé sur votre téléphone et autorisez l'installation depuis des sources inconnues.

**Prérequis :** Android 8.0 (Oreo) ou plus récent.

---

## Captures d'écran

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

## Retour

Un bug ou une idée ? — [ouvrez une issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose).

---

## Stack

| Couche | Techno |
| --- | --- |
| Langage | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Audio | Media3 (ExoPlayer + MediaSession), service en avant-plan avec notification |
| Réseau | OkHttp (+ cache disque), kotlinx.serialization |
| Images | Coil (+ Palette pour la teinte selon la pochette) |
| Stockage | SharedPreferences (réglages, session), index JSON des titres téléchargés |

Même backend que [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) — l'application est un client de plus pour celui-ci.

**Compatibilité :** `minSdk 26` (Android 8.0) … `targetSdk 35`, `compileSdk 35`.

---

## Structure

```
app/src/main/java/com/scd/android/
  App.kt              - Application : init session/cache/réglages, chargeur de pochettes Coil via proxy (clé unique par image)
  MainActivity.kt     - point d'entrée, navigation (Recherche / Wave / Moi), mosaïque de tuiles, bannière « pas d'internet », filtrage des titres indisponibles, deep links depuis les notifications
  Api.kt              - client API : recherche, streaming, pochettes, likes/dislikes titres & playlists, historique, modèles de données ; cache HTTP multi-niveaux (données froides gardées longtemps)
  NetMonitor.kt       - vérification réseau (Wi-Fi / mobile) + repli hors ligne depuis le cache
  Prefs.kt            - réglages (thème, langue, hors ligne, immersif) + nom d'utilisateur en cache
  LocaleHelper.kt     - remplacement de la locale de l'app (changement de langue)

  PlaybackService.kt  - MediaSessionService : lecteur en arrière-plan, notification, like/aléatoire dedans, ouverture du lecteur au tap
  Player.kt           - mini-lecteur et lecteur plein écran (waveform, gestes, aléatoire/répétition, immersif, marquee, ajout à une playlist)
  NowPlaying.kt       - état global du lecteur + événements de navigation/rafraîchissement des playlists (PlaylistEvents, NavEvents)

  WaveScreen.kt       - Wave : fil en pager, swipes et double-tap = like avec animation, rafraîchissement
  ArtistScreen.kt     - profil d'artiste : titres et playlists (y compris hors ligne depuis les téléchargements)
  PlaylistScreen.kt   - écran de playlist : liste des titres, liker la playlist, tout télécharger / annuler
  LibraryScreen.kt    - onglet « Moi » : accueil, likes, téléchargements, playlists (perso & likées), historique + écran des réglages

  Likes.kt / Dislikes.kt - état des likes/dislikes de titres (mutuellement exclusifs, synchronisés avec le backend)
  LikedPlaylists.kt   - état des playlists likées (synchronisé avec le backend)
  Downloads.kt        - téléchargement des titres dans un dossier privé, index, notification de progression, annulation et deep link
  Genres.kt           - liste de genres pour les tuiles sur recherche vide

app/src/main/res/
  drawable/           - icônes vectorielles (style Lucide) + logo
  values/, values-*/  - chaînes en 9 langues + values-night (thème sombre)

proguard-rules.pro    - règles R8 pour le build release (serialization / OkHttp)
.github/workflows/    - CI : lint, tests, build APK debug
```

Journal des modifications complet dans [CHANGELOG.md](../CHANGELOG.md).

---

## Licence

MIT. Détails dans le fichier [LICENSE](../LICENSE).

_SoundCloud est une marque de SoundCloud Ltd. Cette application n'est pas affiliée à SoundCloud._
