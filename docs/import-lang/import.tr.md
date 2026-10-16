<p align="center">
<a href="import.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="import.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="import.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="import.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="import.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="import.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="import.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="import.tr.md"><img src="https://img.shields.io/badge/TR-0048FF?style=flat-square" alt="Türkçe"/></a>
<a href="import.ko.md"><img src="https://img.shields.io/badge/KO-777777?style=flat-square" alt="한국어"/></a>
</p>

# Müzik içe aktarma

İçe aktarma **Ayarlar -> İçe aktar (beta)** bölümünde.
Nasıl çalışır:

- **Spotify ve Yandex Music.** Uygulama orijinalleri kopyalamaz, aynı parçaları SoundCloud'da isim ve sanatçıya göre arar.
- **YouTube.** Parçanın kendisini YouTube'dan alabilir ya da SoundCloud'da benzerini bulabilirsin.
- Doğrudan YouTube'dan alınan parçalar ve çalma listeleri SoundCloud ile senkronize edilmez
- Bir çalma listesinde 500'den fazla parça varsa ayrı listeler oluşturulur
- Tokenlar ve oturumlar yalnızca telefonda saklanır.

## Bağlantıyla

1. Bir parçanın, çalma listesinin veya albümün bağlantısını kopyala.
2. "Bağlantıyla içe aktar" alanına yapıştır.
3. Nereye ekleneceğini seç ve "İçe aktar"a dokun.

Herkese açık bağlantılar giriş yapmadan çalışır. Gizli listeler ve beğeniler için aşağıdan bir hesap bağla.
> Spotify!
> Giriş yapmadan bir listeden yalnızca ilk 100 parça okunur.


## Spotify

1. [Spotify geliştirici panelini](https://developer.spotify.com/dashboard) aç ve kendi hesabınla giriş yap.
2. **Create app**'e dokun. Ad ve açıklama herhangi bir şey olabilir.
3. **Redirect URIs** kısmına uygulama sürümlerini ekle. İkisini de ekleyebilirsin, yalnızca ayrı ayrı ekle:
   - Lite: `com.scd.android://spotify-auth`
   - Full: `com.scd.android.full://spotify-auth`
4. **Which API/SDKs are you planning to use?** kısmında **Web API**'yi işaretle ve kaydet.
5. **Settings -> User Management**'ı aç ve içe aktaracağın Spotify hesabının e-postasını ekle.
6. **Client ID**'yi (32 harf ve rakam) kopyala ve uygulamaya yapıştır: **İçe aktarma -> Spotify -> Client ID**.
7. Düğme "Uygulamayı yetkilendir" olarak değişir. Ona dokun ve açılan yetkilendirme penceresinde onayla.

Her şey doğruysa Spotify adın ve profil resmin görünür, ardından beğenileri ve gizli listeleri içe aktarabilirsin.

Önemli:

- Geliştirme modundaki bir Spotify uygulaması yalnızca User Management'taki hesaplarla çalışır. Paneldeki uygulama sahibinin Spotify Premium'u olmalı, yoksa Spotify 403 döndürür.
- Spotify Client ID'yi reddederse alanın altında bir hata görünür: paneldeki ID'yi ve Redirect URI'yi kontrol et.

Alan boşsa "Spotify'a giriş yap" düğmesi yerleşik bir giriş penceresi açar. Daha kolaydır ama her zaman çalışmaz.

## YouTube

1. **İçe aktarma -> YouTube (Music)** bölümünü aç ve **Hesap ekle**'ye dokun.
2. Açılan pencerede Google'a giriş yap. Pencere kendiliğinden kapanır, hesap adı ve avatarı görünür.
3. **Beğenileri aktar**, beğendiğin videoları SoundCloud'da arar ve bir çalma listesinde toplar.

Bağlantıyla bir parça veya çalma listesi içe aktarılabilir. İki mod var:

- **YouTube'dan al.** Ses YouTube'dan indirilir, liste yalnızca telefonda kalır.
- **SoundCloud'da benzerlerini bul.** Aynı parçaları SoundCloud'da arar.

Google tarayıcının güvenli olmadığını söylerse VPN kullan.

## Yandex Music

1. **İçe aktarma -> Yandex Music** bölümünü aç ve **Yandex ile giriş yap**'a dokun.
2. Tarayıcı açılır. İstediğin hesabı seç.
3. Yönlendirildiğin sayfanın adresini kopyala, uygulamaya dön ve "Bağlantıyı yapıştır" düğmesine dokun.
4. **Beğenileri aktar** sevdiğin parçaları taşır.

Otomatik giriş de var, deneyebilirsin.

Hesabı değiştirmek için **Hesabı unut**'a dokun ve yeniden giriş yap.
