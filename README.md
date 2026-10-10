<p align="center">
<a href="README.md"><img src="https://img.shields.io/badge/RU-0048FF?style=flat-square" alt="Русский"/></a>
<a href="docs/README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="docs/README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="docs/README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="docs/README.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="docs/README.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="docs/README.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="docs/README.tr.md"><img src="https://img.shields.io/badge/TR-777777?style=flat-square" alt="Türkçe"/></a>
<a href="docs/README.ko.md"><img src="https://img.shields.io/badge/KO-777777?style=flat-square" alt="한국어"/></a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="160px" style="border-radius: 50%;" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Desktop версия</a>]</b><br>
<b>Неофициальный клиент SoundCloud для Android</b><br>
Без рекламы · Без капчи · Без цензуры · Доступно в России
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/github/v/release/okeydw/SoundCloud-Android?style=for-the-badge&logo=github&color=0048FF&label=VERSION" alt="Version"/>
</a>

<a href="https://github.com/okeydw/SoundCloud-Android/releases">
<img src="https://img.shields.io/github/downloads/okeydw/SoundCloud-Android/total?style=for-the-badge&logo=github&color=0048FF&label=Downloads" alt="Downloads"/>
</a>

<a href="LICENSE">
<img src="https://img.shields.io/badge/License-MIT-0048FF?style=for-the-badge" alt="License"/>
</a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/Скачать_APK-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"/>
</a>
</p>

---

## Что это?

**SoundCloud Android** - нативный клиент SoundCloud для телефона. Использует тот же бэкенд, что и [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop), поэтому весь каталог доступен напрямую.

Написан на **Kotlin + Jetpack Compose + Media3**, работает нативно и потребляет минимум ресурсов.

На данный момент сделана лёгковесная версия без излишеств, дизайн прикручу, когда пойму что багов нет и функционал в полном объёме, после разделю на две версии: 

> `Full` - много красивых блестяшек и дизайн схожий с десктопной версией.

> `Lite` - упрощённый дизайн, кто ценит минимализм и слабые телефоны. 

### Для iOS порта не будет! (от меня, мб кто-то другой сделает)


---

## Возможности

- **Поиск** - поиск треков/артистов/плейлистов и персональная лента рекомендаций
- **Плеер** - фоновое воспроизведение, управление из уведомления, waveform, shuffle/repeat, жесты
- **Библиотека** - лайки/дизлайки, плейлисты, история, профили артистов
- **Оффлайн** - скачивание треков и автономный режим с кэшем
- **Оформление** - 9 языков и также полная кастомизация приложения

---

## Скачать

Перейди на [страницу релизов](https://github.com/okeydw/SoundCloud-Android/releases/latest) и скачай `.apk`.

**Установка:** открой скачанный файл на телефоне и разреши установку из неизвестных источников.

**Требования:** Android 8.0 (Oreo) или новее.

**Проверка файла:** в каждом релизе лежит `SHA256SUMS.txt`, а в описании релиза — SHA-256 APK и отпечаток сертификата подписи. Если отпечаток не совпадает с прошлыми релизами — не ставь.

---

## Как войти

Без входа приложение дальше экрана логина не пустит. Способы:

1. **QR с десктопа (рекомендую).** В [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) открой «Передать сессию на другое устройство», в приложении на телефоне нажми «Открыть камеру» и отсканируй код встроенным сканером.
2. **QR с телефона.** На экране входа нажми «Показать QR для входа» и отсканируй его там, где ты уже вошёл.
3. **Через браузер.** Кнопка «Войти через браузер» — обычный вход SoundCloud. Такая сессия может слетать, если потом войти где-то ещё.

Уже вошёл и хочешь залогинить другое устройство — «Настройки → Аккаунт → Войти на другом устройстве».

---

## Данные и доверие

- Приложение работает через бэкенд `*.scnative.space` (тот же, что у десктопа). **Сессия и токены SoundCloud хранятся на этом бэкенде**, на телефоне лежит только идентификатор сессии.
- Идентификатор сессии отправляется только на хосты `*.scnative.space`, на сторонние адреса он не уходит.
- «Выйти» удаляет сессию и на бэкенде (`POST /auth/logout`), и все данные на телефоне.
- Прямой запасной путь к SoundCloud (можно выключить в настройках) ходит на soundcloud.com без твоей сессии.

---

## Скриншоты

> Фон, цвет текста и тему можно менять под себя - своя картинка на фон, RGB-палитра и готовые темы.

<p align="center">
<img src="docs/screenshots/wave.png" width="24%" />
<img src="docs/screenshots/player.png" width="24%" />
<img src="docs/screenshots/player_immersive.png" width="24%" />
<img src="docs/screenshots/search.png" width="24%" />
</p>
<p align="center">
<img src="docs/screenshots/suggestions.png" width="24%" />
<img src="docs/screenshots/library.png" width="24%" />
<img src="docs/screenshots/history.png" width="24%" />
<img src="docs/screenshots/settings.png" width="24%" />
</p>

---

## Обратная связь

Нашёл баг или есть идея? - [открой issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose). К багу приложи логи: «Настройки → Логи → Поделиться».


---



## Стек

| Слой | Технологии |
| --- | --- |
| Язык | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Аудио | Media3 (ExoPlayer + MediaSession), фоновый сервис с уведомлением |
| Сеть | OkHttp (+ дисковый кэш), kotlinx.serialization |
| Изображения | Coil (+ Palette для подкраски под обложку) |
| Хранение | SharedPreferences (настройки, сессия), JSON-индекс скачанных треков |

Тот же бэкенд, что и у [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) - приложение работает как ещё один клиент к нему.

**Совместимость:** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

Полный список изменений - в [CHANGELOG.md](CHANGELOG.md).

---

## Для разработчиков

**Сборка:** JDK 17, Android SDK 35.

```bash
./gradlew assembleDebug        # APK в app/build/outputs/apk/debug/
./gradlew test                 # юнит-тесты
./gradlew lint                 # lint (с baseline: ./gradlew updateLintBaseline)
```

**Бэкенд:**

| Назначение | Хосты |
| --- | --- |
| API | `api.scnative.space`, `api-star.scnative.space` (Star) |
| Стриминг | `stream.scnative.space`, `stream-star.scnative.space` |
| Хранилище | `storage.scnative.space`, `storage-star.scnative.space` |
| Картинки | `images.scnative.space` |
| Relay-узлы | `*.rN.relay.scnative.space`, список — `health.scnative.space/topology` |

Протокол входа по QR — [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) в репозитории десктопа.

---

## Лицензия

MIT. Подробности - в файле [LICENSE](LICENSE).

_SoundCloud - торговая марка SoundCloud Ltd. Это приложение не аффилировано с SoundCloud._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/Скачать_SoundCloud_Android-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Скачать SoundCloud Android"/>
</a>
</p>

