<p align="center">
<a href="../../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
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
<img src="../icon.png" width="160px" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Version Desktop</a>] · [<a href="https://github.com/okeydw/SoundCloud-iOS">Version iOS</a>]</b><br>
<b>Client SoundCloud non officiel pour Android</b><br>
Sans pub · Sans captcha · Sans censure · Disponible en Russie
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
<img src="https://img.shields.io/badge/Télécharger_APK-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"/>
</a>
</p>

---

## Qu'est-ce que c'est ?

**SoundCloud Android** est un client SoundCloud natif pour téléphone. Il est synchronisé avec [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop).

Écrit en **Kotlin + Jetpack Compose + Media3**, il tourne en natif et consomme un minimum de ressources.

Le client existe en deux versions avec les mêmes fonctions, seule l'apparence change :

> `SoundCloud Full` - design Glass dans le style de la version Desktop, pour ceux qui aiment ce qui brille et ont un téléphone puissant.

> `SoundCloud Lite` - l'interface simplifiée des premières versions, pour ceux qui aiment le minimalisme ou ont un téléphone modeste.

### [Version iOS (bêta)](https://github.com/okeydw/SoundCloud-iOS)

---

## Télécharger

Va sur la [page des versions](https://github.com/okeydw/SoundCloud-Android/releases/latest) et télécharge le `.apk` qu'il te faut :

| Fichier | Version |
| --- | --- |
| `SoundCloud-Full-vX.Y.Z.apk` | SoundCloud, design Glass |
| `SoundCloud-Lite-vX.Y.Z.apk` | SoundCloud Lite, interface simple |

**Installation :** ouvre le fichier téléchargé sur ton téléphone et autorise l'installation depuis des sources inconnues. Ensuite, les mises à jour s'installent depuis l'application elle-même.

**Configuration requise :** Android 8.0 (Oreo) ou plus récent.

---

## Se connecter

Vient ensuite la connexion, via SoundCloud ou avec un QR code depuis la version Desktop.

1. **QR depuis le Desktop (recommandé).** Dans [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop), ouvre Paramètres -> Compte -> Transférer la session vers un autre appareil, puis scanne le QR code.
2. **QR depuis le téléphone.** Sur l'écran de connexion, appuie sur "Afficher le QR de connexion" et scanne-le là où tu es déjà connecté.
3. **Via le navigateur.** Le bouton "Se connecter via le navigateur" ouvre la connexion SoundCloud habituelle. Cette session peut sauter si tu te connectes ailleurs ensuite.

---

## Captures d'écran

> Les couleurs du fond, de l'accent et du texte se changent avec une palette RVB. Tu peux aussi ajouter une image de fond et des effets.

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

## Fonctionnalités

### Musique

- **Vague et recommandations** - notre propre système de recommandations basé sur tes likes et ton historique d'écoute
- **Recherche** - titres, artistes, playlists, albums et recherche par ambiance (un réseau neuronal choisit les titres selon le contexte)
- **Lecteur** - lecture aléatoire, répétition, file d'attente, paroles, likes et dislikes
- **Égaliseur** - préréglages prêts à l'emploi et possibilité de créer le tien
- **Playlists** - tes propres playlists locales synchronisées avec SoundCloud, publiques ou privées, l'ordre des titres se change par glisser-déposer et reste enregistré
- **Historique et statistiques** - temps d'écoute, top titres et artistes, graphiques par jour et par heure
- **Profils** - s'abonner et se désabonner, liens du profil SoundCloud
- **SoundCloud Auto (bêta)** - scénarios de connexion aux appareils Bluetooth

### Mode hors ligne

- Téléchargement de titres et de playlists, mode hors ligne avec cache
- Réglage manuel de la limite du cache et du moment où il est vidé
- Téléchargements et playlists partagés entre Full et Lite si les deux sont installées (bêta, fonctionne partiellement)

### Import (bêta)

- **YouTube / YouTube Music** - likes, playlists et albums via un compte Google
- **Yandex Music** - likes, playlists et albums par jeton
- **Spotify** - likes, playlists et albums via un client_id

###### [En savoir plus](../import-lang/import.fr.md)

> Important !!! Nous cherchons des titres similaires, nous n'importons pas l'audio lui-même
> (mais cela permet de contourner la censure, fck censor)

---

## Retours
Communauté: [Discord](https://discord.gg/Au3ebtfYu3)

Tu as trouvé un bug ou tu as une idée ? Le plus simple est "Paramètres -> À propos du système -> Support" : ouvre une issue sur GitHub ou un ticket sur Discord, les infos de l'appareil sont ajoutées automatiquement. Tu peux aussi [ouvrir une issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) toi-même et joindre les logs : "Paramètres -> Logs -> Partager".

---

## Stack

| Couche | Technologies |
| --- | --- |
| Langage | Kotlin |
| UI | Jetpack Compose, Material 3, Haze (verre dans Full) |
| Audio | Media3 (ExoPlayer + MediaSession), service en arrière-plan avec notification |
| Réseau | OkHttp (+ cache disque), kotlinx.serialization |
| Images | Coil (+ Palette pour teinter selon la pochette) |
| Caméra | CameraX + ML Kit (scanner QR dans Full), ZXing |
| Stockage | SharedPreferences (paramètres, session), index JSON des téléchargements et des playlists |

Backend: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

État des serveurs [ici](https://status.soundcloud-desktop.fun/)

**Compatibilité :** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

La liste complète des changements se trouve dans [CHANGELOG.md](../../CHANGELOG.md).

---

## Pour les développeurs

**Compilation :** JDK 17, Android SDK 35.

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK dans app/build/outputs/apk/<flavor>/debug/
./gradlew testLiteDebugUnitTest                 # tests unitaires
./gradlew lintLiteDebug                         # lint (avec baseline : ./gradlew updateLintBaseline)
```

**Sources :**

| Dossier | Contenu |
| --- | --- |
| `app/src/main` | logique : API, lecteur, téléchargements, playlists, import |
| `app/src/shared` | écrans et dialogues communs (paramètres, etc.) |
| `app/src/lite` | interface Lite |
| `app/src/full` | interface Glass de Full |

**Publication :** un tag `vX.Y.Z` (doit correspondre à `versionName`) compile et signe les deux versions avec une même clé et les publie avec la description tirée de `CHANGELOG.md`.

**Backend :**

| Domaine | Rôle |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | audio |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | stockage des titres |
| `images.scnative.space` | pochettes |
| `pay.scnative.space` | paiement STAR |
| `health.scnative.space` | vérification de la connexion |
| `call-*.scnative.space` | « Réseau des utilisateurs » |
| `*.relay.scnative.space` | routes de secours |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | téléchargement direct du titre depuis SoundCloud |

Protocole de connexion par QR : [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) dans le dépôt Desktop.

---


## Licence

MIT. Détails dans le fichier [LICENSE](../../LICENSE).

_SoundCloud est une marque de SoundCloud Ltd. Cette application n'est pas affiliée à SoundCloud._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/Télécharger_SoundCloud_Android-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Télécharger SoundCloud Android"/>
</a>
</p>

<p align="center">
<code>soundcloud android</code> · <code>soundcloud apk</code> · <code>client soundcloud</code> · <code>soundcloud pour android</code> · <code>soundcloud sans pub</code> · <code>soundcloud bloqué</code> · <code>soundcloud ne fonctionne pas</code> · <code>soundcloud android app</code> · <code>soundcloud player</code> · <code>soundcloud apk download</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code> · <code>lecteur de musique soundcloud</code>
</p>
