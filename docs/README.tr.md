<p align="center">
<a href="../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="README.tr.md"><img src="https://img.shields.io/badge/TR-0048FF?style=flat-square" alt="Türkçe"/></a>
<a href="README.ko.md"><img src="https://img.shields.io/badge/KO-777777?style=flat-square" alt="한국어"/></a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="160px" style="border-radius: 50%;" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Masaüstü sürümü</a>]</b><br>
<b>Android için resmi olmayan SoundCloud istemcisi</b><br>
Reklamsız · Captcha yok · Sansürsüz
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
<img src="https://img.shields.io/badge/APK_indir-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="APK indir"/>
</a>
</p>

---

## Bu nedir?

**SoundCloud Android**, telefon için yerel bir SoundCloud istemcisidir. [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) ile aynı arka ucu kullanır, bu yüzden tüm katalog doğrudan erişilebilir.

**Kotlin + Jetpack Compose + Media3** ile yazıldı; yerel çalışır ve minimum kaynak tüketir.

Şimdilik süslemesiz, hafif bir sürüm; hata olmadığından ve işlevlerin tam olduğundan emin olunca tasarımı bitireceğim, ardından iki sürüme ayıracağım:

> `Full` — bir sürü güzel efekt ve masaüstü sürümüne yakın bir tasarım.

> `Lite` — sadeliği ve zayıf telefonları önemseyenler için basitleştirilmiş tasarım.

### iOS portu olmayacak!

---

## Özellikler

- **Arama & Wave** — parça/sanatçı/çalma listesi araması ve kişisel öneri akışı
- **Oynatıcı** — arka planda oynatma, bildirimden kontrol, waveform, karıştır/tekrarla, hareketler
- **Kitaplık** — beğeni/beğenmeme, çalma listeleri, geçmiş, sanatçı profilleri
- **Çevrimdışı** — parça indirme ve önbellekli çevrimdışı mod
- **Görünüm** — koyu/açık tema, sürükleyici mod ve 9 dil

---

## İndir

[Sürümler sayfasına](https://github.com/okeydw/SoundCloud-Android/releases/latest) git ve `.apk` dosyasını indir.

**Kurulum:** indirilen dosyayı telefonunda aç ve bilinmeyen kaynaklardan kuruluma izin ver.

**Gereksinimler:** Android 8.0 (Oreo) veya üzeri.

---

## Ekran görüntüleri

> Arka plan, metin rengi ve tema değiştirilebilir — kendi arka plan görseliniz, RGB paleti ve hazır temalar.

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

## Geri bildirim

Hata mı buldun ya da fikrin mi var? — [issue aç](https://github.com/okeydw/SoundCloud-Android/issues/new/choose).

---

## Teknoloji

| Katman | Teknoloji |
| --- | --- |
| Dil | Kotlin |
| Arayüz | Jetpack Compose, Material 3 |
| Ses | Media3 (ExoPlayer + MediaSession), bildirimli ön plan servisi |
| Ağ | OkHttp (+ disk önbelleği), kotlinx.serialization |
| Görseller | Coil (+ kapağa göre renklendirme için Palette) |
| Depolama | SharedPreferences (ayarlar, oturum), indirilen parçaların JSON dizini |

[SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) ile aynı arka uç — uygulama ona bir başka istemci olarak çalışır.

**Uyumluluk:** `minSdk 26` (Android 8.0) … `targetSdk 35`, `compileSdk 35`.

---

Tam değişiklik günlüğü [CHANGELOG.md](../CHANGELOG.md) dosyasında.

---

## Lisans

MIT. Ayrıntılar [LICENSE](../LICENSE) dosyasında.

_SoundCloud, SoundCloud Ltd.'nin ticari markasıdır. Bu uygulama SoundCloud ile bağlantılı değildir._

---

<p align="center">
<code>soundcloud android</code> · <code>soundcloud apk</code> · <code>soundcloud istemcisi</code> · <code>android için soundcloud</code> · <code>reklamsız soundcloud</code> · <code>soundcloud engellendi</code> · <code>soundcloud çalışmıyor</code> · <code>soundcloud android app</code> · <code>soundcloud player</code> · <code>soundcloud apk download</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code> · <code>soundcloud müzik çalar</code>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/SoundCloud_Android_indir-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="SoundCloud Android indir"/>
</a>
</p>
