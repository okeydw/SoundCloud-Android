<p align="center">
<a href="../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-0048FF?style=flat-square" alt="中文"/></a>
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
<b>非官方 SoundCloud Android 客户端</b><br>
无广告 · 无验证码 · 无审查
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
<img src="https://img.shields.io/badge/下载_APK-0048FF?style=for-the-badge&logo=android&logoColor=white" alt="下载 APK"/>
</a>
</p>

---

> [!WARNING]
> ⚠️ **由于主 API 和 SoundCloud 的安全机制，登录可能会使你从另一个会话中退出。** 如果你在其他设备（例如桌面客户端）已登录，在这里登录可能会“解绑”那个会话，反之亦然。这是后端的限制，而非应用的 bug——在需要的地方重新登录即可。

---

## 这是什么？

**SoundCloud Android** 是手机上的原生 SoundCloud 客户端。它使用与 [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) 相同的后端，因此可直接访问整个曲库。

使用 **Kotlin + Jetpack Compose + Media3** 编写，原生运行，占用资源极少。

目前是一个没有花哨功能的轻量版本；等我确认没有 bug 且功能完整后再打磨设计，然后拆分为两个版本：

> `Full` — 大量精美效果，设计接近桌面版。

> `Lite` — 简化设计，面向偏好极简和低配手机的用户。

### 不会有 iOS 版本！

---

## 功能

- **搜索与 Wave** — 搜索曲目/艺术家/歌单，以及个性化推荐流
- **播放器** — 后台播放、通知栏控制、波形图、随机/循环、手势
- **音乐库** — 喜欢/不喜欢、歌单、历史、艺术家主页
- **离线** — 下载曲目及带缓存的离线模式
- **外观** — 深色/浅色主题、沉浸模式和 9 种语言

---

## 下载

前往[发布页面](https://github.com/okeydw/SoundCloud-Android/releases/latest)下载 `.apk`。

**安装：** 在手机上打开下载的文件，并允许安装来自未知来源的应用。

**要求：** Android 8.0 (Oreo) 或更高版本。

---

## 截图

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

## 反馈

发现 bug 或有想法？ — [提交 issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose)。

---

## 技术栈

| 层 | 技术 |
| --- | --- |
| 语言 | Kotlin |
| 界面 | Jetpack Compose, Material 3 |
| 音频 | Media3 (ExoPlayer + MediaSession)，带通知的前台服务 |
| 网络 | OkHttp（+ 磁盘缓存）, kotlinx.serialization |
| 图片 | Coil（+ Palette 根据封面着色） |
| 存储 | SharedPreferences（设置、会话）、已下载曲目的 JSON 索引 |

与 [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) 相同的后端——本应用作为它的另一个客户端。

**兼容性：** `minSdk 26`（Android 8.0）… `targetSdk 35`, `compileSdk 35`。

---

## 结构

```
app/src/main/java/com/scd/android/
  App.kt              - Application：会话/缓存/设置初始化，通过代理的 Coil 封面加载器（每张图唯一键）
  MainActivity.kt     - 入口，导航（搜索 / Wave / 我），马赛克瓦片，“无网络”横幅，过滤不可用曲目，来自通知的深链接
  Api.kt              - API 客户端：搜索、流媒体、封面、曲目与歌单的喜欢/不喜欢、历史、数据模型；多级 HTTP 缓存（冷数据长时间缓存）
  NetMonitor.kt       - 网络检测（Wi-Fi / 移动数据）+ 从缓存的离线回退
  Prefs.kt            - 设置（主题、语言、离线、沉浸）+ 用户名缓存
  LocaleHelper.kt     - 覆盖应用语言环境（切换语言）

  PlaybackService.kt  - MediaSessionService：后台播放器、通知、其中的喜欢/随机、点按打开播放器
  Player.kt           - 迷你与全屏播放器（波形、手势、随机/循环、沉浸、跑马灯、加入歌单）
  NowPlaying.kt       - 全局播放器状态 + 导航/歌单刷新事件（PlaylistEvents, NavEvents）

  WaveScreen.kt       - Wave：分页信息流，滑动与双击 = 带动画的喜欢，刷新
  ArtistScreen.kt     - 艺术家主页：曲目和歌单（包括来自下载的离线内容）
  PlaylistScreen.kt   - 歌单页：曲目列表、给歌单点赞、全部下载 / 取消
  LibraryScreen.kt    - “我”标签：问候、喜欢、下载、自建与已赞歌单、历史 + 设置页

  Likes.kt / Dislikes.kt - 曲目喜欢/不喜欢状态（互斥，与后端同步）
  LikedPlaylists.kt   - 已赞歌单状态（与后端同步）
  Downloads.kt        - 将曲目下载到私有文件夹、索引、进度通知、取消与深链接
  Genres.kt           - 空搜索时瓦片使用的流派列表

app/src/main/res/
  drawable/           - 矢量图标（Lucide 风格）+ logo
  values/, values-*/  - 9 种语言的字符串 + values-night（深色主题）

proguard-rules.pro    - release 构建的 R8 规则（serialization / OkHttp）
.github/workflows/    - CI：lint、测试、构建 debug APK
```

完整更新日志见 [CHANGELOG.md](../CHANGELOG.md)。

---

## 许可证

MIT。详情见 [LICENSE](../LICENSE) 文件。

_SoundCloud 是 SoundCloud Ltd. 的商标。本应用与 SoundCloud 无关联。_
