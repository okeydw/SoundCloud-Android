<p align="center">
<a href="../../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
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
<img src="../icon.png" width="160px" />
</a>
</p>

<h1 align="center">SoundCloud Android</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">데스크톱 버전</a>] · [<a href="https://github.com/okeydw/SoundCloud-Android/tree/ios">iOS 버전</a>]</b><br>
<b>Android용 비공식 SoundCloud 클라이언트</b><br>
광고 없음 · 캡차 없음 · 검열 없음 · 러시아에서 사용 가능
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
<img src="https://img.shields.io/badge/APK_다운로드-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"/>
</a>
</p>

---

## 무엇인가요?

**SoundCloud Android**는 휴대폰용 네이티브 SoundCloud 클라이언트입니다. [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop)과 동기화됩니다.

**Kotlin + Jetpack Compose + Media3**로 작성되어 네이티브로 동작하며 리소스를 최소한으로 사용합니다.

클라이언트는 두 가지 버전으로 나오며, 기능은 같고 디자인만 다릅니다:

> `SoundCloud Full` - 데스크톱 버전 스타일의 Glass 디자인. 화려한 효과와 고사양 휴대폰을 좋아하는 분께.

> `SoundCloud Lite` - 초기 버전에서 쓰던 단순한 인터페이스. 미니멀리즘을 좋아하거나 저사양 휴대폰을 쓰는 분께.

### [iOS 버전 (베타)](https://github.com/okeydw/SoundCloud-Android/tree/ios)

---

## 다운로드

