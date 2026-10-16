<p align="center">
<a href="import.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="import.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="import.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="import.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="import.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="import.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="import.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="import.tr.md"><img src="https://img.shields.io/badge/TR-777777?style=flat-square" alt="Türkçe"/></a>
<a href="import.ko.md"><img src="https://img.shields.io/badge/KO-0048FF?style=flat-square" alt="한국어"/></a>
</p>

# 음악 가져오기

가져오기는 **설정 -> 가져오기 (베타)**에 있습니다.
동작 방식:

- **Spotify와 Yandex Music.** 앱은 원본을 복사하지 않고, 제목과 아티스트로 SoundCloud에서 같은 트랙을 찾습니다.
- **YouTube.** YouTube에서 트랙 자체를 가져오거나 SoundCloud에서 비슷한 트랙을 찾을 수 있습니다.
- YouTube에서 직접 가져온 트랙과 플레이리스트는 SoundCloud와 동기화되지 않습니다
- 플레이리스트에 트랙이 500개를 넘으면 여러 플레이리스트로 나뉘어 만들어집니다
- 토큰과 세션은 휴대폰에만 저장됩니다.

## 링크로

1. 트랙, 플레이리스트 또는 앨범 링크를 복사하세요.
2. "링크로 가져오기" 입력란에 붙여넣으세요.
3. 추가할 위치를 고르고 "가져오기"를 누르세요.

공개 링크는 로그인 없이도 됩니다. 비공개 플레이리스트와 좋아요는 아래에서 계정을 연결하세요.
> Spotify!
> 로그인하지 않으면 플레이리스트에서 처음 100곡만 읽을 수 있습니다.


## Spotify

1. [Spotify 개발자 대시보드](https://developer.spotify.com/dashboard)를 열고 내 계정으로 로그인합니다.
2. **Create app**을 누릅니다. 이름과 설명은 아무거나 괜찮습니다.
3. **Redirect URIs**에 앱 버전을 추가합니다. 두 개 모두 추가해도 되지만 하나씩 따로 추가하세요:
   - Lite: `com.scd.android://spotify-auth`
   - Full: `com.scd.android.full://spotify-auth`
4. **Which API/SDKs are you planning to use?**에서 **Web API**를 선택하고 저장합니다.
5. **Settings -> User Management**를 열고 가져올 Spotify 계정의 이메일을 추가합니다.
6. **Client ID**(영문자와 숫자 32자)를 복사해 앱에 붙여넣습니다: **가져오기 -> Spotify -> Client ID**.
7. 버튼이 "앱 승인"으로 바뀝니다. 누르면 승인 창이 열리고, 거기서 확인을 누릅니다.

모두 올바르면 Spotify 이름과 프로필 사진이 표시되고, 이제 좋아요와 비공개 플레이리스트를 가져올 수 있습니다.

중요:

- 개발 모드의 Spotify 앱은 User Management에 등록된 계정에서만 동작합니다. 대시보드의 앱 소유자에게 Spotify Premium이 필요하며, 없으면 Spotify가 403을 반환합니다.
- Spotify가 Client ID를 거부하면 입력란 아래에 오류가 표시됩니다. 대시보드에서 ID와 Redirect URI를 확인하세요.

입력란이 비어 있으면 "Spotify 로그인" 버튼이 내장 로그인 창을 엽니다. 더 간단하지만 항상 되지는 않습니다.

## YouTube

1. **가져오기 -> YouTube (Music)**을 열고 **계정 추가**를 누릅니다.
2. 열린 창에서 Google에 로그인합니다. 창은 자동으로 닫히고 계정 이름과 아바타가 표시됩니다.
3. **좋아요 가져오기**는 좋아요한 동영상을 SoundCloud에서 찾아 플레이리스트로 모읍니다.

링크로 트랙이나 플레이리스트를 가져올 수 있습니다. 모드는 두 가지입니다:

- **YouTube에서 가져오기.** 오디오를 YouTube에서 받으며, 플레이리스트는 휴대폰에만 남습니다.
- **SoundCloud에서 비슷한 트랙 찾기.** SoundCloud에서 같은 트랙을 찾습니다.

Google이 안전하지 않은 브라우저라고 하면 VPN을 사용하세요.

## Yandex Music

1. **가져오기 -> Yandex Music**을 열고 **Yandex로 로그인**을 누릅니다.
2. 브라우저가 열립니다. 원하는 계정을 고르세요.
3. 이동된 페이지의 주소를 복사한 뒤 앱으로 돌아와 "링크 붙여넣기" 버튼을 누릅니다.
4. **좋아요 가져오기**는 좋아하는 트랙을 옮겨 옵니다.

자동 로그인도 있으니 시도해 보세요.

계정을 바꾸려면 **계정 삭제**를 누르고 다시 로그인하세요.
