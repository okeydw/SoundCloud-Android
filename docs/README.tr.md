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
<img src="icon.png" width="160px" />
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

**SoundCloud Android**, telefon için yerel bir SoundCloud istemcisidir. [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) ile aynı backend'i kullanır, bu yüzden tüm katalog doğrudan erişilebilir.

**Kotlin + Jetpack Compose + Media3** ile yazıldı, yerel çalışır ve çok az kaynak harcar.

İstemcinin aynı özelliklere sahip iki sürümü var, sadece görünüm farklı:

> `SoundCloud Full` - masaüstü sürümü tarzında Glass tasarım: cam efekti, auralar, animasyonlar, kendine ait ekranlar ve oynatıcı.

> `SoundCloud Lite` - alışılmış sade arayüz, minimalizmi ve zayıf telefonları sevenler için.

### iOS planlanmaya başlandı, belki yakında yaparız

---

## İndir

[Sürümler sayfasına](https://github.com/okeydw/SoundCloud-Android/releases/latest) git ve ihtiyacın olan `.apk` dosyasını indir:

| Dosya | Sürüm |
| --- | --- |
| `SoundCloud-Full-vX.Y.Z.apk` | SoundCloud, Glass tasarım |
| `SoundCloud-Lite-vX.Y.Z.apk` | SoundCloud Lite, sade arayüz |

**Kurulum:** indirilen dosyayı telefonda aç ve bilinmeyen kaynaklardan kuruluma izin ver. Sonrasında güncellemeler uygulamanın içinden kurulur.

**Gereksinimler:** Android 8.0 (Oreo) veya daha yeni.

---

## Nasıl giriş yapılır

Giriş yapmadan uygulama giriş ekranından öteye geçmez (önceden indirilmiş parçalarla çevrimdışı mod hariç). Seçenekler:

1. **Masaüstünden QR (önerilen).** [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) içinde "Oturumu başka cihaza aktar" seçeneğini aç, telefonda "Kamerayı aç" düğmesine dokun ve kodu yerleşik tarayıcıyla okut.
2. **Telefondan QR.** Giriş ekranında "Giriş için QR göster" düğmesine dokun ve zaten giriş yapılmış bir yerde okut.
3. **Tarayıcıda.** "Tarayıcıyla giriş yap" düğmesi normal SoundCloud girişini açar. Sonra başka bir yerde giriş yaparsan bu oturum düşebilir.

---

## Ekran görüntüleri

> Arka plan, metin rengi ve tema özelleştirilebilir: kendi arka plan görselin, RGB paleti ve hazır temalar.

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

## Özellikler

Aksi belirtilmedikçe aşağıdakilerin hepsi iki sürümde de var.

### Müzik

- **Dalga ve öneriler** - kişisel akış, raflar ve seçkiler, bir parçadan veya sanatçıdan radyo
- **Arama** - parçalar, sanatçılar, çalma listeleri, albümler ve vibe araması (ruh halini kelimelerle anlat, beta)
- **Oynatıcı** - arka planda çalma, bildirimden kontrol, waveform, karıştır/tekrarla, hareketler, şarkı sözleri (varsa senkronize)
- **Yumuşak geçişler** - parçalar arasında, 1 ile 30 saniye arası
- **Ekolayzer** - sistem ve özel ön ayarlar, gereksiz standart ön ayarlar gizlenebilir
- **Ses çıkışı** - hoparlör, kablolu kulaklık, Bluetooth veya USB seçilebilir
- **Sıra** - her parçanın menüsünden "sıradaki olarak çal"

### Kitaplık

- **Beğeniler ve beğenmemeler** - beğenilmeyen parçalar atlanır, ayrı bir listesi var
- **Çalma listeleri** - SoundCloud ile senkronize kendi yerel listelerin, herkese açık veya gizli, özel kapaklar, sıra sürükleyerek değişir ve kaydedilir
- **Geçmiş ve istatistik** - dinleme süresi, en çok dinlenen parçalar ve sanatçılar, güne ve saate göre grafikler
- **Sanatçı kartları** - biyografi, sosyal ağlar, albümler ve benzer sanatçılar
- **Profiller** - takip etme ve takibi bırakma, SoundCloud profilindeki bağlantılar
- **Sanatçı engelleme** - beğenmeme gibi, ama tüm sanatçı için: dalgada, radyoda ve önerilerde çıkmaz, liste Desktop ile ortak

### Çevrimdışı ve indirmeler

- Parçaları ve tüm çalma listelerini indirme, önbellekli çevrimdışı mod
- Önbellek boyutu ve saklama süresi sınırı, indirilenlerin kalitesini yükseltme (Star için)
- İkisi de kuruluysa Full ve Lite arasında ortak indirmeler ve listeler (beta, seçici çalışıyor)

### İçe aktarma (beta)

- **YouTube / YouTube Music** - bağlantıyla parça veya çalma listesi, ses ve kapak doğrudan YouTube'dan alınır, hemen bir liste oluşturulabilir (Rusya'da VPN gerekir)
- **Yandex Music** - token ile veya uygulama içinde Yandex'e giriş yaparak beğeniler, listeler ve albümler
- **Spotify** - bağlantıyla çalma listeleri, albümler ve parçalar

Yandex Music ve Spotify için uygulama **SoundCloud'da benzer parçaları arar, orijinalleri aktarmaz**. Bulunanlar hemen indirmeye gönderilebilir.
(ama sansürü yener, fck censoor)
### SoundCloud Auto (beta)

