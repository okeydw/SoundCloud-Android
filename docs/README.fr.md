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
<img src="icon.png" width="160px" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Version Desktop</a>]</b><br>
<b>Client SoundCloud non officiel pour Android</b><br>
Sans publicité · Sans captcha · Sans censure
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
<img src="https://img.shields.io/badge/Télécharger_l'APK-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Télécharger l'APK"/>
</a>
</p>

---

## Qu'est-ce que c'est ?

**SoundCloud Android** est un client SoundCloud natif pour téléphone. Il utilise le même backend que [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop), donc tout le catalogue est accessible directement.

Écrit en **Kotlin + Jetpack Compose + Media3**, il tourne nativement et consomme un minimum de ressources.

Le client existe en deux versions avec les mêmes fonctions, seule l'apparence change :

> `SoundCloud Full` - design Glass dans le style de la version desktop : verre, auras, animations, ses propres écrans et son lecteur.

> `SoundCloud Lite` - l'interface simple habituelle, pour ceux qui aiment le minimalisme et les téléphones modestes.

### Une version iOS est en préparation, elle arrivera peut-être bientôt

---

## Télécharger

Va sur la [page des versions](https://github.com/okeydw/SoundCloud-Android/releases/latest) et télécharge le `.apk` qu'il te faut :

| Fichier | Version |
| --- | --- |
| `SoundCloud-Full-vX.Y.Z.apk` | SoundCloud, design Glass |
| `SoundCloud-Lite-vX.Y.Z.apk` | SoundCloud Lite, interface simple |

**Installation:** ouvre le fichier téléchargé sur le téléphone et autorise l'installation depuis des sources inconnues. Ensuite, les mises à jour s'installent depuis l'appli.

**Configuration requise:** Android 8.0 (Oreo) ou plus récent.

---

## Comment se connecter

Sans connexion, l'appli ne va pas plus loin que l'écran de connexion (sauf en mode hors ligne avec des titres déjà téléchargés). Options :

1. **QR depuis le desktop (recommandé).** Dans [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop), ouvre "Transférer la session vers un autre appareil", puis sur le téléphone touche "Ouvrir la caméra" et scanne le code avec le scanner intégré.
2. **QR depuis le téléphone.** Sur l'écran de connexion, touche "Afficher le QR de connexion" et scanne-le là où tu es déjà connecté.
3. **Dans le navigateur.** Le bouton "Se connecter dans le navigateur" ouvre la connexion SoundCloud habituelle. Cette session peut sauter si tu te connectes ailleurs ensuite.

---

## Captures d'écran

> Fond, couleur du texte et thème sont personnalisables : ta propre image de fond, palette RGB et thèmes prêts à l'emploi.

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

## Fonctionnalités

Tout ce qui suit est dans les deux versions, sauf mention contraire.

### Musique

- **Vague et recommandations** - flux personnel, étagères et sélections, radio à partir d'un titre ou d'un artiste
- **Recherche** - titres, artistes, playlists, albums et recherche par ambiance (décris une humeur avec des mots, bêta)
- **Lecteur** - lecture en arrière-plan, contrôles dans la notification, waveform, aléatoire/répétition, gestes, paroles (synchronisées si disponibles)
- **Fondu enchaîné** - entre les titres, de 1 à 30 secondes
- **Égaliseur** - préréglages système et personnalisés, les standards inutiles peuvent être masqués
- **Sortie audio** - haut-parleur, écouteurs filaires, Bluetooth ou USB au choix
- **File d'attente** - "lire ensuite" depuis le menu de n'importe quel titre

### Bibliothèque

- **J'aime et je n'aime pas** - les titres non aimés sont sautés, ils ont leur propre liste
- **Playlists** - tes playlists locales synchronisées avec SoundCloud, publiques ou privées, couvertures personnalisées, l'ordre se change par glisser-déposer et reste enregistré
- **Historique et statistiques** - temps d'écoute, top titres et artistes, graphiques par jour et par heure
- **Fiches d'artistes** - biographie, réseaux sociaux, albums et artistes similaires
- **Profils** - s'abonner et se désabonner, liens du profil SoundCloud
- **Blocage d'artistes** - comme un "je n'aime pas", mais pour tout l'artiste : il n'apparaît plus dans la vague, la radio et les recommandations, la liste est partagée avec Desktop

### Hors ligne et téléchargements

- Téléchargement de titres et de playlists entières, mode hors ligne avec cache
- Limite et durée de conservation du cache, amélioration de la qualité des titres déjà téléchargés (pour Star)
- Téléchargements et playlists partagés entre Full et Lite si les deux sont installées (bêta, fonctionne de façon sélective)

### Import (bêta)

- **YouTube / YouTube Music** - un titre ou une playlist par lien, l'audio et la couverture viennent directement de YouTube, une playlist peut être créée tout de suite (un VPN est nécessaire en Russie)
- **Yandex Music** - j'aime, playlists et albums via un jeton ou en se connectant à Yandex directement dans l'appli
- **Spotify** - playlists, albums et titres par lien

Pour Yandex Music et Spotify, l'appli **cherche des titres similaires sur SoundCloud, elle ne transfère pas les originaux**. Ce qui est trouvé peut être téléchargé tout de suite.
(mais ça contourne la censure, fck censoor)
### SoundCloud Auto (bêta)

- La musique démarre toute seule quand le téléphone se connecte à la voiture en Bluetooth
- Choix de la source (j'aime, vague, téléchargements, playlist), des appareils et de l'aléatoire
- Un préréglage d'égaliseur dédié à la voiture, le précédent revient après la déconnexion

### Réseau

- Plusieurs hôtes et nœuds relais, les nœuds sont trouvés automatiquement
- Choix manuel du nœud avec ping et test de 64 Ko : on voit si le fournisseur coupe les connexions après 13 Ko, et le mode auto ne choisira pas ce nœud
- Le bandeau "pas de réseau/serveur indisponible" n'apparaît qu'après 15 secondes sans connexion

### Apparence

- 9 langues
- Thèmes, fond personnalisé (image avec flou et assombrissement), couleurs d'accent et de texte, palette RGB, export et import de thèmes
- Icône de l'appli : violette, bleue ou orange (une image perso est ajoutée comme raccourci séparé, limitation d'Android)
- **Full uniquement:** interface Glass, aura de profil pour Star (synchronisée avec Desktop), lecteur immersif, ouverture fluide des réglages

### Divers

- Mise à jour automatique : touche le bandeau de nouvelle version, l'APK se télécharge et l'installateur s'ouvre
- Connexion par QR (avec la caméra directement dans l'appli), par lien ou dans le navigateur
- Ton Discord lié s'affiche dans le compte
- Assistance dans "À propos" : issue GitHub ou ticket Discord, les métriques de l'appareil sont remplies automatiquement

---

## Retours

Un bug ou une idée ? Le plus simple : "Réglages -> À propos -> Assistance", ça ouvre une issue GitHub ou un ticket Discord avec les métriques. Ou [ouvre une issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) à la main et joins les logs : "Réglages -> Logs -> Partager".

---

## Stack

| Couche | Technologies |
| --- | --- |
| Langage | Kotlin |
| UI | Jetpack Compose, Material 3, Haze (verre dans Full) |
| Audio | Media3 (ExoPlayer + MediaSession), service en arrière-plan avec notification |
| Réseau | OkHttp (+ cache disque), kotlinx.serialization |
| Images | Coil (+ Palette pour teinter selon la couverture) |
| Caméra | CameraX + ML Kit (scanner QR dans Full), ZXing |
| Stockage | SharedPreferences (réglages, session), index JSON des téléchargements et des playlists |

Même backend que [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) : l'appli est simplement un client de plus.

**Compatibilité:** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

La liste complète des changements est dans [CHANGELOG.md](../CHANGELOG.md).

---

## Pour les développeurs

**Compilation:** JDK 17, Android SDK 35.

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK dans app/build/outputs/apk/<flavor>/debug/
./gradlew testLiteDebugUnitTest                 # tests unitaires
./gradlew lintLiteDebug                         # lint (baseline : ./gradlew updateLintBaseline)
```

**Sources:**

| Dossier | Contenu |
| --- | --- |
| `app/src/main` | logique : API, lecteur, téléchargements, playlists, import |
| `app/src/shared` | écrans et dialogues communs (réglages, etc.) |
| `app/src/lite` | interface Lite |
| `app/src/full` | interface Glass de Full |

**Version:** un tag `vX.Y.Z` (doit correspondre à `versionName`) compile et signe les deux versions avec une seule clé et les publie avec le texte de `CHANGELOG.md`.

**Backend:**

| Rôle | Hôtes |
| --- | --- |
| API | `api.scnative.space`, `api-star.scnative.space` (Star) |
| Streaming | `stream.scnative.space`, `stream-star.scnative.space` |
| Stockage | `storage.scnative.space`, `storage-star.scnative.space` |
| Images | `images.scnative.space` |
| Nœuds relais | `*.rN.relay.scnative.space`, liste: `health.scnative.space/topology` |

Protocole de connexion par QR : [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) dans le dépôt desktop.

---

## Données et confiance

- L'appli passe par le backend `*.scnative.space` (le même que le desktop). **La session et les jetons SoundCloud sont stockés sur ce backend**, le téléphone ne garde que l'identifiant de session.
- "Se déconnecter" supprime la session sur le backend (`POST /auth/logout`) et toutes les données du téléphone.
- Le chemin de secours direct vers SoundCloud (désactivable dans les réglages) va sur soundcloud.com sans ta session.
- Le jeton Yandex Music pour l'import reste uniquement sur le téléphone. L'appli ne voit jamais ton mot de passe Yandex : la connexion se fait sur la page de Yandex elle-même.

---

## Licence

MIT. Détails dans le fichier [LICENSE](../LICENSE).

_SoundCloud est une marque de SoundCloud Ltd. Cette appli n'est pas affiliée à SoundCloud._

---

<p align="center">
<code>soundcloud android</code> · <code>soundcloud apk</code> · <code>client soundcloud</code> · <code>soundcloud pour android</code> · <code>soundcloud sans pub</code> · <code>soundcloud bloqué</code> · <code>soundcloud ne fonctionne pas</code> · <code>soundcloud android app</code> · <code>soundcloud player</code> · <code>soundcloud apk download</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code> · <code>lecteur de musique soundcloud</code>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/Télécharger_SoundCloud_Android-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Télécharger SoundCloud Android"/>
</a>
</p>
