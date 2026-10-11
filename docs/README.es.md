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
<img src="icon.png" width="160px" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Versión de escritorio</a>]</b><br>
<b>Cliente no oficial de SoundCloud para Android</b><br>
Sin anuncios · Sin captcha · Sin censura
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
<img src="https://img.shields.io/badge/Descargar_APK-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Descargar APK"/>
</a>
</p>

---

## ¿Qué es?

**SoundCloud Android** es un cliente nativo de SoundCloud para el móvil. Usa el mismo backend que [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop), así que todo el catálogo está disponible directamente.

Escrito en **Kotlin + Jetpack Compose + Media3**, funciona de forma nativa y consume muy pocos recursos.

El cliente sale en dos versiones con las mismas funciones, solo cambia el aspecto:

> `SoundCloud Full` - diseño Glass al estilo de la versión de escritorio: cristal, auras, animaciones, pantallas y reproductor propios.

> `SoundCloud Lite` - la interfaz sencilla de siempre, para quien valora el minimalismo y los móviles modestos.

### Ya se está planeando iOS, quizá llegue pronto

---

## Descargar

Ve a la [página de versiones](https://github.com/okeydw/SoundCloud-Android/releases/latest) y descarga el `.apk` que necesites:

| Archivo | Versión |
| --- | --- |
| `SoundCloud-Full-vX.Y.Z.apk` | SoundCloud, diseño Glass |
| `SoundCloud-Lite-vX.Y.Z.apk` | SoundCloud Lite, interfaz sencilla |

**Instalación:** abre el archivo descargado en el móvil y permite instalar desde orígenes desconocidos. Después las actualizaciones se instalan desde la propia app.

**Requisitos:** Android 8.0 (Oreo) o posterior.

---

## Cómo iniciar sesión

Sin iniciar sesión la app no pasa de la pantalla de acceso (salvo el modo sin conexión con pistas ya descargadas). Opciones:

1. **QR desde el escritorio (recomendado).** En [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) abre "Transferir sesión a otro dispositivo", en el móvil toca "Abrir cámara" y escanea el código con el escáner integrado.
2. **QR desde el móvil.** En la pantalla de acceso toca "Mostrar QR para iniciar sesión" y escanéalo donde ya tengas la sesión iniciada.
3. **En el navegador.** El botón "Entrar con el navegador" abre el inicio de sesión normal de SoundCloud. Esa sesión puede caerse si luego inicias sesión en otro sitio.

---

## Capturas

> El fondo, el color del texto y el tema se pueden personalizar: tu propia imagen de fondo, paleta RGB y temas listos.

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

## Funciones

Todo lo de abajo está en ambas versiones salvo que se indique lo contrario.

### Música

- **Ola y recomendaciones** - feed personal, estanterías y selecciones, radio a partir de una pista o un artista
- **Búsqueda** - pistas, artistas, listas, álbumes y búsqueda por vibra (describe un estado de ánimo con palabras, beta)
- **Reproductor** - reproducción en segundo plano, control desde la notificación, waveform, aleatorio/repetir, gestos, letras (sincronizadas si las hay)
- **Transiciones suaves** - entre pistas, de 1 a 30 segundos
- **Ecualizador** - preajustes del sistema y propios, los estándar que sobran se pueden ocultar
- **Salida de audio** - altavoz, auriculares con cable, Bluetooth o USB a elegir
- **Cola** - "reproducir a continuación" desde el menú de cualquier pista

### Biblioteca

- **Me gusta y no me gusta** - las pistas con no me gusta se saltan, tienen su propia lista
- **Listas** - tus listas locales sincronizadas con SoundCloud, públicas o privadas, portadas propias, el orden se cambia arrastrando y se guarda
- **Historial y estadísticas** - tiempo de escucha, pistas y artistas más escuchados, gráficos por día y por hora
- **Fichas de artistas** - biografía, redes sociales, álbumes y artistas similares
- **Perfiles** - seguir y dejar de seguir, enlaces del perfil de SoundCloud
- **Bloqueo de artistas** - como un no me gusta, pero para todo el artista: no aparece en la ola, la radio ni las recomendaciones, la lista se comparte con Desktop

### Sin conexión y descargas

- Descarga de pistas y listas completas, modo sin conexión con caché
- Límite y tiempo de conservación de la caché, mejora de calidad de lo ya descargado (para Star)
- Descargas y listas compartidas entre Full y Lite si tienes las dos (beta, funciona de forma selectiva)

### Importación (beta)

- **YouTube / YouTube Music** - una pista o lista por enlace, el audio y la portada se toman directamente de YouTube, se puede crear una lista al momento (en Rusia hace falta VPN)
- **Yandex Music** - me gusta, listas y álbumes con token o iniciando sesión en Yandex dentro de la app
- **Spotify** - listas, álbumes y pistas por enlace

Para Yandex Music y Spotify la app **busca pistas parecidas en SoundCloud, no transfiere los originales**. Lo encontrado se puede poner a descargar al momento.
(pero vence a la censura, fck censoor)
### SoundCloud Auto (beta)

- La música arranca sola al conectar el móvil al coche por Bluetooth
- Eliges la fuente (me gusta, ola, descargas, lista), los dispositivos y el aleatorio
- Un preajuste de ecualizador para el coche, al desconectar vuelve el anterior

### Red

- Varios hosts y nodos relay, los nodos se encuentran solos
- Elección manual del nodo con ping y prueba de 64 KB: se ve si el proveedor corta la conexión tras 13 KB, y el modo automático no elegirá ese nodo
- El aviso "sin red/servidor caído" solo sale tras 15 segundos sin conexión

### Apariencia

- 9 idiomas
- Temas, fondo propio (imagen con desenfoque y oscurecido), colores de acento y texto, paleta RGB, exportar e importar temas
- Icono de la app: violeta, azul o naranja (una imagen propia se añade como acceso directo aparte, limitación de Android)
- **Solo Full:** interfaz Glass, aura de perfil para Star (sincronizada con Desktop), reproductor inmersivo, apertura suave de los ajustes

### Otros

- Actualización automática: tocas el aviso de nueva versión, se descarga el APK y se abre el instalador
- Inicio de sesión por QR (con la cámara dentro de la app), por enlace o en el navegador
- Tu Discord vinculado se ve en la cuenta
- Soporte en "Acerca de": issue en GitHub o ticket en Discord, las métricas del dispositivo se rellenan solas

---

## Comentarios

¿Has encontrado un fallo o tienes una idea? Lo más fácil es "Ajustes -> Acerca de -> Soporte": se abre un issue en GitHub o un ticket en Discord con las métricas. O [abre un issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) a mano y adjunta los logs: "Ajustes -> Logs -> Compartir".

---

## Stack

| Capa | Tecnologías |
| --- | --- |
| Lenguaje | Kotlin |
| UI | Jetpack Compose, Material 3, Haze (cristal en Full) |
| Audio | Media3 (ExoPlayer + MediaSession), servicio en segundo plano con notificación |
| Red | OkHttp (+ caché en disco), kotlinx.serialization |
| Imágenes | Coil (+ Palette para teñir según la portada) |
| Cámara | CameraX + ML Kit (escáner QR en Full), ZXing |
| Almacenamiento | SharedPreferences (ajustes, sesión), índices JSON de descargas y listas |

El mismo backend que [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop): la app funciona como un cliente más.

**Compatibilidad:** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

La lista completa de cambios está en [CHANGELOG.md](../CHANGELOG.md).

---

## Para desarrolladores

**Compilación:** JDK 17, Android SDK 35.

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK en app/build/outputs/apk/<flavor>/debug/
./gradlew testLiteDebugUnitTest                 # tests unitarios
./gradlew lintLiteDebug                         # lint (baseline: ./gradlew updateLintBaseline)
```

**Código:**

| Carpeta | Contenido |
| --- | --- |
| `app/src/main` | lógica: API, reproductor, descargas, listas, importación |
| `app/src/shared` | pantallas y diálogos comunes (ajustes, etc.) |
| `app/src/lite` | interfaz de Lite |
| `app/src/full` | interfaz Glass de Full |

**Versión:** un tag `vX.Y.Z` (debe coincidir con `versionName`) compila y firma ambas versiones con una sola clave y las publica con el texto de `CHANGELOG.md`.

**Backend:**

| Uso | Hosts |
| --- | --- |
| API | `api.scnative.space`, `api-star.scnative.space` (Star) |
| Streaming | `stream.scnative.space`, `stream-star.scnative.space` |
| Almacenamiento | `storage.scnative.space`, `storage-star.scnative.space` |
| Imágenes | `images.scnative.space` |
| Nodos relay | `*.rN.relay.scnative.space`, lista: `health.scnative.space/topology` |

Protocolo de inicio de sesión por QR: [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) en el repositorio de escritorio.

---

## Datos y confianza

- La app funciona a través del backend `*.scnative.space` (el mismo que el escritorio). **La sesión y los tokens de SoundCloud se guardan en ese backend**, en el móvil solo queda el identificador de sesión.
- "Cerrar sesión" borra la sesión en el backend (`POST /auth/logout`) y todos los datos del móvil.
- La vía directa de respaldo a SoundCloud (se puede desactivar en ajustes) va a soundcloud.com sin tu sesión.
- El token de Yandex Music para importar se guarda solo en el móvil. La app nunca ve tu contraseña de Yandex: el acceso se hace en la propia página de Yandex.

---

## Licencia

MIT. Detalles en el archivo [LICENSE](../LICENSE).

_SoundCloud es una marca de SoundCloud Ltd. Esta app no está afiliada a SoundCloud._

---

<p align="center">
<code>soundcloud android</code> · <code>soundcloud apk</code> · <code>cliente soundcloud</code> · <code>soundcloud para android</code> · <code>soundcloud sin anuncios</code> · <code>soundcloud bloqueado</code> · <code>soundcloud no funciona</code> · <code>soundcloud android app</code> · <code>soundcloud player</code> · <code>soundcloud apk download</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code> · <code>reproductor de música soundcloud</code>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/Descargar_SoundCloud_Android-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Descargar SoundCloud Android"/>
</a>
</p>