- Telefon arabaya Bluetooth ile bağlanınca müzik kendiliğinden başlar
- Kaynak (beğeniler, dalga, indirilenler, liste), cihazlar ve karıştırma seçilebilir
- Araba için ayrı bir ekolayzer ön ayarı, bağlantı kesilince öncekine döner

### Ağ

- Birkaç host ve relay düğümü, düğümler otomatik bulunur
- Ping ve 64 KB testiyle elle düğüm seçimi: sağlayıcının bağlantıyı 13 KB'den sonra kesip kesmediği görünür, otomatik mod böyle bir düğümü seçmez
- "Ağ yok/sunucu kapalı" uyarısı ancak 15 saniye bağlantısızlıktan sonra çıkar

### Görünüm

- 9 dil
- Temalar, özel arka plan (bulanıklık ve karartmalı görsel), vurgu ve metin renkleri, RGB paleti, tema dışa ve içe aktarma
- Uygulama simgesi: mor, mavi veya turuncu (özel bir görsel ayrı bir kısayol olarak eklenir, Android kısıtlaması)
- **Sadece Full:** Glass arayüz, Star için profil aurası (Desktop ile senkronize), sürükleyici oynatıcı, ayarların yumuşak açılması

### Diğer

- Otomatik güncelleme: yeni sürüm uyarısına dokun, APK iner ve yükleyici açılır
- QR ile giriş (kamera doğrudan uygulamada), bağlantıyla veya tarayıcıda
- Bağlı Discord hesabın hesapta görünür
- "Hakkında" bölümünde destek: GitHub issue veya Discord bileti, cihaz metrikleri otomatik doldurulur

---

## Geri bildirim

Bir hata mı buldun ya da bir fikrin mi var? En kolayı "Ayarlar -> Hakkında -> Destek": metriklerle birlikte bir GitHub issue veya Discord bileti açılır. Ya da elle [issue aç](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) ve logları ekle: "Ayarlar -> Loglar -> Paylaş".

---

## Teknolojiler

| Katman | Teknolojiler |
| --- | --- |
| Dil | Kotlin |
| UI | Jetpack Compose, Material 3, Haze (Full'da cam efekti) |
| Ses | Media3 (ExoPlayer + MediaSession), bildirimli arka plan servisi |
| Ağ | OkHttp (+ disk önbelleği), kotlinx.serialization |
| Görseller | Coil (+ kapağa göre renklendirme için Palette) |
| Kamera | CameraX + ML Kit (Full'da QR tarayıcı), ZXing |
| Depolama | SharedPreferences (ayarlar, oturum), indirmeler ve listeler için JSON indeksleri |

[SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) ile aynı backend: uygulama onun için bir istemci daha.

**Uyumluluk:** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

Değişikliklerin tam listesi [CHANGELOG.md](../CHANGELOG.md) dosyasında.

---

## Geliştiriciler için

**Derleme:** JDK 17, Android SDK 35.

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK: app/build/outputs/apk/<flavor>/debug/
./gradlew testLiteDebugUnitTest                 # birim testleri
./gradlew lintLiteDebug                         # lint (baseline: ./gradlew updateLintBaseline)
```

**Kaynak kod:**

| Klasör | İçerik |
| --- | --- |
| `app/src/main` | mantık: API, oynatıcı, indirmeler, listeler, içe aktarma |
| `app/src/shared` | ortak ekranlar ve diyaloglar (ayarlar vb.) |
| `app/src/lite` | Lite arayüzü |
| `app/src/full` | Full'un Glass arayüzü |

**Sürüm:** `vX.Y.Z` etiketi (`versionName` ile aynı olmalı) iki sürümü tek anahtarla derleyip imzalar ve `CHANGELOG.md` metniyle yayınlar.

**Backend:**

| Amaç | Hostlar |
| --- | --- |
| API | `api.scnative.space`, `api-star.scnative.space` (Star) |
| Akış | `stream.scnative.space`, `stream-star.scnative.space` |
| Depolama | `storage.scnative.space`, `storage-star.scnative.space` |
| Görseller | `images.scnative.space` |
| Relay düğümleri | `*.rN.relay.scnative.space`, liste: `health.scnative.space/topology` |

QR giriş protokolü: masaüstü deposundaki [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md).

---

## Veriler ve güven

- Uygulama `*.scnative.space` backend'i üzerinden çalışır (masaüstüyle aynı). **SoundCloud oturumu ve token'lar bu backend'de saklanır**, telefonda sadece oturum kimliği kalır.
- "Çıkış yap" backend'deki oturumu (`POST /auth/logout`) ve telefondaki tüm verileri siler.
- SoundCloud'a doğrudan yedek yol (ayarlardan kapatılabilir) soundcloud.com'a oturumun olmadan gider.
- İçe aktarma için Yandex Music token'ı sadece telefonda saklanır. Uygulama Yandex şifreni asla görmez: giriş Yandex'in kendi sayfasında yapılır.

---

## Lisans

MIT. Ayrıntılar [LICENSE](../LICENSE) dosyasında.

_SoundCloud, SoundCloud Ltd.'nin ticari markasıdır. Bu uygulamanın SoundCloud ile bir bağlantısı yoktur._

---

<p align="center">
<code>soundcloud android</code> · <code>soundcloud apk</code> · <code>soundcloud istemcisi</code> · <code>android için soundcloud</code> · <code>reklamsız soundcloud</code> · <code>soundcloud engellendi</code> · <code>soundcloud çalışmıyor</code> · <code>soundcloud android app</code> · <code>soundcloud player</code> · <code>soundcloud apk download</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code> · <code>soundcloud müzik çalar</code>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/SoundCloud_Android_indir-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="SoundCloud Android indir"/>
</a>
</p>
