<p align="center">
<a href="../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-0048FF?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
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
<b>Неафіцыйны кліент SoundCloud для Android</b><br>
Без рэкламы · Без капчы · Без цэнзуры
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
<img src="https://img.shields.io/badge/Спампаваць_APK-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Спампаваць APK"/>
</a>
</p>

---

> [!WARNING]
> ⚠️ **З-за сістэм бяспекі асноўнага API і SoundCloud уваход можа выкінуць вас з іншай сесіі.** Калі вы залагінены на іншай прыладзе (напрыклад, у дэсктопным кліенце), уваход тут можа «адвязаць» тую сесію, і наадварот. Гэта абмежаванне бэкенда, а не памылка праграмы — проста ўвайдзіце зноў там, дзе трэба.

---

## Што гэта?

**SoundCloud Android** — нативны кліент SoundCloud для тэлефона. Ён выкарыстоўвае той жа бэкенд, што і [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop), таму ўвесь каталог даступны напрамую.

Напісаны на **Kotlin + Jetpack Compose + Media3**, працуе нативна і спажывае мінімум рэсурсаў.

Пакуль гэта лёгкая версія без залішніх упрыгожванняў; дызайн дараблю, калі ўпэўнюся, што няма багаў і функцыянал поўны, а потым падзялю на дзве версіі:

> `Full` — шмат прыгожых эфектаў і дызайн, блізкі да дэсктопнай версіі.

> `Lite` — спрошчаны дызайн для тых, хто цэніць мінімалізм і слабейшыя тэлефоны.

### iOS-порта не будзе!

---

## Магчымасці

- **Пошук і Wave** — пошук трэкаў/выканаўцаў/плэйлістаў і персанальная стужка рэкамендацый
- **Плэер** — прайграванне ў фоне, кіраванне з апавяшчэння, waveform, перамешванне/паўтор, жэсты
- **Бібліятэка** — лайкі/дызлайкі, плэйлісты, гісторыя, профілі выканаўцаў
- **Афлайн** — спампоўванне трэкаў і аўтаномны рэжым з кэшам
- **Аздабленне** — цёмная/светлая тэма, іммерсіўны рэжым і 9 моў

---

## Спампаваць

Перайдзіце на [старонку рэлізаў](https://github.com/okeydw/SoundCloud-Android/releases/latest) і спампуйце `.apk`.

**Усталёўка:** адкрыйце спампаваны файл на тэлефоне і дазвольце ўсталёўку з невядомых крыніц.

**Патрабаванні:** Android 8.0 (Oreo) ці навейшы.

---

## Скрыншоты

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

## Зваротная сувязь

Знайшлі баг ці ёсць ідэя? — [адкрыйце issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose).

---

## Стэк

| Пласт | Тэхналогіі |
| --- | --- |
| Мова | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Аўдыё | Media3 (ExoPlayer + MediaSession), фонавы сэрвіс з апавяшчэннем |
| Сетка | OkHttp (+ дыскавы кэш), kotlinx.serialization |
| Выявы | Coil (+ Palette для падфарбоўкі пад вокладку) |
| Захоўванне | SharedPreferences (налады, сесія), JSON-індэкс спампаваных трэкаў |

Той жа бэкенд, што і ў [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) — праграма працуе як яшчэ адзін кліент да яго.

**Сумяшчальнасць:** `minSdk 26` (Android 8.0) … `targetSdk 35`, `compileSdk 35`.

---

## Структура

```
app/src/main/java/com/scd/android/
  App.kt              - Application: ініцыялізацыя сесіі/кэша/налад, загрузчык вокладак Coil праз проксі (унікальны ключ на выяву)
  MainActivity.kt     - кропка ўваходу, навігацыя (Пошук / Wave / Я), мазаіка плітак, банер «няма інтэрнэту», адсеў недаступных трэкаў, deep-link з апавяшчэнняў
  Api.kt              - API-кліент: пошук, стрым, вокладкі, лайкі/дызлайкі трэкаў і плэйлістаў, гісторыя, мадэлі даных; шматузроўневы HTTP-кэш (халодныя даныя надоўга)
  NetMonitor.kt       - праверка сеткі (Wi-Fi / мабільныя) + афлайн-фолбэк з кэша
  Prefs.kt            - налады (тэма, мова, афлайн, іммерсіў) + кэш імя карыстальніка
  LocaleHelper.kt     - падмена лакалі праграмы (змена мовы)

  PlaybackService.kt  - MediaSessionService: фонавы плэер, апавяшчэнне, лайк/перамешванне ў ім, адкрыццё плэера па тапе
  Player.kt           - міні- і поўнаэкранны плэер (waveform, жэсты, перамешванне/паўтор, іммерсіў, marquee, дадаванне ў плэйліст)
  NowPlaying.kt       - глабальны стан плэера + падзеі навігацыі/абнаўлення плэйлістаў (PlaylistEvents, NavEvents)

  WaveScreen.kt       - Wave: стужка з пэйджарам, свайпы і двайны тап = лайк з анімацыяй, абнаўленне
  ArtistScreen.kt     - профіль выканаўцы: трэкі і плэйлісты (у т.л. афлайн са спампаванага)
  PlaylistScreen.kt   - экран плэйліста: спіс трэкаў, лайк плэйліста, спампаваць усё / скасаваць
  LibraryScreen.kt    - укладка «Я»: прывітанне, лайкі, спампаваныя, свае і ўпадабаныя плэйлісты, гісторыя + экран налад

  Likes.kt / Dislikes.kt - стан лайкаў/дызлайкаў трэкаў (узаемавыключальныя, сінк з бэкендам)
  LikedPlaylists.kt   - стан упадабаных плэйлістаў (сінк з бэкендам)
  Downloads.kt        - спампоўванне трэкаў у прыватную папку, індэкс, апавяшчэнне з прагрэсам, скасаванне і deep-link
  Genres.kt           - спіс жанраў для плітак пры пустым пошуку

app/src/main/res/
  drawable/           - вектарныя іконкі (стыль Lucide) + лагатып
  values/, values-*/  - радкі на 9 мовах + values-night (цёмная тэма)

proguard-rules.pro    - правілы R8 для release-зборкі (serialization / OkHttp)
.github/workflows/    - CI: lint, тэсты, зборка debug APK
```

Поўны спіс змен — у [CHANGELOG.md](../CHANGELOG.md).

---

## Ліцэнзія

MIT. Падрабязнасці — у файле [LICENSE](../LICENSE).

_SoundCloud — гандлёвая марка SoundCloud Ltd. Гэта праграма не афіліявана з SoundCloud._