[릴리스 페이지](https://github.com/okeydw/SoundCloud-Android/releases/latest)로 이동해 필요한 `.apk`를 받으세요:

| 파일 | 버전 |
| --- | --- |
| `SoundCloud-Full-vX.Y.Z.apk` | SoundCloud, Glass 디자인 |
| `SoundCloud-Lite-vX.Y.Z.apk` | SoundCloud Lite, 단순한 인터페이스 |

**설치:** 받은 파일을 휴대폰에서 열고 출처를 알 수 없는 앱 설치를 허용하세요. 이후 업데이트는 앱에서 바로 설치됩니다.

**요구 사항:** Android 8.0 (Oreo) 이상.

---

## 로그인 방법

다음은 로그인입니다. SoundCloud로 로그인하거나 데스크톱 버전의 QR 코드를 사용하세요.

1. **데스크톱의 QR (권장).** [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop)에서 설정 -> 계정 -> 다른 기기로 세션 전송을 연 뒤 QR 코드를 스캔하세요.
2. **휴대폰의 QR.** 로그인 화면에서 "로그인용 QR 표시"를 누르고, 이미 로그인된 기기에서 스캔하세요.
3. **브라우저로.** "브라우저로 로그인" 버튼은 일반 SoundCloud 로그인 화면을 엽니다. 나중에 다른 곳에서 로그인하면 이 세션이 끊길 수 있습니다.

---

## 스크린샷

> 배경, 강조, 텍스트 색상은 RGB 팔레트로 바꿀 수 있습니다. 배경 이미지와 효과도 추가할 수 있습니다.

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

## 기능

### 음악

- **웨이브와 추천** - 좋아요와 감상 기록을 바탕으로 한 자체 추천 시스템
- **검색** - 트랙, 아티스트, 플레이리스트, 앨범, 바이브 검색 (신경망이 맥락에 맞춰 트랙을 골라 줍니다)
- **플레이어** - 셔플, 반복, 재생 대기열, 가사, 좋아요와 싫어요
- **이퀄라이저** - 기본 프리셋과 나만의 프리셋 만들기
- **플레이리스트** - SoundCloud와 동기화되는 나만의 로컬 플레이리스트, 공개 또는 비공개, 드래그로 트랙 순서를 바꾸면 저장됩니다
- **기록과 통계** - 감상 시간, 인기 트랙과 아티스트, 일별 및 시간별 그래프
- **프로필** - 팔로우와 언팔로우, SoundCloud 프로필의 링크
- **SoundCloud Auto (베타)** - 블루투스 기기 연결 시나리오 만들기

### 오프라인 모드

- 트랙과 플레이리스트 다운로드, 캐시를 쓰는 오프라인 모드
- 캐시 한도와 캐시 초기화 시점 직접 설정
- 두 버전이 모두 설치된 경우 Full과 Lite 사이 다운로드와 플레이리스트 공유 (베타, 일부만 동작)

### 가져오기 (베타)

- **YouTube / YouTube Music** - Google 계정으로 좋아요, 플레이리스트, 앨범
- **Yandex Music** - 토큰으로 좋아요, 플레이리스트, 앨범
- **Spotify** - client_id로 좋아요, 플레이리스트, 앨범

###### [자세히 보기](../import-lang/import.ko.md)

> 중요!!! 오디오 자체를 가져오는 것이 아니라 비슷한 트랙을 찾습니다
> (덕분에 검열을 피할 수 있습니다, fck censor)

---

## 피드백
커뮤니티: [Discord](https://discord.gg/Au3ebtfYu3)

버그를 찾았거나 아이디어가 있나요? 가장 쉬운 방법은 "설정 -> 시스템 정보 -> 지원"입니다. GitHub에 issue를 열거나 Discord에 티켓을 만들면 기기 정보가 자동으로 들어갑니다. 직접 [issue를 열고](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) 로그를 첨부해도 됩니다: "설정 -> 로그 -> 공유".

---

## 기술 스택

| 계층 | 기술 |
| --- | --- |
| 언어 | Kotlin |
| UI | Jetpack Compose, Material 3, Haze (Full의 유리 효과) |
| 오디오 | Media3 (ExoPlayer + MediaSession), 알림이 있는 백그라운드 서비스 |
| 네트워크 | OkHttp (+ 디스크 캐시), kotlinx.serialization |
| 이미지 | Coil (+ 커버에 맞춘 색상을 위한 Palette) |
| 카메라 | CameraX + ML Kit (Full의 QR 스캐너), ZXing |
| 저장소 | SharedPreferences (설정, 세션), 다운로드와 플레이리스트의 JSON 인덱스 |

백엔드: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

호스트 상태는 [여기](https://status.soundcloud-desktop.fun/)에서 확인하세요

**호환성:** `minSdk 26` (Android 8.0), `targetSdk 35`, `compileSdk 35`.

전체 변경 내역은 [CHANGELOG.md](../../CHANGELOG.md)에 있습니다.

---

## 개발자용

**빌드:** JDK 17, Android SDK 35.

```bash
./gradlew assembleLiteDebug assembleFullDebug   # APK는 app/build/outputs/apk/<flavor>/debug/ 에 생성
./gradlew testLiteDebugUnitTest                 # 유닛 테스트
./gradlew lintLiteDebug                         # lint (baseline: ./gradlew updateLintBaseline)
```

**소스:**

| 폴더 | 내용 |
| --- | --- |
| `app/src/main` | 로직: API, 플레이어, 다운로드, 플레이리스트, 가져오기 |
| `app/src/shared` | 공용 화면과 대화상자 (설정 등) |
| `app/src/lite` | Lite 인터페이스 |
| `app/src/full` | Full의 Glass 인터페이스 |

**릴리스:** `vX.Y.Z` 태그(`versionName`과 같아야 함)를 달면 두 버전을 하나의 키로 빌드하고 서명한 뒤 `CHANGELOG.md`의 설명과 함께 게시합니다.

**백엔드:**

| 도메인 | 용도 |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | 오디오 |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | 트랙 저장소 |
| `images.scnative.space` | 커버 |
| `pay.scnative.space` | STAR 결제 |
| `health.scnative.space` | 연결 확인 |
| `call-*.scnative.space` | "사용자 네트워크" |
| `*.relay.scnative.space` | 예비 경로 |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | SoundCloud에서 트랙 직접 다운로드 |

QR 로그인 프로토콜: 데스크톱 저장소의 [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md).

---


## 라이선스

MIT. 자세한 내용은 [LICENSE](../../LICENSE) 파일을 참고하세요.

_SoundCloud는 SoundCloud Ltd.의 상표입니다. 이 앱은 SoundCloud와 관련이 없습니다._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/SoundCloud_Android_다운로드-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="SoundCloud Android 다운로드"/>
</a>
</p>

<p align="center">
<code>soundcloud android</code> · <code>soundcloud apk</code> · <code>사운드클라우드 안드로이드</code> · <code>사운드클라우드 앱</code> · <code>광고 없는 사운드클라우드</code> · <code>사운드클라우드 차단</code> · <code>soundcloud android app</code> · <code>soundcloud player</code> · <code>soundcloud apk download</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code> · <code>사운드클라우드 음악 플레이어</code>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-Android/releases/latest">
<img src="https://img.shields.io/badge/SoundCloud_Android_다운로드-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="SoundCloud Android 다운로드"/>
</a>
</p>
