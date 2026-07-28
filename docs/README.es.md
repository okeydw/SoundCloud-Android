<p align="center">
<a href="../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/ES-0048FF?style=flat-square" alt="Español"/></a>
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
<b>Cliente no oficial de SoundCloud para Android</b><br>
Sin anuncios · Sin captcha · Sin censura · Funciona en Rusia
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
<img src="https://img.shields.io/badge/Descargar_APK-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Descargar APK"/>
</a>
</p>

---

> [!WARNING]
> ⚠️ **Debido a los sistemas de seguridad de la API principal y de SoundCloud, iniciar sesión puede cerrarte otra sesión.** Si tienes sesión iniciada en otro dispositivo (por ejemplo, el cliente de escritorio), iniciar sesión aquí puede «desvincular» esa sesión, y viceversa. Es una limitación del backend, no un fallo de la app: simplemente vuelve a iniciar sesión donde lo necesites.

---

## ¿Qué es?

**SoundCloud Android** es un cliente nativo de SoundCloud para el teléfono. Usa el mismo backend que [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop), así que todo el catálogo está disponible directamente.

Escrito en **Kotlin + Jetpack Compose + Media3**, funciona de forma nativa y consume pocos recursos.

Por ahora es una versión ligera sin adornos; puliré el diseño cuando esté seguro de que no hay bugs y las funciones estén completas, y luego lo dividiré en dos versiones:

> `Full` — muchos efectos bonitos y un diseño parecido a la versión de escritorio.

> `Lite` — un diseño simplificado para quien valora el minimalismo y los teléfonos modestos.

### ¡No habrá versión para iOS!

---

## Funciones

- **Búsqueda y Wave** — busca pistas/artistas/listas y un feed personal de recomendaciones
- **Reproductor** — reproducción en segundo plano, control desde la notificación, waveform, aleatorio/repetición, gestos
- **Biblioteca** — me gusta/no me gusta, listas, historial, perfiles de artistas
- **Sin conexión** — descarga de pistas y modo offline con caché
- **Apariencia** — tema claro/oscuro, modo inmersivo y 9 idiomas

---

## Descargar

Ve a la [página de releases](https://github.com/okeydw/SoundCloud-Android/releases/latest) y descarga el `.apk`.

**Instalación:** abre el archivo descargado en tu teléfono y permite la instalación desde orígenes desconocidos.

**Requisitos:** Android 8.0 (Oreo) o superior.

---

## Capturas
`antiguas, pero las actualizaré en 4.0.0 — por ahora son de la 0.3.5`

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

## Comentarios

¿Encontraste un bug o tienes una idea? — [abre un issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose).

---

## Stack

| Capa | Tecnología |
| --- | --- |
| Lenguaje | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Audio | Media3 (ExoPlayer + MediaSession), servicio en primer plano con notificación |
| Red | OkHttp (+ caché en disco), kotlinx.serialization |
| Imágenes | Coil (+ Palette para teñir según la portada) |
| Almacenamiento | SharedPreferences (ajustes, sesión), índice JSON de pistas descargadas |

El mismo backend que [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop): la app es otro cliente para él.

**Compatibilidad:** `minSdk 26` (Android 8.0) … `targetSdk 35`, `compileSdk 35`.

---

## Estructura

```
app/src/main/java/com/scd/android/
  App.kt              - Application: init de sesión/caché/ajustes, cargador de portadas Coil vía proxy (clave única por imagen)
  MainActivity.kt     - punto de entrada, navegación (Búsqueda / Wave / Yo), mosaico de tiles, banner «sin internet», filtrado de pistas no disponibles, deep links desde notificaciones
  Api.kt              - cliente API: búsqueda, streaming, portadas, me gusta/no me gusta de pistas y listas, historial, modelos de datos; caché HTTP multinivel (datos fríos por mucho tiempo)
  NetMonitor.kt       - comprobación de red (Wi-Fi / móvil) + respaldo offline desde caché
  Prefs.kt            - ajustes (tema, idioma, offline, inmersivo) + nombre de usuario en caché
  LocaleHelper.kt     - sustitución de la configuración regional de la app (cambio de idioma)

  PlaybackService.kt  - MediaSessionService: reproductor en segundo plano, notificación, me gusta/aleatorio en ella, abrir el reproductor al tocar
  Player.kt           - mini reproductor y reproductor a pantalla completa (waveform, gestos, aleatorio/repetición, inmersivo, marquee, añadir a lista)
  NowPlaying.kt       - estado global del reproductor + eventos de navegación/actualización de listas (PlaylistEvents, NavEvents)

  WaveScreen.kt       - Wave: feed con pager, swipes y doble toque = me gusta con animación, actualizar
  ArtistScreen.kt     - perfil de artista: pistas y listas (incl. offline desde descargas)
  PlaylistScreen.kt   - pantalla de lista: lista de pistas, dar me gusta a la lista, descargar todo / cancelar
  LibraryScreen.kt    - pestaña «Yo»: saludo, me gusta, descargas, listas propias y con me gusta, historial + pantalla de ajustes

  Likes.kt / Dislikes.kt - estado de me gusta/no me gusta de pistas (mutuamente excluyentes, sincronizados con el backend)
  LikedPlaylists.kt   - estado de listas con me gusta (sincronizado con el backend)
  Downloads.kt        - descarga de pistas a una carpeta privada, índice, notificación de progreso, cancelación y deep link
  Genres.kt           - lista de géneros para los tiles en búsqueda vacía

app/src/main/res/
  drawable/           - iconos vectoriales (estilo Lucide) + logo
  values/, values-*/  - cadenas en 9 idiomas + values-night (tema oscuro)

proguard-rules.pro    - reglas R8 para la build de release (serialization / OkHttp)
.github/workflows/    - CI: lint, tests, build de APK debug
```

Registro de cambios completo en [CHANGELOG.md](../CHANGELOG.md).

---

## Licencia

MIT. Detalles en el archivo [LICENSE](../LICENSE).

_SoundCloud es una marca de SoundCloud Ltd. Esta app no está afiliada con SoundCloud._
