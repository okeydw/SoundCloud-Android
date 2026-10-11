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
<img src="docs/icon.png" width="160px" />
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

Клиент выходит в двух версиях, функционал у них общий, отличается только внешний вид:

> `SoundCloud Full` - Glass-дизайн в стиле десктопной версии: стекло, ауры, анимации, свои экраны и плеер.

> `SoundCloud Lite` - привычный простой интерфейс, для тех, кто ценит минимализм и слабые телефоны.

### IOS начал планироваться, возможно скоро сделаем

---

## Скачать

Перейди на [страницу релизов](https://github.com/okeydw/SoundCloud-Android/releases/latest) и скачай нужный `.apk`:

| Файл | Версия |
| --- | --- |
| `SoundCloud-Full-vX.Y.Z.apk` | SoundCloud, Glass-дизайн |
| `SoundCloud-Lite-vX.Y.Z.apk` | SoundCloud Lite, простой интерфейс |

**Установка:** открой скачанный файл на телефоне и разреши установку из неизвестных источников. Дальше обновления ставятся из самого приложения.

**Требования:** Android 8.0 (Oreo) или новее.

---

## Как войти

Без входа приложение дальше экрана логина не пустит (кроме офлайн-режима с уже скачанными треками). Способы:

1. **QR с десктопа (рекомендую).** В [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) открой "Передать сессию на другое устройство", в приложении на телефоне нажми "Открыть камеру" и отсканируй код встроенным сканером.
2. **QR с телефона.** На экране входа нажми "Показать QR для входа" и отсканируй его там, где ты уже вошёл.
3. **Через браузер.** Кнопка "Войти через браузер" открывает обычный вход SoundCloud. Такая сессия может слетать, если потом войти где-то ещё.

---

## Скриншоты

> Фон, цвет текста и тему можно менять под себя: своя картинка на фон, RGB-палитра и готовые темы.

### SoundCloud (Full)

<p align="center">
<img src="docs/screenshots/full/wave.png" width="24%" />
<img src="docs/screenshots/full/player.png" width="24%" />
<img src="docs/screenshots/full/equalizer.png" width="24%" />
<img src="docs/screenshots/full/search.png" width="24%" />
</p>
<p align="center">
<img src="docs/screenshots/full/suggestions.png" width="24%" />
<img src="docs/screenshots/full/library.png" width="24%" />
<img src="docs/screenshots/full/playlists.png" width="24%" />
<img src="docs/screenshots/full/settings.png" width="24%" />
</p>

### SoundCloud Lite

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

## Возможности

Всё ниже есть в обеих версиях, если не сказано иначе.

### Музыка

- **Волна и рекомендации** - персональная лента, полки и подборки, радио от трека и от артиста
- **Поиск** - треки, артисты, плейлисты, альбомы и вайб-поиск (описываешь настроение словами, бета)
- **Плеер** - фоновое воспроизведение, управление из уведомления, waveform, shuffle/repeat, жесты, тексты песен (синхронные, если есть)
- **Плавные переходы** между треками, длина от 1 до 30 секунд
- **Эквалайзер** - системные и свои пресеты, ненужные стандартные можно скрыть
- **Вывод звука** - можно выбрать динамик, проводные наушники, Bluetooth или USB
- **Очередь** - "играть следующим" из меню любого трека

### Библиотека

- **Лайки и дизлайки** - дизлайкнутые треки пропускаются, есть отдельный список
- **Плейлисты** - свои локальные плейлисты с синхронизацией в SoundCloud, публичные или приватные, свои обложки, порядок треков меняется перетаскиванием и сохраняется
- **История и статистика** - время прослушивания, топ треков и артистов, график по дням и по часам
- **Карточки артистов** - био, соцсети, альбомы и похожие артисты
- **Профили** - подписка и отписка, ссылки из профиля SoundCloud
- **Блокировка артистов** - как дизлайк, только для артиста целиком: не попадает в волну, радио и рекомендации, список общий с Desktop

### Оффлайн и загрузки

- Скачивание треков и целых плейлистов, автономный режим с кэшем
- Лимит и срок хранения кэша, улучшение качества уже скачанного (для Star)
- Общие загрузки и плейлисты между Full и Lite, если стоят обе версии (бета, работает выборочно)

### Импорт (бета)

- **YouTube / YouTube Music** - трек или плейлист по ссылке, аудиодорожка и обложка берутся с YouTube напрямую, можно сразу собрать плейлист (в РФ нужен VPN)
- **Яндекс Музыка** - лайки, плейлисты и альбомы по токену или через вход в Яндекс прямо в приложении
- **Spotify** - плейлисты, альбомы и треки по ссылке

Для Яндекса и Spotify приложение **ищет похожие треки на SoundCloud, а не переносит оригиналы**. Найденное можно сразу поставить на скачивание.
(зато контрит цензуру, fck censoor)
### SoundCloud Auto (бета)

- Автозапуск музыки при подключении к машине по Bluetooth
- Выбор источника (лайки, волна, загрузки, плейлист), устройств и перемешивания
- Отдельный пресет эквалайзера для машины, после отключения возвращается прежний

### Сеть

- Несколько хостов и relay-узлов, узлы находятся сами
- Выбор узла вручную с пингом и проверкой 64 КБ: видно, если провайдер режет соединение после 13 КБ, и авто такой узел не выберет
- Плашка "нет сети/сервер недоступен" появляется только если связи нет 15 секунд

### Оформление

- 9 языков
- Темы, свой фон (картинка с размытием и затемнением), цвета акцента и текста, RGB-палитра, экспорт и импорт тем
- Иконка приложения: фиолетовая, синяя или оранжевая (своя картинка ставится отдельным ярлыком, это ограничение Android)
- **Только Full:** Glass-интерфейс, аура профиля для Star (синхронизируется с Desktop), иммерсивный плеер, плавное открытие настроек

### Прочее

- Автообновление: нажимаешь на плашку о новой версии, APK скачивается и открывается установщик
- Вход по QR (камерой прямо в приложении), через ссылку или браузер
- Привязанный Discord виден в аккаунте
- Поддержка в "О системе": issue на GitHub или тикет в Discord, метрика устройства подставляется сама

---

## Обратная связь

Нашёл баг или есть идея? Проще всего через "Настройки -> О системе -> Поддержка": откроется issue на GitHub или тикет в Discord, метрика подставится сама. Или [открой issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) вручную и приложи логи: "Настройки -> Логи -> Поделиться".

---

## Стек

| Слой | Технологии |
| --- | --- |
| Язык | Kotlin |
| UI | Jetpack Compose, Material 3, Haze (стекло в Full) |
| Аудио | Media3 (ExoPlayer + MediaSession), фоновый сервис с уведомлением |
| Сеть | OkHttp (+ дисковый кэш), kotlinx.serialization |
| Изображения | Coil (+ Palette для подкраски под обложку) |
| Камера | CameraX + ML Kit (сканер QR в Full), ZXing |
| Хранение | SharedPreferences (настройки, сессия), JSON-индексы загрузок и плейлистов |

Тот же бэкенд, что и у [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop): приложение работает как ещё один клиент к нему.

**Совместимость:** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

Полный список изменений в [CHANGELOG.md](CHANGELOG.md).

---

## Для разработчиков

**Сборка:** JDK 17, Android SDK 35.

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK в app/build/outputs/apk/<flavor>/debug/
./gradlew testLiteDebugUnitTest                 # юнит-тесты
./gradlew lintLiteDebug                         # lint (с baseline: ./gradlew updateLintBaseline)
```

**Исходники:**

| Папка | Что внутри |
| --- | --- |
| `app/src/main` | логика: API, плеер, загрузки, плейлисты, импорт |
| `app/src/shared` | общие экраны и диалоги (настройки и т.д.) |
| `app/src/lite` | интерфейс Lite |
| `app/src/full` | Glass-интерфейс Full |

**Релиз:** тег `vX.Y.Z` (должен совпадать с `versionName`) собирает и подписывает обе версии одним ключом и публикует их с описанием из `CHANGELOG.md`.

**Бэкенд:**

| Назначение | Хосты |
| --- | --- |
| API | `api.scnative.space`, `api-star.scnative.space` (Star) |
| Стриминг | `stream.scnative.space`, `stream-star.scnative.space` |
| Хранилище | `storage.scnative.space`, `storage-star.scnative.space` |
| Картинки | `images.scnative.space` |
| Relay-узлы | `*.rN.relay.scnative.space`, список: `health.scnative.space/topology` |

Протокол входа по QR: [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) в репозитории десктопа.

---

## Данные и доверие

- Приложение работает через бэкенд `*.scnative.space` (тот же, что у десктопа). **Сессия и токены SoundCloud хранятся на этом бэкенде**, на телефоне лежит только идентификатор сессии.
- "Выйти" удаляет сессию и на бэкенде (`POST /auth/logout`), и все данные на телефоне.
- Прямой запасной путь к SoundCloud (можно выключить в настройках) ходит на soundcloud.com без твоей сессии.
- Токен Яндекс Музыки для импорта хранится только на телефоне. Пароль от Яндекса приложение не видит: вход идёт на странице самого Яндекса.

---


## Лицензия

MIT. Подробности в файле [LICENSE](LICENSE).

_SoundCloud - торговая марка SoundCloud Ltd. Это приложение не аффилировано с SoundCloud._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/Скачать_SoundCloud_Android-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Скачать SoundCloud Android"/>
</a>
</p>
