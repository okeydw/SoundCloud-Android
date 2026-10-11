<p align="center">
<a href="../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="README.tr.md"><img src="https://img.shields.io/badge/TR-777777?style=flat-square" alt="Türkçe"/></a>
<a href="README.ko.md"><img src="https://img.shields.io/badge/KO-0048FF?style=flat-square" alt="한국어"/></a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="icon.png" width="160px" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">데스크톱 버전</a>]</b><br>
<b>비공식 SoundCloud Android 클라이언트</b><br>
광고 없음 · 캡차 없음 · 검열 없음
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
<img src="https://img.shields.io/badge/APK_다운로드-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="APK 다운로드"/>
</a>
</p>

---

## 이게 뭔가요?

**SoundCloud Android**는 휴대폰용 네이티브 SoundCloud 클라이언트입니다. [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop)와 같은 백엔드를 사용하므로 전체 카탈로그를 바로 이용할 수 있습니다.

**Kotlin + Jetpack Compose + Media3**로 작성되어 네이티브로 동작하며 리소스를 최소한으로 사용합니다.

클라이언트는 기능은 같고 외관만 다른 두 가지 버전으로 제공됩니다:

> `SoundCloud Full` - 데스크톱 버전 스타일의 Glass 디자인: 유리 효과, 오라, 애니메이션, 전용 화면과 플레이어.

> `SoundCloud Lite` - 익숙하고 간결한 인터페이스, 미니멀리즘과 저사양 휴대폰을 선호하는 분들을 위해.

### iOS 버전을 계획하기 시작했습니다, 곧 나올 수도 있어요

---

## 다운로드

