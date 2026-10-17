<p align="center">
<a href="../../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
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
<img src="../icon.png" width="160px" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Versión de escritorio</a>] · [<a href="https://github.com/okeydw/SoundCloud-iOS">Versión iOS</a>]</b><br>
<b>Cliente no oficial de SoundCloud para Android</b><br>
Sin anuncios · Sin captcha · Sin censura · Disponible en Rusia
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
<img src="https://img.shields.io/badge/Descargar_APK-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"/>
</a>
</p>

---

## ¿Qué es?

**SoundCloud Android** es un cliente nativo de SoundCloud para el móvil. Está sincronizado con [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop).

Escrito en **Kotlin + Jetpack Compose + Media3**, funciona de forma nativa y consume el mínimo de recursos.

El cliente sale en dos versiones con las mismas funciones, solo cambia el aspecto:

> `SoundCloud Full` - diseño Glass al estilo de la versión de escritorio, para quienes aprecian el brillo y tienen móviles potentes.

> `SoundCloud Lite` - la interfaz simplificada de las primeras versiones, para quienes aprecian el minimalismo o tienen móviles más modestos.

### [Versión iOS (beta)](https://github.com/okeydw/SoundCloud-iOS)

---

## Descargar

Ve a la [página de versiones](https://github.com/okeydw/SoundCloud-Android/releases/latest) y descarga el `.apk` que necesites:

| Archivo | Versión |
| --- | --- |
| `SoundCloud-Full-vX.Y.Z.apk` | SoundCloud, diseño Glass |
| `SoundCloud-Lite-vX.Y.Z.apk` | SoundCloud Lite, interfaz sencilla |

**Instalación:** abre el archivo descargado en el móvil y permite instalar desde orígenes desconocidos. Después, las actualizaciones se instalan desde la propia app.

**Requisitos:** Android 8.0 (Oreo) o superior.

---

## Cómo iniciar sesión

Después viene el inicio de sesión, a través de SoundCloud o con un código QR desde la versión de escritorio.

1. **QR desde el escritorio (recomendado).** En [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) abre Ajustes -> Cuenta -> Transferir sesión a otro dispositivo y luego escanea el código QR.
2. **QR desde el móvil.** En la pantalla de inicio de sesión pulsa "Mostrar QR para iniciar sesión" y escanéalo donde ya tengas la sesión iniciada.
3. **Con el navegador.** El botón "Iniciar sesión con el navegador" abre el inicio de sesión normal de SoundCloud. Esta sesión puede cerrarse si después inicias sesión en otro sitio.

---

## Capturas de pantalla

> Los colores del fondo, del acento y del texto se pueden cambiar con una paleta RGB. También puedes añadir una imagen de fondo y efectos.

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

## Funciones

### Música

- **Ola y recomendaciones** - sistema de recomendaciones propio basado en tus me gusta y tu historial de escucha
- **Búsqueda** - pistas, artistas, listas, álbumes y búsqueda por vibra (una red neuronal elige pistas según el contexto)
- **Reproductor** - aleatorio, repetición, cola, letras, me gusta y no me gusta
- **Ecualizador** - presets listos y la opción de crear el tuyo
- **Listas** - tus propias listas locales sincronizadas con SoundCloud, públicas o privadas, el orden de las pistas se cambia arrastrando y se guarda
- **Historial y estadísticas** - tiempo de escucha, pistas y artistas más escuchados, gráficos por días y por horas
- **Perfiles** - seguir y dejar de seguir, enlaces del perfil de SoundCloud
- **SoundCloud Auto (beta)** - escenarios para conectarse a dispositivos Bluetooth

### Modo sin conexión

- Descarga de pistas y listas, modo sin conexión con caché
- Ajuste manual del límite de caché y del momento en que se vacía
- Descargas y listas compartidas entre Full y Lite si tienes las dos instaladas (beta, funciona de forma parcial)

### Importación (beta)

- **YouTube / YouTube Music** - me gusta, listas y álbumes con una cuenta de Google
- **Yandex Music** - me gusta, listas y álbumes por token
- **Spotify** - me gusta, listas y álbumes con un client_id

###### [Más información](../import-lang/import.es.md)

> ¡¡¡Importante!!! Buscamos pistas parecidas, no importamos el audio en sí
> (pero así se puede esquivar la censura, fck censor)

---

## Comentarios
Comunidad: [Discord](https://discord.gg/Au3ebtfYu3)

¿Has encontrado un error o tienes una idea? Lo más fácil es "Ajustes -> Acerca del sistema -> Soporte": abre un issue en GitHub o un ticket en Discord, los datos del dispositivo se rellenan solos. También puedes [abrir un issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) a mano y adjuntar los registros: "Ajustes -> Registros -> Compartir".

---

## Tecnologías

| Capa | Tecnologías |
| --- | --- |
| Lenguaje | Kotlin |
| UI | Jetpack Compose, Material 3, Haze (cristal en Full) |
| Audio | Media3 (ExoPlayer + MediaSession), servicio en segundo plano con notificación |
| Red | OkHttp (+ caché en disco), kotlinx.serialization |
| Imágenes | Coil (+ Palette para teñir según la portada) |
| Cámara | CameraX + ML Kit (escáner QR en Full), ZXing |
| Almacenamiento | SharedPreferences (ajustes, sesión), índices JSON de descargas y listas |

Backend: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

Estado de los servidores [aquí](https://status.soundcloud-desktop.fun/)

**Compatibilidad:** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

La lista completa de cambios está en [CHANGELOG.md](../../CHANGELOG.md).

---

## Para desarrolladores

**Compilación:** JDK 17, Android SDK 35.

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK en app/build/outputs/apk/<flavor>/debug/
./gradlew testLiteDebugUnitTest                 # pruebas unitarias
./gradlew lintLiteDebug                         # lint (con baseline: ./gradlew updateLintBaseline)
```

**Código fuente:**

| Carpeta | Contenido |
| --- | --- |
| `app/src/main` | lógica: API, reproductor, descargas, listas, importación |
| `app/src/shared` | pantallas y diálogos comunes (ajustes, etc.) |
| `app/src/lite` | interfaz Lite |
| `app/src/full` | interfaz Glass de Full |

**Versión:** una etiqueta `vX.Y.Z` (debe coincidir con `versionName`) compila y firma las dos versiones con una sola clave y las publica con la descripción de `CHANGELOG.md`.

**Backend:**

| Dominio | Para qué |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | audio |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | almacenamiento de pistas |
| `images.scnative.space` | portadas |
| `pay.scnative.space` | pago de STAR |
| `health.scnative.space` | comprobación de conexión |
| `call-*.scnative.space` | «Red de usuarios» |
| `*.relay.scnative.space` | rutas de respaldo |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | descarga directa de la pista desde SoundCloud |

Protocolo de inicio de sesión por QR: [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) en el repositorio de escritorio.

---


## Licencia

MIT. Detalles en el archivo [LICENSE](../../LICENSE).

_SoundCloud es una marca registrada de SoundCloud Ltd. Esta aplicación no está afiliada a SoundCloud._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/Descargar_SoundCloud_Android-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Descargar SoundCloud Android"/>
</a>
</p>

<p align="center">
<code>soundcloud android</code> · <code>soundcloud apk</code> · <code>cliente soundcloud</code> · <code>soundcloud para android</code> · <code>soundcloud sin anuncios</code> · <code>soundcloud bloqueado</code> · <code>soundcloud no funciona</code> · <code>soundcloud android app</code> · <code>soundcloud player</code> · <code>soundcloud apk download</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code> · <code>reproductor de música soundcloud</code>
</p>
