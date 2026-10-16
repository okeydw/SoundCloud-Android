<p align="center">
<a href="import.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="import.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="import.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="import.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="import.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="import.fr.md"><img src="https://img.shields.io/badge/FR-0048FF?style=flat-square" alt="Français"/></a>
<a href="import.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="import.tr.md"><img src="https://img.shields.io/badge/TR-777777?style=flat-square" alt="Türkçe"/></a>
<a href="import.ko.md"><img src="https://img.shields.io/badge/KO-777777?style=flat-square" alt="한국어"/></a>
</p>

# Importer de la musique

L'import se trouve dans **Paramètres -> Importer (bêta)**.
Comment ça marche :

- **Spotify et Yandex Music.** L'application ne copie pas les originaux, elle cherche les mêmes titres sur SoundCloud par titre et artiste.
- **YouTube.** Tu peux prendre le titre directement sur YouTube ou en trouver un similaire sur SoundCloud.
- Les titres et playlists pris directement sur YouTube ne seront pas synchronisés avec SoundCloud
- Si une playlist contient plus de 500 titres, des playlists séparées seront créées
- Les jetons et les sessions sont stockés uniquement sur le téléphone.

## Par lien

1. Copie le lien d'un titre, d'une playlist ou d'un album.
2. Colle-le dans le champ "Importer par lien".
3. Choisis où l'ajouter et appuie sur "Importer".

Les liens publics fonctionnent sans connexion. Pour les playlists privées et les likes, connecte un compte ci-dessous.
> Spotify !
> Sans connexion, seuls les 100 premiers titres d'une playlist sont lus.


## Spotify

1. Ouvre le [tableau de bord développeur Spotify](https://developer.spotify.com/dashboard) et connecte-toi avec ton compte.
2. Appuie sur **Create app**. Le nom et la description peuvent être quelconques.
3. Dans **Redirect URIs**, ajoute les versions de l'application. Tu peux ajouter les deux, mais une par une :
   - Lite : `com.scd.android://spotify-auth`
   - Full : `com.scd.android.full://spotify-auth`
4. Dans **Which API/SDKs are you planning to use?**, coche **Web API** et enregistre.
5. Ouvre **Settings -> User Management** et ajoute l'e-mail du compte Spotify depuis lequel tu vas importer.
6. Copie le **Client ID** (32 lettres et chiffres) et colle-le dans l'application : **Import -> Spotify -> Client ID**.
7. Le bouton devient "Autoriser l'application". Appuie dessus, puis confirme dans la fenêtre d'autorisation.

Si tout est correct, ton nom et ta photo de profil Spotify s'affichent, et tu peux importer tes likes et tes playlists privées.

Important :

- Une application Spotify en mode développement ne fonctionne que pour les comptes listés dans User Management. Le propriétaire de l'application dans le tableau de bord doit avoir Spotify Premium, sinon Spotify répond 403.
- Si Spotify refuse le Client ID, une erreur s'affiche sous le champ : vérifie l'ID et la Redirect URI dans le tableau de bord.

Si le champ est vide, le bouton "Se connecter à Spotify" ouvre une fenêtre de connexion intégrée. C'est plus simple, mais ça ne marche pas toujours.

## YouTube

1. Ouvre **Import -> YouTube (Music)** et appuie sur **Ajouter un compte**.
2. Connecte-toi à Google dans la fenêtre qui s'ouvre. Elle se ferme toute seule, puis le nom et l'avatar du compte apparaissent.
3. **Importer les j'aime** cherche tes vidéos aimées sur SoundCloud et les rassemble dans une playlist.

Un titre ou une playlist peut être importé par lien. Il y a deux modes :

- **Prendre sur YouTube.** Le son est téléchargé depuis YouTube, la playlist reste uniquement sur le téléphone.
- **Trouver des titres similaires sur SoundCloud.** Cherche les mêmes titres sur SoundCloud.

Si Google indique que le navigateur n'est pas sécurisé, utilise un VPN.

## Yandex Music

1. Ouvre **Import -> Yandex Music** et appuie sur **Se connecter avec Yandex**.
2. Un navigateur s'ouvre. Choisis le compte voulu.
3. Copie l'adresse de la page vers laquelle tu es redirigé, reviens dans l'application et appuie sur "Coller le lien".
4. **Importer les j'aime** transfère tes titres préférés.

Il existe aussi une connexion automatique, tu peux l'essayer.

Pour changer de compte, appuie sur **Oublier le compte** et reconnecte-toi.