[릴리스 페이지](https://github.com/okeydw/SoundCloud-Android/releases/latest)로 이동해 필요한 `.apk`를 다운로드하세요:

| 파일 | 버전 |
| --- | --- |
| `SoundCloud-Full-vX.Y.Z.apk` | SoundCloud, Glass 디자인 |
| `SoundCloud-Lite-vX.Y.Z.apk` | SoundCloud Lite, 간결한 인터페이스 |

**설치:** 휴대폰에서 다운로드한 파일을 열고 알 수 없는 출처의 설치를 허용하세요. 이후 업데이트는 앱 안에서 설치됩니다.

**요구 사항:** Android 8.0 (Oreo) 이상.

---

## 로그인 방법

로그인하지 않으면 앱은 로그인 화면에서 더 나아가지 않습니다 (이미 다운로드한 트랙으로 쓰는 오프라인 모드 제외). 방법:

1. **데스크톱에서 QR (권장).** [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop)에서 "다른 기기로 세션 전송"을 열고, 휴대폰에서 "카메라 열기"를 누른 뒤 내장 스캐너로 코드를 스캔하세요.
2. **휴대폰에서 QR.** 로그인 화면에서 "로그인용 QR 표시"를 누르고 이미 로그인된 곳에서 스캔하세요.
3. **브라우저에서.** "브라우저로 로그인" 버튼은 일반 SoundCloud 로그인을 엽니다. 나중에 다른 곳에서 로그인하면 이 세션이 끊길 수 있습니다.

---

## 스크린샷

> 배경, 텍스트 색상, 테마를 바꿀 수 있습니다: 나만의 배경 이미지, RGB 팔레트, 기본 제공 테마.

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

## 기능

따로 표시하지 않은 기능은 두 버전 모두에 있습니다.

### 음악

- **웨이브와 추천** - 개인 피드, 추천 선반과 셀렉션, 트랙이나 아티스트 기반 라디오
- **검색** - 트랙, 아티스트, 플레이리스트, 앨범, 그리고 분위기 검색 (기분을 말로 설명, 베타)
- **플레이어** - 백그라운드 재생, 알림 컨트롤, 파형, 셔플/반복, 제스처, 가사 (있으면 동기화)
- **부드러운 전환** - 트랙 사이, 1초에서 30초까지
- **이퀄라이저** - 시스템 프리셋과 사용자 프리셋, 필요 없는 기본 프리셋은 숨길 수 있음
- **오디오 출력** - 스피커, 유선 헤드폰, 블루투스 또는 USB 선택
- **재생 대기열** - 모든 트랙 메뉴에서 "다음에 재생"

### 보관함

- **좋아요와 싫어요** - 싫어요 트랙은 건너뛰며, 별도 목록이 있음
- **플레이리스트** - SoundCloud와 동기화되는 나만의 로컬 플레이리스트, 공개 또는 비공개, 사용자 커버, 드래그로 순서를 바꾸면 저장됨
- **기록과 통계** - 청취 시간, 많이 들은 트랙과 아티스트, 일별 및 시간대별 차트
- **아티스트 카드** - 소개, 소셜 네트워크, 앨범, 비슷한 아티스트
- **프로필** - 팔로우와 언팔로우, SoundCloud 프로필의 링크
- **아티스트 차단** - 싫어요와 비슷하지만 아티스트 전체에 적용: 웨이브, 라디오, 추천에 나오지 않으며 목록은 Desktop과 공유

### 오프라인과 다운로드

- 트랙과 플레이리스트 전체 다운로드, 캐시를 활용한 오프라인 모드
- 캐시 용량 및 보관 기간 제한, 이미 다운로드한 트랙의 음질 업그레이드 (Star용)
- 둘 다 설치된 경우 Full과 Lite 간 다운로드와 플레이리스트 공유 (베타, 선택적으로 동작)

### 가져오기 (베타)

- **YouTube / YouTube Music** - 링크로 트랙이나 플레이리스트, 오디오와 커버는 YouTube에서 직접 가져오며 바로 플레이리스트를 만들 수 있음 (러시아에서는 VPN 필요)
- **Yandex Music** - 토큰 또는 앱 안에서 Yandex 로그인으로 좋아요, 플레이리스트, 앨범
- **Spotify** - 링크로 플레이리스트, 앨범, 트랙

Yandex Music과 Spotify의 경우 앱은 **SoundCloud에서 비슷한 트랙을 찾을 뿐, 원본을 옮기지는 않습니다**. 찾은 트랙은 바로 다운로드할 수 있습니다.
(대신 검열을 뚫어요, fck censoor)
### SoundCloud Auto (베타)

- 휴대폰이 블루투스로 차에 연결되면 음악이 자동으로 시작
- 소스 (좋아요, 웨이브, 다운로드, 플레이리스트), 기기, 셔플 선택
- 차량용 별도 이퀄라이저 프리셋, 연결이 끊기면 이전 프리셋으로 복원

### 네트워크

- 여러 호스트와 릴레이 노드, 노드는 자동으로 탐색
- 핑과 64 KB 테스트로 노드 수동 선택: 통신사가 13 KB 이후 연결을 끊는지 확인할 수 있으며, 자동 모드는 그런 노드를 고르지 않음
- "네트워크 없음/서버 다운" 알림은 연결이 15초 동안 없을 때만 표시

### 외관

- 9개 언어
- 테마, 사용자 배경 (블러와 어둡게 처리한 이미지), 강조 색상과 텍스트 색상, RGB 팔레트, 테마 내보내기와 가져오기
- 앱 아이콘: 보라색, 파란색 또는 주황색 (사용자 이미지는 별도 바로가기로 추가됨, Android 제한)
- **Full 전용:** Glass 인터페이스, Star용 프로필 오라 (Desktop과 동기화), 몰입형 플레이어, 설정 화면의 부드러운 열림

### 기타

- 자동 업데이트: 새 버전 알림을 누르면 APK가 다운로드되고 설치 프로그램이 열림
- QR 로그인 (앱 안에서 바로 카메라 사용), 링크 또는 브라우저 로그인
- 연결된 Discord가 계정에 표시됨
- "정보"의 지원: GitHub issue 또는 Discord 티켓, 기기 정보는 자동으로 입력됨

---

## 피드백

버그를 찾았거나 아이디어가 있나요? 가장 쉬운 방법은 "설정 -> 정보 -> 지원"입니다: 기기 정보가 포함된 GitHub issue 또는 Discord 티켓이 열립니다. 또는 직접 [issue를 열고](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) 로그를 첨부하세요: "설정 -> 로그 -> 공유".

---

## 기술 스택

| 계층 | 기술 |
| --- | --- |
| 언어 | Kotlin |
| UI | Jetpack Compose, Material 3, Haze (Full의 유리 효과) |
| 오디오 | Media3 (ExoPlayer + MediaSession), 알림이 있는 백그라운드 서비스 |
| 네트워크 | OkHttp (+ 디스크 캐시), kotlinx.serialization |
| 이미지 | Coil (+ 커버 기반 색상용 Palette) |
| 카메라 | CameraX + ML Kit (Full의 QR 스캐너), ZXing |
| 저장소 | SharedPreferences (설정, 세션), 다운로드와 플레이리스트의 JSON 인덱스 |

[SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop)와 같은 백엔드: 앱은 그 백엔드의 또 다른 클라이언트입니다.

**호환성:** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

전체 변경 내역은 [CHANGELOG.md](../CHANGELOG.md)에 있습니다.

---

## 개발자용

**빌드:** JDK 17, Android SDK 35.

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK 위치: app/build/outputs/apk/<flavor>/debug/
./gradlew testLiteDebugUnitTest                 # 단위 테스트
./gradlew lintLiteDebug                         # lint (베이스라인: ./gradlew updateLintBaseline)
```

**소스 코드:**

| 폴더 | 내용 |
| --- | --- |
| `app/src/main` | 로직: API, 플레이어, 다운로드, 플레이리스트, 가져오기 |
| `app/src/shared` | 공통 화면과 대화상자 (설정 등) |
| `app/src/lite` | Lite 인터페이스 |
| `app/src/full` | Full의 Glass 인터페이스 |

**릴리스:** `vX.Y.Z` 태그 (`versionName`과 일치해야 함)를 푸시하면 두 버전을 하나의 키로 빌드하고 서명한 뒤 `CHANGELOG.md`의 내용으로 게시합니다.

**백엔드:**

| 용도 | 호스트 |
| --- | --- |
| API | `api.scnative.space`, `api-star.scnative.space` (Star) |
| 스트리밍 | `stream.scnative.space`, `stream-star.scnative.space` |
| 저장소 | `storage.scnative.space`, `storage-star.scnative.space` |
| 이미지 | `images.scnative.space` |
| 릴레이 노드 | `*.rN.relay.scnative.space`, 목록: `health.scnative.space/topology` |

QR 로그인 프로토콜: 데스크톱 저장소의 [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md).

---

## 데이터와 신뢰

- 앱은 `*.scnative.space` 백엔드를 통해 동작합니다 (데스크톱과 동일). **SoundCloud 세션과 토큰은 이 백엔드에 저장되며**, 휴대폰에는 세션 ID만 남습니다.
- "로그아웃"은 백엔드의 세션 (`POST /auth/logout`)과 휴대폰의 모든 데이터를 삭제합니다.
- SoundCloud로 직접 연결하는 예비 경로 (설정에서 끌 수 있음)는 세션 없이 soundcloud.com에 접속합니다.
- 가져오기용 Yandex Music 토큰은 휴대폰에만 저장됩니다. 앱은 Yandex 비밀번호를 절대 보지 못합니다: 로그인은 Yandex 자체 페이지에서 이루어집니다.

---

## 라이선스

MIT. 자세한 내용은 [LICENSE](../LICENSE) 파일을 참고하세요.

_SoundCloud는 SoundCloud Ltd.의 상표입니다. 이 앱은 SoundCloud와 관련이 없습니다._

---

<p align="center">
<code>soundcloud android</code> · <code>soundcloud apk</code> · <code>사운드클라우드 안드로이드</code> · <code>사운드클라우드 앱</code> · <code>광고 없는 사운드클라우드</code> · <code>사운드클라우드 차단</code> · <code>soundcloud android app</code> · <code>soundcloud player</code> · <code>soundcloud apk download</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code> · <code>사운드클라우드 음악 플레이어</code>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/SoundCloud_Android_다운로드-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="SoundCloud Android 다운로드"/>
</a>
</p>
