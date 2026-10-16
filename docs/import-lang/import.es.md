<p align="center">
<a href="import.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="import.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="import.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="import.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="import.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="import.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="import.es.md"><img src="https://img.shields.io/badge/ES-0048FF?style=flat-square" alt="Español"/></a>
<a href="import.tr.md"><img src="https://img.shields.io/badge/TR-777777?style=flat-square" alt="Türkçe"/></a>
<a href="import.ko.md"><img src="https://img.shields.io/badge/KO-777777?style=flat-square" alt="한국어"/></a>
</p>

# Importar música

La importación está en **Ajustes -> Importar (beta)**.
Cómo funciona:

- **Spotify y Yandex Music.** La app no copia los originales, busca las mismas pistas en SoundCloud por título y artista.
- **YouTube.** Puedes tomar la pista directamente de YouTube o buscar una parecida en SoundCloud.
- Las pistas y listas tomadas directamente de YouTube no se sincronizarán con SoundCloud
- Si una lista tiene más de 500 pistas, se crearán listas separadas
- Los tokens y las sesiones se guardan solo en el móvil.

## Por enlace

1. Copia el enlace de una pista, lista o álbum.
2. Pégalo en el campo "Importar por enlace".
3. Elige dónde añadirlo y pulsa "Importar".

Sin iniciar sesión funcionan los enlaces públicos. Para listas privadas y me gusta, conecta una cuenta más abajo.
> ¡Spotify!
> Sin iniciar sesión solo se leen las primeras 100 pistas de una lista.


## Spotify

1. Abre el [panel de desarrolladores de Spotify](https://developer.spotify.com/dashboard) e inicia sesión con tu cuenta.
2. Pulsa **Create app**. El nombre y la descripción pueden ser cualquiera.
3. En **Redirect URIs** añade las versiones de la app. Puedes añadir las dos, pero de una en una:
   - Lite: `com.scd.android://spotify-auth`
   - Full: `com.scd.android.full://spotify-auth`
4. En **Which API/SDKs are you planning to use?** marca **Web API** y guarda.
5. Abre **Settings -> User Management** y añade el email de la cuenta de Spotify desde la que vas a importar.
6. Copia el **Client ID** (32 letras y números) y pégalo en la app: **Importación -> Spotify -> Client ID**.
7. El botón cambiará a "Autorizar la app". Púlsalo y en la ventana de autorización pulsa confirmar.

Si todo está bien, aparecerán el nombre y la foto de tu perfil de Spotify, y ya puedes importar me gusta y listas privadas.

Importante:

- Una app de Spotify en modo desarrollo solo funciona para las cuentas de User Management. El propietario de la app en el panel necesita Spotify Premium, si no, Spotify responde con 403.
- Si Spotify rechaza el Client ID, aparecerá un error bajo el campo: revisa el ID y la Redirect URI en el panel.

Si el campo está vacío, el botón "Iniciar sesión en Spotify" abre una ventana de inicio de sesión integrada. Es más sencillo, pero no siempre funciona.

## YouTube

1. Abre **Importación -> YouTube (Music)** y pulsa **Añadir cuenta**.
2. Inicia sesión en Google en la ventana que se abre. La ventana se cerrará sola y aparecerán el nombre y el avatar de la cuenta.
3. **Importar me gusta** busca en SoundCloud los vídeos que te gustan y los reúne en una lista.

Por enlace se puede importar una pista o una lista. Hay dos modos:

- **Tomar de YouTube.** El audio se descarga de YouTube y la lista se queda solo en el móvil.
- **Buscar similares en SoundCloud.** Busca las mismas pistas en SoundCloud.

Si Google dice que el navegador no es seguro, usa una VPN.

## Yandex Music

1. Abre **Importación -> Yandex Music** y pulsa **Entrar con Yandex**.
2. Se abrirá el navegador. Elige la cuenta que quieras.
3. Copia la dirección de la página a la que te redirige, vuelve a la app y pulsa "Pegar enlace".
4. **Importar me gusta** trae tus pistas favoritas.

También hay inicio de sesión automático, puedes probarlo.

Para cambiar de cuenta, pulsa **Olvidar cuenta** e inicia sesión de nuevo.
